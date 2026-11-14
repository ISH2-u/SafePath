import pandas as pd
from pipeline.aggregate.h3_aggregator import H3Aggregator
from pipeline.aggregate.temporal_aggregator import TemporalAggregator


def test_h3_aggregator():
    agg = H3Aggregator(resolution=8)
    h3_idx = agg.lat_lng_to_h3(28.6139, 77.2090)
    assert h3_idx is not None
    assert isinstance(h3_idx, str)

    # Missing coordinates must return None
    assert agg.lat_lng_to_h3(None, None) is None


def test_temporal_aggregator():
    temporal = TemporalAggregator()
    df = pd.DataFrame({
        "_incident_date_iso": ["2023-01-05", "2023-01-20", "2023-02-15"],
        "_crime_category": ["THEFT", "THEFT", "ROBBERY"]
    })

    monthly = temporal.aggregate_monthly(df)
    assert len(monthly) == 2  # 2023-01 and 2023-02
    assert "total" in monthly.columns

