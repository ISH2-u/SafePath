"""
H3 Risk Grid Spatial Service.
Calculates H3 hexagonal risk grid, maps real sample incident records,
and evaluates geographical risk classifications at H3 resolution 8.
"""
import os
import csv
import logging
from typing import Dict, List, Optional, Tuple, Any
from datetime import datetime, timezone
import h3
from app.schemas.risk import H3CellRiskDto, H3GridRiskResponse

logger = logging.getLogger("uvicorn.error")

_sample_incidents_by_cell: Optional[Dict[str, List[Dict[str, Any]]]] = None

# Severity classification based on IPC offense severity
VERY_HIGH_CRIMES = {"robbery", "dacoity", "assault", "physical assault", "armed robbery", "attempt to murder"}
HIGH_CRIMES = {"eve teasing", "burglary", "harassment", "stalking", "housebreaking", "molestation"}
MODERATE_CRIMES = {"theft", "snatching", "vehicle theft", "pickpocketing", "cyber fraud", "cheating", "mobile theft", "general theft"}


def _load_sample_incidents() -> Dict[str, List[Dict[str, Any]]]:
    global _sample_incidents_by_cell
    if _sample_incidents_by_cell is not None:
        return _sample_incidents_by_cell

    _sample_incidents_by_cell = {}
    csv_candidates = [
        os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../data-pipeline/sample_data/sample_crime_data.csv")),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "../../data-pipeline/sample_data/sample_crime_data.csv")),
        "/Users/sagnikpaul/Desktop/SafePath/data-pipeline/sample_data/sample_crime_data.csv"
    ]

    csv_path = None
    for p in csv_candidates:
        if os.path.exists(p):
            csv_path = p
            break

    if not csv_path:
        logger.warning("Sample crime data CSV not found. Running with empty historical incident baseline.")
        return _sample_incidents_by_cell

    try:
        with open(csv_path, mode="r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    lat_str = row.get("latitude", "")
                    lng_str = row.get("longitude", "")
                    if not lat_str or not lng_str:
                        continue
                    lat = float(lat_str)
                    lng = float(lng_str)
                    if lat == 0.0 and lng == 0.0:
                        continue
                    if not (6.4 <= lat <= 37.6 and 68.7 <= lng <= 97.4):
                        continue

                    cell = h3.latlng_to_cell(lat, lng, 8)
                    _sample_incidents_by_cell.setdefault(cell, []).append(row)
                except Exception as row_err:
                    logger.debug(f"Skipping malformed row in sample crime data: {row_err}")
        logger.info(f"Loaded sample crime dataset: {sum(len(v) for v in _sample_incidents_by_cell.values())} incidents indexed into {len(_sample_incidents_by_cell)} H3 cells.")
    except Exception as e:
        logger.error(f"Error loading sample crime data CSV: {e}")

    return _sample_incidents_by_cell


def evaluate_cell_risk(cell_id: str, is_current_location: bool = False) -> H3CellRiskDto:
    """
    Evaluates risk for an H3 cell.
    - If cell contains historical sample incidents, assigns real count and severity.
    - If cell is within monitored baseline with 0 incidents, assigns Low Risk.
    - If cell lies outside active baseline bounds, assigns No Data.
    """
    c_lat, c_lng = h3.cell_to_latlng(cell_id)

    # Convert boundary to GeoJSON Polygon format: [[lng, lat], ...]
    boundary = h3.cell_to_boundary(cell_id)
    # GeoJSON coordinates are [longitude, latitude], closed ring
    polygon_coords = [[round(pt[1], 6), round(pt[0], 6)] for pt in boundary]
    if polygon_coords:
        polygon_coords.append(polygon_coords[0])  # Close ring

    # Geographic boundary check (India baseline)
    is_in_monitored_region = (6.4 <= c_lat <= 37.6 and 68.7 <= c_lng <= 97.4)

    if not is_in_monitored_region:
        return H3CellRiskDto(
            cell_id=cell_id,
            risk_class=-1,
            risk_code="NO_DATA",
            risk_category="No Data",
            color_indicator="#94A3B8",
            confidence_score=0.0,
            incident_count=0,
            primary_incident_type=None,
            observation_period="Outside Monitored Baseline",
            center_lat=round(c_lat, 6),
            center_lng=round(c_lng, 6),
            is_current_location=is_current_location,
            polygon_coordinates=polygon_coords
        )

    incidents_dict = _load_sample_incidents()
    cell_incidents = incidents_dict.get(cell_id, [])

    if cell_incidents:
        count = len(cell_incidents)
        # Determine highest severity
        has_vhigh = any(inc.get("crime_type", "").lower() in VERY_HIGH_CRIMES for inc in cell_incidents)
        has_high = any(inc.get("crime_type", "").lower() in HIGH_CRIMES for inc in cell_incidents)
        has_moderate = any(inc.get("crime_type", "").lower() in MODERATE_CRIMES for inc in cell_incidents)

        primary_crime = cell_incidents[0].get("crime_type", "Incident")

        if has_vhigh:
            risk_class = 3
            risk_code = "VERY_HIGH"
            risk_category = "Very High Risk"
            color = "#EF4444"
            confidence = min(0.95, 0.85 + 0.05 * count)
        elif has_high:
            risk_class = 2
            risk_code = "HIGH"
            risk_category = "High Risk"
            color = "#F97316"
            confidence = min(0.90, 0.80 + 0.05 * count)
        elif has_moderate:
            risk_class = 1
            risk_code = "MODERATE"
            risk_category = "Moderate Risk"
            color = "#EAB308"
            confidence = min(0.85, 0.75 + 0.05 * count)
        else:
            risk_class = 1
            risk_code = "MODERATE"
            risk_category = "Moderate Risk"
            color = "#EAB308"
            confidence = 0.75

        return H3CellRiskDto(
            cell_id=cell_id,
            risk_class=risk_class,
            risk_code=risk_code,
            risk_category=risk_category,
            color_indicator=color,
            confidence_score=round(confidence, 2),
            incident_count=count,
            primary_incident_type=primary_crime,
            observation_period="Historical 2023 Sample Records",
            center_lat=round(c_lat, 6),
            center_lng=round(c_lng, 6),
            is_current_location=is_current_location,
            polygon_coordinates=polygon_coords
        )

    # Within monitored region, 0 historical recorded incidents
    return H3CellRiskDto(
        cell_id=cell_id,
        risk_class=0,
        risk_code="LOW",
        risk_category="Low Risk",
        color_indicator="#16A34A",
        confidence_score=0.78,
        incident_count=0,
        primary_incident_type=None,
        observation_period="SafePath Baseline 2019-2023",
        center_lat=round(c_lat, 6),
        center_lng=round(c_lng, 6),
        is_current_location=is_current_location,
        polygon_coordinates=polygon_coords
    )


def get_h3_risk_grid(lat: float, lng: float, radius_km: float = 2.0, resolution: int = 8) -> H3GridRiskResponse:
    """
    Computes an H3 hexagon grid around (lat, lng) with individual risk classifications.
    """
    # Clamp resolution between 7 and 9 (default 8)
    res = max(7, min(9, resolution))
    center_cell = h3.latlng_to_cell(lat, lng, res)

    # Disk radius k:
    # res 8 edge ~460m, diameter ~900m
    # k=1: 7 cells (~1.2 km radius)
    # k=2: 19 cells (~2.2 km radius)
    # k=3: 37 cells (~3.5 km radius)
    if radius_km <= 1.2:
        k = 1
    elif radius_km <= 2.5:
        k = 2
    else:
        k = 3

    cells_in_disk = h3.grid_disk(center_cell, k)

    cell_dtos: List[H3CellRiskDto] = []
    cells_with_data = 0
    cells_no_data = 0

    current_cell_dto: Optional[H3CellRiskDto] = None

    for cell_id in cells_in_disk:
        is_current = (cell_id == center_cell)
        dto = evaluate_cell_risk(cell_id, is_current_location=is_current)
        cell_dtos.append(dto)
        if dto.risk_class == -1:
            cells_no_data += 1
        else:
            cells_with_data += 1
        if is_current:
            current_cell_dto = dto

    if not current_cell_dto:
        current_cell_dto = evaluate_cell_risk(center_cell, is_current_location=True)

    legend = {
        "LOW": "#16A34A",
        "MODERATE": "#EAB308",
        "HIGH": "#F97316",
        "VERY_HIGH": "#EF4444",
        "NO_DATA": "#94A3B8"
    }

    return H3GridRiskResponse(
        center_lat=round(lat, 6),
        center_lng=round(lng, 6),
        radius_km=radius_km,
        resolution=res,
        current_cell_id=center_cell,
        current_risk_category=current_cell_dto.risk_category,
        current_color_indicator=current_cell_dto.color_indicator,
        total_cells=len(cell_dtos),
        cells_with_data=cells_with_data,
        cells_no_data=cells_no_data,
        last_updated=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        cells=cell_dtos,
        legend=legend,
        disclaimer="Historical estimates do not predict individual victimization or guarantee safety. SafePath AI never claims an area is completely safe.",
        guarantee_of_safety=False
    )
