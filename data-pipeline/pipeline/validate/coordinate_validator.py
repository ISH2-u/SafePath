"""Coordinate validation and spatial boundary checking.

STRICT RULE: Never invent, guess, or interpolate missing coordinates.
"""

from typing import Dict, Any, Tuple, Optional
import pandas as pd


class CoordinateValidator:
    """Validates geographic latitude and longitude telemetry."""

    # Approximate bounding box for the Republic of India (including territorial waters & islands)
    INDIA_BOUNDS = {
        "min_lat": 6.4,
        "max_lat": 37.6,
        "min_lng": 68.7,
        "max_lng": 97.4
    }

    def validate_point(self, lat: Optional[float], lng: Optional[float]) -> Tuple[bool, str]:
        if lat is None or lng is None:
            return False, "COORDINATES_MISSING"

        try:
            lat = float(lat)
            lng = float(lng)
        except (ValueError, TypeError):
            return False, "COORDINATES_NON_NUMERIC"

        if pd.isna(lat) or pd.isna(lng):
            return False, "COORDINATES_NAN"

        # Check for Null Island (0, 0)
        if abs(lat) < 0.001 and abs(lng) < 0.001:
            return False, "NULL_ISLAND_ZERO_COORDINATES"

        # Global sanity check
        if not (-90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0):
            return False, "OUT_OF_GLOBAL_BOUNDS"

        # India bounding box check
        if not (self.INDIA_BOUNDS["min_lat"] <= lat <= self.INDIA_BOUNDS["max_lat"] and
                self.INDIA_BOUNDS["min_lng"] <= lng <= self.INDIA_BOUNDS["max_lng"]):
            return False, "OUT_OF_INDIA_BOUNDS"

        return True, "VALID"

    def validate_dataframe(self, df: pd.DataFrame, lat_col: str = "latitude", lng_col: str = "longitude") -> pd.DataFrame:
        """Enriches DataFrame with coordinate validation flags without mutating original coordinates."""
        valid_flags = []
        issue_reasons = []

        has_lat = lat_col in df.columns
        has_lng = lng_col in df.columns

        for _, row in df.iterrows():
            lat_val = row[lat_col] if has_lat else None
            lng_val = row[lng_col] if has_lng else None
            is_valid, reason = self.validate_point(lat_val, lng_val)
            valid_flags.append(is_valid)
            issue_reasons.append(reason)

        df["_coord_valid"] = valid_flags
        df["_coord_issue"] = issue_reasons
        return df

    def get_summary_report(self, df: pd.DataFrame) -> Dict[str, Any]:
        total = len(df)
        if total == 0:
            return {"total_records": 0, "valid_coords_pct": 0.0}

        valid_count = int(df["_coord_valid"].sum()) if "_coord_valid" in df.columns else 0
        missing_count = int((df["_coord_issue"] == "COORDINATES_MISSING").sum()) if "_coord_issue" in df.columns else 0
        null_island_count = int((df["_coord_issue"] == "NULL_ISLAND_ZERO_COORDINATES").sum()) if "_coord_issue" in df.columns else 0
        out_of_bounds = int((df["_coord_issue"] == "OUT_OF_INDIA_BOUNDS").sum()) if "_coord_issue" in df.columns else 0

        return {
            "total_records": total,
            "valid_coordinates_count": valid_count,
            "valid_coordinates_pct": round((valid_count / total) * 100.0, 2),
            "missing_coordinates_count": missing_count,
            "missing_coordinates_pct": round((missing_count / total) * 100.0, 2),
            "null_island_count": null_island_count,
            "out_of_bounds_count": out_of_bounds
        }

