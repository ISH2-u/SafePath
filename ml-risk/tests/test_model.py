import tempfile
import os
import pandas as pd
from src.model import AreaRiskModel


def test_model_training_and_insufficient_data_guardrail():
    model = AreaRiskModel(model_type="gradient_boosting")

    feature_cols = ["f1", "f2"]
    X = pd.DataFrame({
        "f1": [1.0, 2.0, 3.0, 4.0, 5.0, 6.0],
        "f2": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6]
    })
    y = pd.Series([0, 0, 1, 1, 2, 2])

    model.fit(X, y, feature_cols)
    assert model.is_fitted is True

    # Test single cell prediction
    res = model.predict_single_cell({"f1": 5.5, "f2": 0.55}, is_data_insufficient=False)
    assert res["risk_class"] in [0, 1, 2]
    assert "disclaimer" in res
    assert res["data_status"] == "AVAILABLE"

    # Test insufficient data guardrail
    insufficient = model.predict_single_cell({"f1": 5.5, "f2": 0.55}, is_data_insufficient=True)
    assert insufficient["risk_class"] == -1
    assert insufficient["risk_label"] == "Insufficient data"
    assert insufficient["data_status"] == "INSUFFICIENT_DATA"
