import os
import tempfile
import pandas as pd
from pipeline.ingest.csv_loader import CsvLoader
from pipeline.ingest.provenance import ProvenanceRegistry, ProvenanceRecord


def test_provenance_registry():
    registry = ProvenanceRegistry()
    records = registry.list_all()
    assert len(records) >= 3

    ncrb = registry.get("ncrb_cii")
    assert ncrb is not None
    assert "National Crime Records Bureau" in ncrb.source_name
    assert "GODL" in ncrb.license_name


def test_csv_loader_with_provenance():
    loader = CsvLoader()
    with tempfile.NamedTemporaryFile(mode="w", suffix=".csv", delete=False) as f:
        f.write("incident_date,crime_type,latitude,longitude\n")
        f.write("2023-01-01,Theft,28.6139,77.2090\n")
        f.write("2023-01-02,Robbery,19.0760,72.8777\n")
        temp_path = f.name

    try:
        df = loader.load(temp_path, "test_provenance")
        assert len(df) == 2
        assert "_source_file" in df.columns
        assert "_row_hash" in df.columns
        assert df.attrs["provenance_id"] == "test_provenance"
    finally:
        os.remove(temp_path)

