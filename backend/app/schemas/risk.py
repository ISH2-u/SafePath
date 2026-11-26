from pydantic import BaseModel
from typing import Optional, Dict, Any, List


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


class H3CellRiskDto(BaseModel):
    cell_id: str
    risk_class: int
    risk_code: str
    risk_category: str
    color_indicator: str
    confidence_score: float
    incident_count: int
    primary_incident_type: Optional[str] = None
    observation_period: str
    center_lat: float
    center_lng: float
    is_current_location: bool = False
    polygon_coordinates: List[List[float]]


class H3GridRiskResponse(BaseModel):
    center_lat: float
    center_lng: float
    radius_km: float
    resolution: int
    current_cell_id: str
    current_risk_category: str
    current_color_indicator: str
    total_cells: int
    cells_with_data: int
    cells_no_data: int
    last_updated: str
    cells: List[H3CellRiskDto]
    legend: Dict[str, str]
    disclaimer: str
    guarantee_of_safety: bool = False
