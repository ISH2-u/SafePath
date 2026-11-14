import pandas as pd
from pipeline.clean.deduplicator import Deduplicator
from pipeline.clean.category_normalizer import CategoryNormalizer
from pipeline.clean.location_handler import LocationHandler
from schemas.crime_record_schema import CrimeCategory, LocationQuality


def test_deduplicator():
    dedup = Deduplicator()
    df = pd.DataFrame({
        "incident_date": ["2023-01-01", "2023-01-01", "2023-01-02"],
        "crime_type": ["Theft", "Theft", "Robbery"],
        "latitude": [28.61, 28.61, 19.07],
        "longitude": [77.20, 77.20, 72.87]
    })

    df_clean, df_dupes, stats = dedup.deduplicate(
        df, ["incident_date", "crime_type", "latitude", "longitude"]
    )

    assert len(df_clean) == 2
    assert len(df_dupes) == 1
    assert stats["duplicates_found"] == 1


def test_category_normalizer():
    normalizer = CategoryNormalizer()
    assert normalizer.normalize("Motorcycle theft reported under IPC 379") == CrimeCategory.THEFT
    assert normalizer.normalize("Dacoity with firearms") == CrimeCategory.ROBBERY
    assert normalizer.normalize("Physical assault and battery") == CrimeCategory.ASSAULT
    assert normalizer.normalize("Stalking and eve teasing") == CrimeCategory.HARASSMENT
    assert normalizer.normalize("Unrelated complaint") == CrimeCategory.OTHER


def test_location_handler_strict_ethics():
    handler = LocationHandler()

    # Missing coordinates
    quality, disclaimer = handler.assess_quality(None, None)
    assert quality == LocationQuality.MISSING
    assert "Excluded" in disclaimer

    # District centroid note
    quality, disclaimer = handler.assess_quality(28.61, 77.20, "Recorded at district headquarters")
    assert quality == LocationQuality.DISTRICT_CENTROID
    assert "not street-level" in disclaimer

