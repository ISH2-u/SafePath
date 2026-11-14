"""Location resolution grading and spatial accuracy handling.

CRITICAL ETHICAL RULES:
1. NEVER invent or interpolate missing coordinates.
2. NEVER claim district-level centroids provide street-level accuracy.
"""

from typing import Optional, Tuple
import pandas as pd
from schemas.crime_record_schema import LocationQuality


class LocationHandler:
    """Evaluates spatial fidelity and adds mandatory accuracy disclosures."""

    DISCLAIMER_EXACT = "Coordinate from GPS device with accuracy <= 15m."
    DISCLAIMER_STREET = "Street/road level geocode. Approximate position."
    DISCLAIMER_NEIGHBORHOOD = "Locality/ward level centroid. Not exact street incident location."
    DISCLAIMER_CITY = "City-level centroid. Excluded from street-level safety routing."
    DISCLAIMER_DISTRICT = "District centroid only — not street-level. Do not use for precise risk mapping."
    DISCLAIMER_MISSING = "No location coordinates available. Excluded from spatial risk calculations."

    def assess_quality(
        self,
        lat: Optional[float],
        lng: Optional[float],
        notes_or_address: str = ""
    ) -> Tuple[LocationQuality, str]:
        if lat is None or lng is None or pd.isna(lat) or pd.isna(lng):
            return LocationQuality.MISSING, self.DISCLAIMER_MISSING

        notes = str(notes_or_address).lower()

        if "district centroid" in notes or "district headquarters" in notes or "district-level" in notes:
            return LocationQuality.DISTRICT_CENTROID, self.DISCLAIMER_DISTRICT

        if "city centroid" in notes or "municipality center" in notes:
            return LocationQuality.CITY_LEVEL, self.DISCLAIMER_CITY

        # Check precision based on decimal places in coordinates
        lat_str = str(lat).strip()
        decimals = len(lat_str.split(".")[1]) if "." in lat_str else 0

        if decimals >= 4:
            return LocationQuality.EXACT, self.DISCLAIMER_EXACT
        elif decimals == 3:
            return LocationQuality.STREET, self.DISCLAIMER_STREET
        elif decimals == 2:
            return LocationQuality.NEIGHBORHOOD, self.DISCLAIMER_NEIGHBORHOOD
        else:
            return LocationQuality.CITY_LEVEL, self.DISCLAIMER_CITY

    def process_dataframe(
        self,
        df: pd.DataFrame,
        lat_col: str = "latitude",
        lng_col: str = "longitude",
        notes_col: str = "description"
    ) -> pd.DataFrame:
        qualities = []
        disclaimers = []

        has_lat = lat_col in df.columns
        has_lng = lng_col in df.columns
        has_notes = notes_col in df.columns

        for _, row in df.iterrows():
            lat = row[lat_col] if has_lat else None
            lng = row[lng_col] if has_lng else None
            notes = row[notes_col] if has_notes else ""

            quality, disclaimer = self.assess_quality(lat, lng, notes)
            qualities.append(quality.value)
            disclaimers.append(disclaimer)

        df["_location_quality"] = qualities
        df["_accuracy_disclaimer"] = disclaimers
        return df

