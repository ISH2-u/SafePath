"""Crime record data structures and taxonomy definitions."""

from dataclasses import dataclass, asdict
from enum import Enum
from typing import Optional, Dict, Any


class CrimeCategory(str, Enum):
    THEFT = "THEFT"
    ROBBERY = "ROBBERY"
    ASSAULT = "ASSAULT"
    BURGLARY = "BURGLARY"
    HARASSMENT = "HARASSMENT"
    FRAUD = "FRAUD"
    OTHER = "OTHER"


class LocationQuality(str, Enum):
    EXACT = "EXACT"                          # GPS coordinate < 15m radius
    STREET = "STREET"                        # Street-level geocode
    NEIGHBORHOOD = "NEIGHBORHOOD"            # Locality / ward level
    CITY_LEVEL = "CITY_LEVEL"                # City centroid
    DISTRICT_CENTROID = "DISTRICT_CENTROID"  # District / revenue district centroid
    MISSING = "MISSING"                      # No spatial telemetry available


@dataclass
class CrimeRecord:
    record_id: str
    source_id: str
    incident_date: Optional[str]
    report_date: Optional[str]
    crime_category: CrimeCategory
    original_category: str
    latitude: Optional[float]
    longitude: Optional[float]
    location_quality: LocationQuality
    accuracy_disclaimer: str
    state: Optional[str]
    district: Optional[str]
    city: Optional[str]
    h3_index: Optional[str]
    is_duplicate: bool
    row_hash: str
    load_timestamp: str

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["crime_category"] = self.crime_category.value
        data["location_quality"] = self.location_quality.value
        return data

