import pandas as pd
from src.baseline import HistoricalFrequencyBaseline


def test_baseline_predictions():
    baseline = HistoricalFrequencyBaseline()
    df_train = pd.DataFrame({
        "rolling_3m_mean": [1.0, 2.0, 3.0, 4.0, 5.0, 6.0]
    })
    baseline.fit(df_train)

    df_test = pd.DataFrame({
        "rolling_3m_mean": [0.5, 3.0, 8.0]
    })
    preds = baseline.predict(df_test)
    assert len(preds) == 3
    assert preds[0] == 0  # Low
    assert preds[2] == 2  # High
