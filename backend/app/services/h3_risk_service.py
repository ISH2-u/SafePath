"""
Risk Spatial & Incident Aggregation Service.
Calculates geographical area risk and H3 grid data from synthetic demo incident records.
Provides reproducible, frequency- and severity-sensitive risk evaluations across urban areas.
"""
import os
import csv
import math
import logging
from typing import Dict, List, Optional, Tuple, Any
from datetime import datetime, timezone
import h3
from app.schemas.risk import H3CellRiskDto, H3GridRiskResponse

logger = logging.getLogger("uvicorn.error")

_sample_incidents_by_cell: Optional[Dict[str, List[Dict[str, Any]]]] = None
_all_incidents: Optional[List[Dict[str, Any]]] = None

# Severity classification based on IPC offense severity
VERY_HIGH_CRIMES = {"robbery", "dacoity", "assault", "physical assault", "armed robbery", "attempt to murder"}
HIGH_CRIMES = {"eve teasing", "burglary", "harassment", "stalking", "housebreaking", "molestation", "snatching"}
MODERATE_CRIMES = {"theft", "vehicle theft", "pickpocketing", "cyber fraud", "cheating", "mobile theft", "general theft"}


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Computes great-circle distance between two decimal coordinates in kilometers."""
    r = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2.0) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2.0) ** 2
    c = 2.0 * math.asin(math.sqrt(max(0.0, min(1.0, a))))
    return r * c


def _load_sample_incidents():
    global _sample_incidents_by_cell, _all_incidents
    if _sample_incidents_by_cell is not None and _all_incidents is not None:
        return _sample_incidents_by_cell, _all_incidents

    _sample_incidents_by_cell = {}
    _all_incidents = []

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
        return _sample_incidents_by_cell, _all_incidents

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

                    item = {
                        "incident_id": row.get("incident_id", ""),
                        "crime_type": row.get("crime_type", "Incident"),
                        "ipc_section": row.get("ipc_section", ""),
                        "city": row.get("city", ""),
                        "latitude": lat,
                        "longitude": lng,
                        "description": row.get("description", "")
                    }
                    _all_incidents.append(item)

                    cell = h3.latlng_to_cell(lat, lng, 8)
                    _sample_incidents_by_cell.setdefault(cell, []).append(item)
                except Exception as row_err:
                    logger.debug(f"Skipping malformed row in sample crime data: {row_err}")
        logger.info(f"Loaded synthetic demo crime dataset: {len(_all_incidents)} incidents indexed across {len(_sample_incidents_by_cell)} H3 cells.")
    except Exception as e:
        logger.error(f"Error loading sample crime data CSV: {e}")

    return _sample_incidents_by_cell, _all_incidents


def evaluate_area_risk_within_radius(lat: float, lng: float, radius_km: float = 1.0) -> Dict[str, Any]:
    """
    Evaluates area risk within a circular radius around (lat, lng).
    Aggregates synthetic incidents within the requested radius to determine
    frequency, highest IPC severity, and geographic concentration.
    """
    # 1. Geographic baseline check (India coverage baseline)
    if not (6.4 <= lat <= 37.6 and 68.7 <= lng <= 97.4) or (lat == 0.0 and lng == 0.0):
        return {
            "risk_class": -1,
            "risk_code": "INSUFFICIENT_DATA",
            "risk_category": "Insufficient data",
            "color": "#94A3B8",
            "confidence": 0.0,
            "count": 0,
            "primary_type": None,
            "data_period": "Historical 2019-2023",
            "guidance": "Sparse data records available for this locality. Always maintain standard situational awareness.",
            "is_monitored": False
        }

    _, all_incidents = _load_sample_incidents()

    # Find incidents within radius
    nearby = [inc for inc in all_incidents if haversine_km(lat, lng, inc["latitude"], inc["longitude"]) <= radius_km]
    count = len(nearby)

    # Monitored city check (e.g. Bengaluru Urban perimeter: 12.75 to 13.35, 77.40 to 77.85)
    # or proximity to indexed demo cities (within 15km of any synthetic incident)
    is_in_monitored_city = (12.75 <= lat <= 13.35 and 77.40 <= lng <= 77.85) or any(
        haversine_km(lat, lng, inc["latitude"], inc["longitude"]) <= 15.0 for inc in all_incidents
    )

    if count == 0:
        if not is_in_monitored_city:
            return {
                "risk_class": -1,
                "risk_code": "INSUFFICIENT_DATA",
                "risk_category": "Insufficient data",
                "color": "#94A3B8",
                "confidence": 0.0,
                "count": 0,
                "primary_type": None,
                "data_period": "SafePath AI Synthetic Demo Baseline",
                "guidance": "Location lies outside active demonstration monitoring perimeter. Always maintain situational awareness.",
                "is_monitored": False
            }

        return {
            "risk_class": 0,
            "risk_code": "LOW",
            "risk_category": "Low Risk",
            "color": "#16A34A",
            "confidence": 0.82,
            "count": 0,
            "primary_type": None,
            "data_period": "SafePath AI Synthetic Demo Baseline",
            "guidance": "Demonstration baseline records show low relative incidence in this sector. Exercise standard personal vigilance.",
            "is_monitored": True
        }

    # Evaluate severity of incidents
    has_vhigh = any(inc["crime_type"].lower() in VERY_HIGH_CRIMES for inc in nearby)
    has_high = any(inc["crime_type"].lower() in HIGH_CRIMES for inc in nearby)

    primary_type = nearby[0]["crime_type"]

    if has_vhigh or count >= 5:
        risk_class = 3
        risk_code = "VERY_HIGH"
        risk_category = "Very High Risk"
        color = "#EF4444"
        confidence = min(0.95, 0.82 + 0.04 * count)
        guidance = "Elevated risk zone with severe violent incident records. Avoid unlit or isolated paths; keep emergency contacts notified."
    elif has_high or count >= 3:
        risk_class = 2
        risk_code = "HIGH"
        risk_category = "High Risk"
        color = "#F97316"
        confidence = min(0.92, 0.78 + 0.04 * count)
        guidance = "Elevated historical incidence of street harassment or burglary. Prefer well-traveled, well-lit thoroughfares."
    else:
        risk_class = 1
        risk_code = "MODERATE"
        risk_category = "Moderate Risk"
        color = "#EAB308"
        confidence = min(0.88, 0.74 + 0.04 * count)
        guidance = "Moderate historical activity recorded. Keep personal belongings secure and maintain standard vigilance."

    return {
        "risk_class": risk_class,
        "risk_code": risk_code,
        "risk_category": risk_category,
        "color": color,
        "confidence": round(confidence, 2),
        "count": count,
        "primary_type": primary_type,
        "data_period": "SafePath AI Synthetic Demo Baseline",
        "guidance": guidance,
        "is_monitored": True
    }


def evaluate_cell_risk(cell_id: str, is_current_location: bool = False) -> H3CellRiskDto:
    """Evaluates risk for an individual H3 resolution-8 cell."""
    c_lat, c_lng = h3.cell_to_latlng(cell_id)

    boundary = h3.cell_to_boundary(cell_id)
    polygon_coords = [[round(pt[1], 6), round(pt[0], 6)] for pt in boundary]
    if polygon_coords:
        polygon_coords.append(polygon_coords[0])

    area_eval = evaluate_area_risk_within_radius(c_lat, c_lng, radius_km=0.6)

    return H3CellRiskDto(
        cell_id=cell_id,
        risk_class=area_eval["risk_class"],
        risk_code=area_eval["risk_code"],
        risk_category=area_eval["risk_category"],
        color_indicator=area_eval["color"],
        confidence_score=area_eval["confidence"],
        incident_count=area_eval["count"],
        primary_incident_type=area_eval["primary_type"],
        observation_period=area_eval["data_period"],
        center_lat=round(c_lat, 6),
        center_lng=round(c_lng, 6),
        is_current_location=is_current_location,
        polygon_coordinates=polygon_coords
    )


def get_h3_risk_grid(lat: float, lng: float, radius_km: float = 2.0, resolution: int = 8) -> H3GridRiskResponse:
    """Retains backend H3 grid calculation for spatial dependencies."""
    res = max(7, min(9, resolution))
    center_cell = h3.latlng_to_cell(lat, lng, res)

    k = 1 if radius_km <= 1.2 else (2 if radius_km <= 2.5 else 3)
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
        disclaimer="Demonstration risk model based on synthetic benchmark records. Does not predict individual victimization or guarantee safety.",
        guarantee_of_safety=False
    )
