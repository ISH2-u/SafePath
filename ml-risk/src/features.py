"""Spatial and temporal feature engineering for historical area-risk estimation.

ETHICAL SAFEGUARD:
This module extracts relative historical incidence patterns across spatial cells.
It does NOT predict individual victimhood or guarantee safety.
"""

from typing import List, Tuple, Dict, Any, Optional
import numpy as np
import pandas as pd


class FeatureEngineer:
    """Constructs chronological lag, rolling window, and spatial density features."""

    MIN_REQUIRED_OBSERVATIONS = 3  # Minimum temporal periods needed to avoid insufficient-data flag

    FEATURE_COLUMNS = [
        "lag_1m_incidents",
        "lag_2m_incidents",
        "lag_3m_incidents",
        "rolling_3m_mean",
        "rolling_3m_max",
        "rolling_6m_mean",
        "month_of_year",
        "quarter",
        "violent_crime_ratio",
        "historical_cell_total"
    ]

    def build_features(self, df: pd.DataFrame) -> Tuple[pd.DataFrame, List[str]]:
        """
        Expects df with columns:
        ['spatial_cell_id', 'period_date', 'total_incidents', 'violent_incidents']
        Sorted chronologically.
        """
        df = df.copy()
        df["period_date"] = pd.to_datetime(df["period_date"])
        df = df.sort_values(by=["spatial_cell_id", "period_date"]).reset_index(drop=True)

        # Lags per cell
        df["lag_1m_incidents"] = df.groupby("spatial_cell_id")["total_incidents"].shift(1)
        df["lag_2m_incidents"] = df.groupby("spatial_cell_id")["total_incidents"].shift(2)
        df["lag_3m_incidents"] = df.groupby("spatial_cell_id")["total_incidents"].shift(3)

        # Rolling statistics
        df["rolling_3m_mean"] = df.groupby("spatial_cell_id")["lag_1m_incidents"].transform(
            lambda s: s.rolling(window=3, min_periods=1).mean()
        )
        df["rolling_3m_max"] = df.groupby("spatial_cell_id")["lag_1m_incidents"].transform(
            lambda s: s.rolling(window=3, min_periods=1).max()
        )
        df["rolling_6m_mean"] = df.groupby("spatial_cell_id")["lag_1m_incidents"].transform(
            lambda s: s.rolling(window=6, min_periods=1).mean()
        )

        # Cumulative historical count for this cell
        df["historical_cell_total"] = df.groupby("spatial_cell_id")["lag_1m_incidents"].transform(
            lambda s: s.cumsum()
        )

        # Calendar features
        df["month_of_year"] = df["period_date"].dt.month
        df["quarter"] = df["period_date"].dt.quarter

        # Crime type ratios
        if "violent_incidents" in df.columns:
            df["violent_crime_ratio"] = np.where(
                df["lag_1m_incidents"] > 0,
                df.groupby("spatial_cell_id")["violent_incidents"].shift(1) / (df["lag_1m_incidents"] + 1e-5),
                0.0
            )
        else:
            df["violent_crime_ratio"] = 0.0

        # Mark records with insufficient history
        cell_observation_counts = df.groupby("spatial_cell_id")["period_date"].transform("count")
        df["is_data_insufficient"] = (
            cell_observation_counts < self.MIN_REQUIRED_OBSERVATIONS
        ) | (df["historical_cell_total"].isna()) | (df["historical_cell_total"] == 0)

        # Define Relative Risk Target for future period:
        # 0 = Low (<= 33rd percentile), 1 = Moderate (34th-66th), 2 = High (>= 67th percentile)
        p33 = df["total_incidents"].quantile(0.33)
        p66 = df["total_incidents"].quantile(0.66)

        def assign_target(x):
            if x <= p33:
                return 0  # Low relative activity
            elif x <= p66:
                return 1  # Moderate relative activity
            else:
                return 2  # High relative activity

        df["target_risk_class"] = df["total_incidents"].apply(assign_target)

        # Fill NaNs from early lags
        df[self.FEATURE_COLUMNS] = df[self.FEATURE_COLUMNS].fillna(0.0)

        return df, self.FEATURE_COLUMNS
