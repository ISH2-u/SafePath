"""Simple historical-frequency baseline model for area risk comparison."""

import numpy as np
import pandas as pd
from typing import Dict, Any


class HistoricalFrequencyBaseline:
    """
    Baseline benchmark: predicts future relative activity level based solely
    on the cell's historical moving average (rolling mean).
    """

    def __init__(self):
        self.p33_threshold = 0.0
        self.p66_threshold = 0.0

    def fit(self, df_train: pd.DataFrame):
        rolling_means = df_train["rolling_3m_mean"].dropna()
        if not rolling_means.empty:
            self.p33_threshold = float(rolling_means.quantile(0.33))
            self.p66_threshold = float(rolling_means.quantile(0.66))
        else:
            self.p33_threshold = 1.0
            self.p66_threshold = 3.0

    def predict(self, df: pd.DataFrame) -> np.ndarray:
        predictions = []
        for val in df["rolling_3m_mean"]:
            if val <= self.p33_threshold:
                predictions.append(0)  # Low
            elif val <= self.p66_threshold:
                predictions.append(1)  # Moderate
            else:
                predictions.append(2)  # High
        return np.array(predictions)

    def predict_proba(self, df: pd.DataFrame) -> np.ndarray:
        preds = self.predict(df)
        probas = np.zeros((len(preds), 3))
        for i, p in enumerate(preds):
            probas[i, p] = 0.8
            other = (1.0 - 0.8) / 2.0
            for c in range(3):
                if c != p:
                    probas[i, c] = other
        return probas
