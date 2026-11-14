from pydantic import BaseModel
from typing import Optional, Dict, Any


class AreaRiskQuery(BaseModel):
    latitude: float
    longitude: float
    radius_km: float = 1.0


class AreaRiskResponse(BaseModel):
    latitude: float
    longitude: float
    radius_km: float
    spatial_cell_id: Optional[str] = None
    risk_category: str
    risk_class: int
    risk_code: str
    color_indicator: str
    confidence_score: float
    data_period: str
    last_updated: str
    incident_summary: str
    safety_guidance: str
    disclaimer: str
    guarantee_of_safety: bool = False
