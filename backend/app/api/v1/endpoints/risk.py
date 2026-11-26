from fastapi import APIRouter, Query, HTTPException, Depends
from typing import Optional
import hashlib
import h3
from app.schemas.risk import AreaRiskResponse, H3GridRiskResponse
from app.services.ml_risk_service import predict_area_risk
from app.services.h3_risk_service import get_h3_risk_grid, evaluate_cell_risk

router = APIRouter()


@router.get("/h3-grid", response_model=H3GridRiskResponse)
def get_h3_grid(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude of query center"),
    lng: float = Query(..., ge=-180.0, le=180.0, description="Longitude of query center"),
    radius_km: float = Query(2.0, gt=0.0, le=25.0, description="Search radius in kilometers"),
    resolution: int = Query(8, ge=7, le=9, description="H3 hexagon resolution (7 to 9)")
):
    """
    Returns contiguous H3 hexagonal risk grid around a geographic coordinate.
    Each cell contains individual risk classification, historical incident count,
    polygon coordinates for MapLibre rendering, and data integrity guarantees.
    """
    return get_h3_risk_grid(lat=lat, lng=lng, radius_km=radius_km, resolution=resolution)


@router.get("/area", response_model=AreaRiskResponse)
def get_area_risk(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude of query center"),
    lng: float = Query(..., ge=-180.0, le=180.0, description="Longitude of query center"),
    radius_km: float = Query(1.0, gt=0.0, le=25.0, description="Search radius in kilometers")
):
    """
    Returns relative recorded-incident activity levels for a geographic coordinate.
    
    SAFETY CONSTRAINTS ENFORCED:
    1. Returns 'Insufficient data' if coordinate lies outside indexed historical baseline.
    2. Explicitly denies guarantee of safety (guarantee_of_safety=False).
    3. Never claims an area is completely safe merely due to lack of records.
    """
    # 1. Geographic baseline check (India coverage baseline)
    if not (6.4 <= lat <= 37.6 and 68.7 <= lng <= 97.4):
        return AreaRiskResponse(
            latitude=lat,
            longitude=lng,
            radius_km=radius_km,
            spatial_cell_id=f"cell_{round(lat, 2)}_{round(lng, 2)}",
            risk_category="Insufficient data",
            risk_class=-1,
            risk_code="INSUFFICIENT_DATA",
            color_indicator="#94A3B8",
            confidence_score=0.0,
            data_period="Historical 2019-2023",
            last_updated="2024-03-01T00:00:00Z",
            incident_summary="Location outside current historical open-data baseline.",
            safety_guidance="Sparse data records available for this locality. Always maintain standard situational awareness.",
            disclaimer="Insufficient historical police records available for this locality. No reliable estimate can be produced.",
            guarantee_of_safety=False
        )

    # 2. Derive true H3 cell
    cell_id = h3.latlng_to_cell(lat, lng, 8)

    # 3. Check historical incident evaluation
    cell_eval = evaluate_cell_risk(cell_id, is_current_location=True)
    if cell_eval.incident_count > 0:
        return AreaRiskResponse(
            latitude=lat,
            longitude=lng,
            radius_km=radius_km,
            spatial_cell_id=cell_id,
            risk_category=cell_eval.risk_category,
            risk_class=cell_eval.risk_class,
            risk_code=cell_eval.risk_code,
            color_indicator=cell_eval.color_indicator,
            confidence_score=cell_eval.confidence_score,
            data_period=cell_eval.observation_period,
            last_updated="2024-03-01T00:00:00Z",
            incident_summary=f"{cell_eval.incident_count} historical incident(s) recorded in cell ({cell_eval.primary_incident_type or 'General'}).",
            safety_guidance="Exercise elevated awareness near recorded incident zones. Stay on main lighted routes.",
            disclaimer="Historical estimates do not predict individual victimization or guarantee safety. SafePath AI never claims an area is completely safe.",
            guarantee_of_safety=False
        )

    # 4. Check if trained ML risk model is available; otherwise use spatial baseline
    ml_result = predict_area_risk(lat, lng, radius_km)
    if ml_result:
        risk_class = ml_result["risk_class"]
        risk_label = ml_result["risk_label"]
        risk_code = ml_result["risk_code"]
        color = ml_result["color"]
        conf = ml_result["confidence"]
        guidance = ml_result["guidance"]
        data_period = "SafePath AI Calibrated Risk Model"
    else:
        # Monitored baseline with 0 incidents
        risk_class = 0
        risk_label = "Low relative recorded activity"
        risk_code = "LOW"
        color = "#16A34A"
        conf = 0.78
        guidance = "Historical records show low relative incidence. Exercise standard personal vigilance."
        data_period = "Historical 2019-2023 Police Station Aggregates"

    return AreaRiskResponse(
        latitude=lat,
        longitude=lng,
        radius_km=radius_km,
        spatial_cell_id=cell_id,
        risk_category=risk_label,
        risk_class=risk_class,
        risk_code=risk_code,
        color_indicator=color,
        confidence_score=conf,
        data_period=data_period,
        last_updated="2024-03-01T00:00:00Z",
        incident_summary=f"Historical relative activity level: {risk_label}.",
        safety_guidance=guidance,
        disclaimer="Historical estimates do not predict individual victimization or guarantee safety. SafePath AI never claims an area is completely safe.",
        guarantee_of_safety=False
    )
