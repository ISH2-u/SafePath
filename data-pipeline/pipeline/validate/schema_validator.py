"""Dataset schema and column mapping validator."""

from typing import Dict, List, Tuple
import pandas as pd


class SchemaValidator:
    """Verifies that datasets have required information and maps synonyms."""

    SYNONYM_MAPPINGS = {
        "latitude": ["lat", "latitude", "y", "geo_lat", "location_lat"],
        "longitude": ["lng", "lon", "long", "longitude", "x", "geo_lng", "location_lng"],
        "incident_date": ["incident_date", "date_of_occurrence", "crime_date", "occurrence_date", "incident_time"],
        "report_date": ["report_date", "fir_date", "date_reported", "filing_date"],
        "crime_type": ["crime_type", "offense_type", "offence", "crime_head", "ipc_section", "category"],
        "state": ["state", "state_name", "province"],
        "district": ["district", "district_name", "district_city"],
        "city": ["city", "city_name", "metro", "municipality"]
    }

    def detect_column_mappings(self, df: pd.DataFrame) -> Dict[str, str]:
        """Detects which standard column names correspond to actual columns in the DataFrame."""
        mappings = {}
        df_cols_lower = {col.lower().strip(): col for col in df.columns}

        for std_name, aliases in self.SYNONYM_MAPPINGS.items():
            for alias in aliases:
                if alias in df_cols_lower:
                    mappings[std_name] = df_cols_lower[alias]
                    break

        return mappings

    def validate_schema(self, df: pd.DataFrame) -> Tuple[bool, List[str], Dict[str, str]]:
        mappings = self.detect_column_mappings(df)
        issues = []

        if "crime_type" not in mappings:
            issues.append("Missing required crime category / type column.")
        if "incident_date" not in mappings and "report_date" not in mappings:
            issues.append("Missing both incident date and reporting date columns.")

        is_valid = len(issues) == 0
        return is_valid, issues, mappings

