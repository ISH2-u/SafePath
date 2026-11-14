"""Machine learning area-risk model using Gradient Boosting with probabilistic calibration."""

import os
from typing import Dict, Any, Optional, Tuple
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.calibration import CalibratedClassifierCV


class AreaRiskModel:
    """
    Trained classifier predicting relative recorded-incident activity levels:
    - 0: Low relative recorded activity
    - 1: Moderate relative recorded activity
    - 2: High relative recorded activity
    - -1: Insufficient historical data

    MANDATORY SAFEGUARD:
    Returns INSUFFICIENT_DATA when observation count or coverage is too low,
    instead of outputting a misleading synthetic risk score.
    """

    LABEL_NAMES = {
        0: "Low relative recorded activity",
        1: "Moderate relative recorded activity",
        2: "High relative recorded activity",
        -1: "Insufficient data"
    }

    def __init__(self, model_type: str = "gradient_boosting"):
        self.model_type = model_type
        if model_type == "random_forest":
            base_est = RandomForestClassifier(n_estimators=100, max_depth=6, random_state=42)
        else:
            base_est = GradientBoostingClassifier(n_estimators=100, max_depth=4, learning_rate=0.05, random_state=42)

        self.classifier = CalibratedClassifierCV(estimator=base_est, method="sigmoid", cv=3)
        self.feature_columns = []
        self.is_fitted = False

    def fit(self, X_train: pd.DataFrame, y_train: pd.Series, feature_cols: list):
        self.feature_columns = feature_cols
        X = X_train[self.feature_columns].values
        y = y_train.values

        self.classifier.fit(X, y)
        self.is_fitted = True

    def predict_single_cell(
        self,
        features: Dict[str, float],
        is_data_insufficient: bool = False
    ) -> Dict[str, Any]:
        """Inference for a single spatial cell query with guardrails."""
        if is_data_insufficient:
            return {
                "risk_class": -1,
                "risk_label": self.LABEL_NAMES[-1],
                "confidence": 0.0,
                "probabilities": {"low": 0.0, "moderate": 0.0, "high": 0.0},
                "disclaimer": "Insufficient historical police records available for this locality. No reliable estimate can be produced.",
                "data_status": "INSUFFICIENT_DATA"
            }

        if not self.is_fitted:
            raise RuntimeError("Model has not been fitted or loaded.")

        X = np.array([[features.get(col, 0.0) for col in self.feature_columns]])
        pred_class = int(self.classifier.predict(X)[0])
        probas = self.classifier.predict_proba(X)[0]

        return {
            "risk_class": pred_class,
            "risk_label": self.LABEL_NAMES[pred_class],
            "confidence": round(float(np.max(probas)), 3),
            "probabilities": {
                "low": round(float(probas[0]), 3),
                "moderate": round(float(probas[1]), 3) if len(probas) > 1 else 0.0,
                "high": round(float(probas[2]), 3) if len(probas) > 2 else 0.0
            },
            "disclaimer": "Estimate based solely on historical police record trends. This does not predict individual victimization or guarantee safety.",
            "data_status": "AVAILABLE"
        }

    def save(self, filepath: str):
        joblib.dump({
            "classifier": self.classifier,
            "feature_columns": self.feature_columns,
            "model_type": self.model_type,
            "is_fitted": self.is_fitted
        }, filepath)

    @classmethod
    def load(cls, filepath: str) -> "AreaRiskModel":
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Model file not found: {filepath}")
        data = joblib.load(filepath)
        instance = cls(model_type=data.get("model_type", "gradient_boosting"))
        instance.classifier = data["classifier"]
        instance.feature_columns = data["feature_columns"]
        instance.is_fitted = data["is_fitted"]
        return instance
