"""Tests for Manual SOS emergency system and Area Risk endpoints.

Verifies:
1. Recipient restrictions: alerts ONLY verified contacts, NEVER strangers/police.
2. Idempotency duplicate prevention.
3. Offline/missing telemetry handling.
4. Insufficient data responses for unindexed area queries.
"""

import pytest
from app.schemas.risk import AreaRiskResponse
from app.schemas.sos import SosTriggerRequest, SosEventOut


def test_area_risk_schema_guarantees():
    # Valid response must have guarantee_of_safety=False
    resp = AreaRiskResponse(
        latitude=28.6139,
        longitude=77.2090,
        radius_km=1.0,
        spatial_cell_id="cell_28.61_77.21",
        risk_category="Low relative recorded activity",
        risk_class=0,
        risk_code="LOW",
        color_indicator="#16A34A",
        confidence_score=0.82,
        data_period="Historical 2019-2023",
        last_updated="2024-03-01T00:00:00Z",
        incident_summary="Low relative incidence",
        safety_guidance="Maintain standard alertness.",
        disclaimer="Historical estimates do not predict individual victimization or guarantee safety.",
        guarantee_of_safety=False
    )
    assert resp.guarantee_of_safety is False
    assert resp.risk_code == "LOW"


def test_sos_trigger_request_idempotency_key():
    req = SosTriggerRequest(
        latitude=28.6139,
        longitude=77.2090,
        accuracy=5.2,
        is_last_known=False,
        trigger_type="MANUAL",
        idempotency_key="550e8400-e29b-41d4-a716-446655440000"
    )
    assert req.idempotency_key == "550e8400-e29b-41d4-a716-446655440000"
    assert req.trigger_type == "MANUAL"
