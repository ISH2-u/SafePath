"""Geographic aggregation using H3 hexagonal hierarchical spatial index.

STRICT RULE: Excludes missing coordinates and district centroids from micro-grid risk mapping.
"""

from typing import Optional, Dict, Any, Tuple
import pandas as pd

# Gracefully import h3
try:
    import h3
    H3_AVAILABLE = True
except ImportError:
    H3_AVAILABLE = False


class H3Aggregator:
    """Aggregates spatial crime telemetry into discrete hexagonal cells."""

    SUPPORTED_RESOLUTIONS = [7, 8, 9]

    def __init__(self, resolution: int = 8):
        if resolution not in self.SUPPORTED_RESOLUTIONS:
            raise ValueError(f"Resolution {resolution} not supported. Use 7, 8, or 9.")
        self.resolution = resolution

    def lat_lng_to_h3(self, lat: Optional[float], lng: Optional[float]) -> Optional[str]:
        if lat is None or lng is None or pd.isna(lat) or pd.isna(lng):
            return None

        try:
            lat = float(lat)
            lng = float(lng)
        except (ValueError, TypeError):
            return None

        if H3_AVAILABLE:
            try:
                # Support both h3 v3 (geo_to_h3) and v4 (latlng_to_cell)
                if hasattr(h3, "latlng_to_cell"):
                    return h3.latlng_to_cell(lat, lng, self.resolution)
                else:
                    return h3.geo_to_h3(lat, lng, self.resolution)
            except Exception:
                return None
        else:
            # Fallback deterministic pseudo-H3 grid cell for environments without C-compiled h3
            scale = 10 ** (self.resolution - 5)
            r_lat = round(lat * scale) / scale
            r_lng = round(lng * scale) / scale
            return f"hex_res{self.resolution}_{r_lat:.3f}_{r_lng:.3f}"

    def aggregate_dataframe(
        self,
        df: pd.DataFrame,
        lat_col: str = "latitude",
        lng_col: str = "longitude"
    ) -> Tuple[pd.DataFrame, pd.DataFrame, Dict[str, Any]]:
        """
        Enriches records with H3 index and produces an aggregated cell summary table.
        Excludes records marked as MISSING or DISTRICT_CENTROID from localized risk cells.
        """
        h3_indices = []
        eligible_for_h3 = []

        has_quality = "_location_quality" in df.columns

        for _, row in df.iterrows():
            lat = row[lat_col] if lat_col in df.columns else None
            lng = row[lng_col] if lng_col in df.columns else None

            # Exclude low-fidelity records from micro-risk aggregation
            if has_quality and row["_location_quality"] in ["MISSING", "DISTRICT_CENTROID"]:
                h3_indices.append(None)
                eligible_for_h3.append(False)
                continue

            h3_idx = self.lat_lng_to_h3(lat, lng)
            h3_indices.append(h3_idx)
            eligible_for_h3.append(h3_idx is not None)

        df["_h3_index"] = h3_indices
        df["_h3_eligible"] = eligible_for_h3

        # Create aggregated summary table
        eligible_subset = df[df["_h3_eligible"]].copy()

        if not eligible_subset.empty:
            cat_col = "_crime_category" if "_crime_category" in eligible_subset.columns else None

            if cat_col:
                agg_df = eligible_subset.groupby(["_h3_index", cat_col]).size().unstack(fill_value=0)
                agg_df["total_incidents"] = agg_df.sum(axis=1)
                agg_df = agg_df.reset_index()
            else:
                agg_df = eligible_subset.groupby("_h3_index").size().reset_index(name="total_incidents")
        else:
            agg_df = pd.DataFrame(columns=["_h3_index", "total_incidents"])

        stats = {
            "h3_resolution": self.resolution,
            "total_records": len(df),
            "eligible_for_h3_count": int(sum(eligible_for_h3)),
            "excluded_from_h3_count": int(len(df) - sum(eligible_for_h3)),
            "unique_cells_covered": len(agg_df),
            "h3_library_active": H3_AVAILABLE
        }

        return df, agg_df, stats

