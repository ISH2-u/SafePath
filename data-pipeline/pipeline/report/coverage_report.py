"""Geographic and historical coverage analysis."""

from typing import Dict, Any, List
import pandas as pd


class CoverageReport:
    """Summarizes geographic footprints, historical spans, and data gaps."""

    def generate(self, df: pd.DataFrame) -> Dict[str, Any]:
        if df.empty:
            return {"status": "EMPTY"}

        # 1. Geographic coverage
        states = []
        if "state" in df.columns:
            states = [str(s) for s in df["state"].dropna().unique() if str(s).strip()]

        districts = []
        if "district" in df.columns:
            districts = [str(d) for d in df["district"].dropna().unique() if str(d).strip()]

        cities = []
        if "city" in df.columns:
            cities = [str(c) for c in df["city"].dropna().unique() if str(c).strip()]

        # Bounding box of valid coordinates
        valid_pts = df[df["_coord_valid"] == True] if "_coord_valid" in df.columns else df
        bbox = {}
        if not valid_pts.empty and "latitude" in valid_pts.columns and "longitude" in valid_pts.columns:
            bbox = {
                "min_lat": round(float(valid_pts["latitude"].min()), 4),
                "max_lat": round(float(valid_pts["latitude"].max()), 4),
                "min_lng": round(float(valid_pts["longitude"].min()), 4),
                "max_lng": round(float(valid_pts["longitude"].max()), 4),
            }

        # 2. Historical coverage
        date_col = "_incident_date_iso" if "_incident_date_iso" in df.columns else "incident_date"
        earliest_date = None
        latest_date = None
        years_spanned = []

        if date_col in df.columns:
            dates = df[date_col].dropna().astype(str)
            if not dates.empty:
                earliest_date = str(dates.min())
                latest_date = str(dates.max())
                years_spanned = sorted(list(set(d[:4] for d in dates if len(d) >= 4)))

        # 3. Data Gaps Analysis
        gaps: List[str] = []
        if not states:
            gaps.append("No state-level territorial metadata found.")
        if not districts:
            gaps.append("No district-level administrative demarcations recorded.")
        if not years_spanned:
            gaps.append("Missing temporal year distribution.")
        elif len(years_spanned) == 1:
            gaps.append(f"Only single year ({years_spanned[0]}) data present. Multi-year longitudinal baseline unavailable.")

        return {
            "geographic_coverage": {
                "states_covered": states,
                "states_count": len(states),
                "districts_count": len(districts),
                "cities_count": len(cities),
                "bounding_box": bbox
            },
            "temporal_coverage": {
                "earliest_incident": earliest_date,
                "latest_incident": latest_date,
                "years_covered": years_spanned,
                "total_years_spanned": len(years_spanned)
            },
            "identified_data_gaps": gaps
        }

