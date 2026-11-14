"""Dataset provenance, licensing records, and data source citations."""

from dataclasses import dataclass, asdict
from typing import Dict, List, Optional
import json


@dataclass
class ProvenanceRecord:
    source_id: str
    source_name: str
    source_url: str
    license_name: str
    license_url: str
    citation: str
    download_date: str
    description: str
    geographic_coverage: str
    temporal_coverage_start: str
    temporal_coverage_end: str
    data_provider: str

    def to_dict(self) -> Dict[str, str]:
        return asdict(self)


class ProvenanceRegistry:
    """Maintains a catalog of official open-data sources and their licensing."""

    def __init__(self):
        self._records: Dict[str, ProvenanceRecord] = {}
        self._init_defaults()

    def _init_defaults(self):
        # 1. NCRB (National Crime Records Bureau)
        self.register(ProvenanceRecord(
            source_id="ncrb_cii",
            source_name="National Crime Records Bureau - Crime in India",
            source_url="https://ncrb.gov.in/",
            license_name="Government Open Data License - India (GODL)",
            license_url="https://data.gov.in/sites/default/files/NDSAP.pdf",
            citation="National Crime Records Bureau (NCRB), Ministry of Home Affairs, Government of India.",
            download_date="2024-01-15",
            description="Aggregated district and state level crime statistics published annually.",
            geographic_coverage="All States & Union Territories of India",
            temporal_coverage_start="2018-01-01",
            temporal_coverage_end="2022-12-31",
            data_provider="Ministry of Home Affairs, Government of India"
        ))

        # 2. Open Government Data Portal (data.gov.in)
        self.register(ProvenanceRecord(
            source_id="datagov_in",
            source_name="Open Government Data (OGD) Platform India",
            source_url="https://data.gov.in/",
            license_name="National Data Sharing and Accessibility Policy (NDSAP)",
            license_url="https://data.gov.in/sites/default/files/NDSAP.pdf",
            citation="Open Government Data (OGD) Platform India, Government of India.",
            download_date="2024-02-01",
            description="Public domain urban safety and crime incident portal datasets.",
            geographic_coverage="Select Municipalities and Metros across India",
            temporal_coverage_start="2019-01-01",
            temporal_coverage_end="2023-12-31",
            data_provider="National Informatics Centre (NIC)"
        ))

        # 3. State & City Open Data Initiatives
        self.register(ProvenanceRecord(
            source_id="city_open_data",
            source_name="Municipal Open Data Repositories",
            source_url="https://opencity.in/",
            license_name="Creative Commons Attribution 4.0 International (CC BY 4.0)",
            license_url="https://creativecommons.org/licenses/by/4.0/",
            citation="Urban Safety and Spatial Analytics Initiative, Community Data Portal.",
            download_date="2024-03-10",
            description="Geo-tagged police station jurisdiction boundary and incidence reports.",
            geographic_coverage="Urban metropolitan centers",
            temporal_coverage_start="2020-01-01",
            temporal_coverage_end="2023-12-31",
            data_provider="Citizen Data Collaborative"
        ))

    def register(self, record: ProvenanceRecord):
        self._records[record.source_id] = record

    def get(self, source_id: str) -> Optional[ProvenanceRecord]:
        return self._records.get(source_id)

    def list_all(self) -> List[ProvenanceRecord]:
        return list(self._records.values())

