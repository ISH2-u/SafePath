"""SafePath AI Master Crime Data Processing Pipeline.

Orchestrates ingestion, provenance tracking, coordinate/timestamp validation,
deduplication, category normalization, H3 spatial aggregation, and quality reporting.
"""

import os
import time
from dataclasses import dataclass, asdict
from typing import Optional, Dict, Any
import pandas as pd

from pipeline.ingest.csv_loader import CsvLoader
from pipeline.ingest.excel_loader import ExcelLoader
from pipeline.ingest.provenance import ProvenanceRegistry, ProvenanceRecord
from pipeline.validate.coordinate_validator import CoordinateValidator
from pipeline.validate.timestamp_validator import TimestampValidator
from pipeline.validate.schema_validator import SchemaValidator
from pipeline.clean.deduplicator import Deduplicator
from pipeline.clean.category_normalizer import CategoryNormalizer
from pipeline.clean.location_handler import LocationHandler
from pipeline.aggregate.h3_aggregator import H3Aggregator
from pipeline.aggregate.temporal_aggregator import TemporalAggregator
from pipeline.report.quality_report import DataQualityReport
from pipeline.report.coverage_report import CoverageReport


@dataclass
class PipelineResult:
    success: bool
    source_file: str
    provenance_id: str
    records_in: int
    records_clean: int
    duplicates_isolated: int
    quality_score: float
    quality_grade: str
    output_files: Dict[str, str]
    duration_seconds: float
    error_message: Optional[str] = None


class SafePathCrimePipeline:
    """Master pipeline orchestrator for historical crime data processing."""

    def __init__(self, h3_resolution: int = 8):
        self.h3_resolution = h3_resolution
        self.provenance_registry = ProvenanceRegistry()
        self.csv_loader = CsvLoader()
        self.excel_loader = ExcelLoader()
        self.coord_validator = CoordinateValidator()
        self.time_validator = TimestampValidator()
        self.schema_validator = SchemaValidator()
        self.deduplicator = Deduplicator()
        self.normalizer = CategoryNormalizer()
        self.location_handler = LocationHandler()
        self.h3_aggregator = H3Aggregator(resolution=h3_resolution)
        self.temporal_aggregator = TemporalAggregator()
        self.quality_reporter = DataQualityReport()
        self.coverage_reporter = CoverageReport()

    def run(
        self,
        input_filepath: str,
        provenance_id: str = "datagov_in",
        output_dir: str = "./output"
    ) -> PipelineResult:
        start_time = time.time()
        os.makedirs(output_dir, exist_ok=True)

        try:
            # ── 1. Ingestion ──────────────────────────────────────────
            ext = os.path.splitext(input_filepath)[1].lower()
            if ext in [".xlsx", ".xls"]:
                df_raw = self.excel_loader.load(input_filepath, provenance_id)
            else:
                df_raw = self.csv_loader.load(input_filepath, provenance_id)

            records_in = len(df_raw)
            prov_record = self.provenance_registry.get(provenance_id)
            prov_dict = prov_record.to_dict() if prov_record else {"source_id": provenance_id}

            # ── 2. Schema Validation ──────────────────────────────────
            is_valid_schema, schema_issues, mappings = self.schema_validator.validate_schema(df_raw)

            lat_col = mappings.get("latitude", "latitude")
            lng_col = mappings.get("longitude", "longitude")
            inc_col = mappings.get("incident_date", "incident_date")
            rep_col = mappings.get("report_date", "report_date")
            cat_col = mappings.get("crime_type", "crime_type")

            # ── 3. Coordinate & Timestamp Validation ──────────────────
            df_val = self.coord_validator.validate_dataframe(df_raw, lat_col, lng_col)
            coord_stats = self.coord_validator.get_summary_report(df_val)

            df_val = self.time_validator.validate_dataframe(df_val, inc_col, rep_col)

            # ── 4. Cleaning: Deduplication ────────────────────────────
            dedup_keys = [inc_col, cat_col, lat_col, lng_col]
            df_clean, df_dupes, dedup_stats = self.deduplicator.deduplicate(df_val, dedup_keys)

            # ── 5. Cleaning: Taxonomy Normalization & Location Quality ─
            df_clean = self.normalizer.normalize_dataframe(df_clean, cat_col)
            df_clean = self.location_handler.process_dataframe(df_clean, lat_col, lng_col)

            # ── 6. Geographic & Temporal Aggregation ──────────────────
            df_clean, h3_agg_df, h3_stats = self.h3_aggregator.aggregate_dataframe(df_clean, lat_col, lng_col)
            monthly_agg_df = self.temporal_aggregator.aggregate_monthly(df_clean)

            # ── 7. Reporting ──────────────────────────────────────────
            quality_report = self.quality_reporter.generate(
                df_clean,
                provenance_info=prov_dict,
                dedup_stats=dedup_stats,
                coord_stats=coord_stats,
                h3_stats=h3_stats
            )
            coverage_report = self.coverage_reporter.generate(df_clean)

            # ── 8. Exports ────────────────────────────────────────────
            output_files = {}

            clean_csv_path = os.path.join(output_dir, "cleaned_crime_data.csv")
            df_clean.to_csv(clean_csv_path, index=False)
            output_files["cleaned_csv"] = clean_csv_path

            # Export parquet if available
            try:
                parquet_path = os.path.join(output_dir, "cleaned_crime_data.parquet")
                df_clean.to_parquet(parquet_path, index=False)
                output_files["cleaned_parquet"] = parquet_path
            except Exception:
                pass

            if not df_dupes.empty:
                dupes_path = os.path.join(output_dir, "isolated_duplicates.csv")
                df_dupes.to_csv(dupes_path, index=False)
                output_files["duplicates_csv"] = dupes_path

            if not h3_agg_df.empty:
                h3_path = os.path.join(output_dir, "h3_hex_risk_summary.csv")
                h3_agg_df.to_csv(h3_path, index=False)
                output_files["h3_summary_csv"] = h3_path

            json_report_path = os.path.join(output_dir, "data_quality_report.json")
            self.quality_reporter.save_json(quality_report, json_report_path)
            output_files["quality_report_json"] = json_report_path

            html_report_path = os.path.join(output_dir, "data_quality_report.html")
            self.quality_reporter.save_html(quality_report, html_report_path)
            output_files["quality_report_html"] = html_report_path

            duration = round(time.time() - start_time, 2)
            dataset_summary = quality_report.get("dataset_summary", {})

            return PipelineResult(
                success=True,
                source_file=os.path.basename(input_filepath),
                provenance_id=provenance_id,
                records_in=records_in,
                records_clean=len(df_clean),
                duplicates_isolated=len(df_dupes),
                quality_score=dataset_summary.get("quality_score", 0.0),
                quality_grade=dataset_summary.get("quality_grade", "N/A"),
                output_files=output_files,
                duration_seconds=duration
            )

        except Exception as e:
            return PipelineResult(
                success=False,
                source_file=os.path.basename(input_filepath),
                provenance_id=provenance_id,
                records_in=0,
                records_clean=0,
                duplicates_isolated=0,
                quality_score=0.0,
                quality_grade="FAILED",
                output_files={},
                duration_seconds=round(time.time() - start_time, 2),
                error_message=str(e)
            )

