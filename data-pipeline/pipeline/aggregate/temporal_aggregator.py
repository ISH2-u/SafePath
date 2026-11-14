"""Temporal aggregation by month, quarter, and year."""

from typing import Dict, Any
import pandas as pd


class TemporalAggregator:
    """Aggregates crime incidents across temporal intervals."""

    def aggregate_monthly(
        self,
        df: pd.DataFrame,
        date_col: str = "_incident_date_iso"
    ) -> pd.DataFrame:
        target_col = date_col if date_col in df.columns else "incident_date"
        if target_col not in df.columns:
            return pd.DataFrame()

        valid_dates = df[pd.notna(df[target_col])].copy()
        if valid_dates.empty:
            return pd.DataFrame()

        valid_dates["_year_month"] = valid_dates[target_col].astype(str).str.slice(0, 7)

        cat_col = "_crime_category" if "_crime_category" in valid_dates.columns else None

        if cat_col:
            agg = valid_dates.groupby(["_year_month", cat_col]).size().unstack(fill_value=0)
            agg["total"] = agg.sum(axis=1)
            return agg.reset_index()
        else:
            return valid_dates.groupby("_year_month").size().reset_index(name="total")

    def aggregate_yearly(
        self,
        df: pd.DataFrame,
        date_col: str = "_incident_date_iso"
    ) -> pd.DataFrame:
        target_col = date_col if date_col in df.columns else "incident_date"
        if target_col not in df.columns:
            return pd.DataFrame()

        valid_dates = df[pd.notna(df[target_col])].copy()
        if valid_dates.empty:
            return pd.DataFrame()

        valid_dates["_year"] = valid_dates[target_col].astype(str).str.slice(0, 4)
        cat_col = "_crime_category" if "_crime_category" in valid_dates.columns else None

        if cat_col:
            agg = valid_dates.groupby(["_year", cat_col]).size().unstack(fill_value=0)
            agg["total"] = agg.sum(axis=1)
            return agg.reset_index()
        else:
            return valid_dates.groupby("_year").size().reset_index(name="total")

