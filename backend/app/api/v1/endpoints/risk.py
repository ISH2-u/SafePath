from fastapi import APIRouter, Query, HTTPException, Depends
from typing import Optional
from datetime import datetime, timezone
import hashlib
import h3
from app.schemas.risk import AreaRiskResponse, H3GridRiskResponse
from app.services.h3_risk_service import get_h3_risk_grid, evaluate_area_risk_within_radius

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
    Preserved for spatial dependencies and future multi-resolution operations.
    """
    return get_h3_risk_grid(lat=lat, lng=lng, radius_km=radius_km, resolution=resolution)


@router.get("/area", response_model=AreaRiskResponse)
def get_area_risk(
    lat: float = Query(..., ge=-90.0, le=90.0, description="Latitude of query center"),
    lng: float = Query(..., ge=-180.0, le=180.0, description="Longitude of query center"),
    radius_km: float = Query(1.0, gt=0.0, le=25.0, description="Search radius in kilometers")
):
    """
    Returns circular area risk assessment derived from synthetic demo incident records.
    
    SAFETY CONSTRAINTS ENFORCED:
    1. Returns 'Insufficient data' if coordinate lies outside indexed baseline.
    2. Explicitly denies guarantee of safety (guarantee_of_safety=False).
    3. Never claims an area is completely safe merely due to lack of records.
    4. Evaluates incident frequency and IPC severity within the circular search radius.
    """
    result = evaluate_area_risk_within_radius(lat=lat, lng=lng, radius_km=radius_km)

    # Derive H3 spatial cell identifier
    try:
        cell_id = h3.latlng_to_cell(lat, lng, 8)
    except Exception:
        cell_id = f"cell_{round(lat, 2)}_{round(lng, 2)}"

    now_iso = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    if result["count"] > 0:
        summary = f"{result['count']} demo incident(s) in {radius_km:.1f}km radius ({result['primary_type'] or 'Incident'})."
    elif result["is_monitored"]:
        summary = f"0 recorded incidents in {radius_km:.1f}km radius. Demonstration baseline active."
    else:
        summary = "Location outside current demonstration data baseline."

    return AreaRiskResponse(
        latitude=lat,
        longitude=lng,
        radius_km=radius_km,
        spatial_cell_id=cell_id,
        risk_category=result["risk_category"],
        risk_class=result["risk_class"],
        risk_code=result["risk_code"],
        color_indicator=result["color"],
        confidence_score=result["confidence"],
        data_period=result["data_period"],
        last_updated=now_iso,
        incident_summary=summary,
        safety_guidance=result["guidance"],
        disclaimer="Demonstration risk model based on synthetic benchmark records. Does not predict individual victimization or guarantee safety.",
        guarantee_of_safety=False
    )
