import pandas as pd
from pipeline.validate.coordinate_validator import CoordinateValidator
from pipeline.validate.timestamp_validator import TimestampValidator
from pipeline.validate.schema_validator import SchemaValidator


def test_coordinate_validator():
    validator = CoordinateValidator()

    # Valid Delhi coordinates
    is_valid, reason = validator.validate_point(28.6139, 77.2090)
    assert is_valid is True
    assert reason == "VALID"

    # Null Island (0,0)
    is_valid, reason = validator.validate_point(0.0, 0.0)
    assert is_valid is False
    assert reason == "NULL_ISLAND_ZERO_COORDINATES"

    # Out of India bounds (e.g. London coordinates)
    is_valid, reason = validator.validate_point(51.5074, -0.1278)
    assert is_valid is False
    assert reason == "OUT_OF_INDIA_BOUNDS"

    # Missing coordinates
    is_valid, reason = validator.validate_point(None, None)
    assert is_valid is False
    assert reason == "COORDINATES_MISSING"


def test_timestamp_validator():
    validator = TimestampValidator()

    # Valid incident + report date
    is_valid, reason, inc_iso, rep_iso = validator.validate_dates("2023-05-01", "2023-05-02")
    assert is_valid is True
    assert inc_iso == "2023-05-01"
    assert rep_iso == "2023-05-02"

    # Causality violation (report date before incident date)
    is_valid, reason, _, _ = validator.validate_dates("2023-05-10", "2023-05-01")
    assert is_valid is False
    assert reason == "REPORT_PRECEDES_INCIDENT"


def test_schema_validator():
    validator = SchemaValidator()
    df = pd.DataFrame({
        "lat": [28.6],
        "lng": [77.2],
        "offense_type": ["Theft"],
        "date_reported": ["2023-01-01"]
    })

    mappings = validator.detect_column_mappings(df)
    assert mappings["latitude"] == "lat"
    assert mappings["longitude"] == "lng"
    assert mappings["crime_type"] == "offense_type"
    assert mappings["report_date"] == "date_reported"

