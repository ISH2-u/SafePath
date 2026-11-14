import pandas as pd
from src.features import FeatureEngineer


def test_feature_engineering_lags_and_rolling():
    fe = FeatureEngineer()
    df = pd.DataFrame({
        "spatial_cell_id": ["cell_01"] * 6,
        "period_date": [
            "2023-01-31", "2023-02-28", "2023-03-31",
            "2023-04-30", "2023-05-31", "2023-06-30"
        ],
        "total_incidents": [2, 4, 6, 8, 10, 12],
        "violent_incidents": [1, 2, 2, 3, 4, 5]
    })

    df_out, cols = fe.build_features(df)

    assert "lag_1m_incidents" in df_out.columns
    assert "rolling_3m_mean" in df_out.columns
    assert "target_risk_class" in df_out.columns
    assert len(cols) == len(fe.FEATURE_COLUMNS)

    # 4th row (index 3) lag 1m should equal 6
    assert df_out.loc[3, "lag_1m_incidents"] == 6.0
