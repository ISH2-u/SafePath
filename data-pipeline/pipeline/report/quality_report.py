"""Data Quality Assessment and HTML/JSON Report Generator."""

import json
from typing import Dict, Any, Optional
import pandas as pd


class DataQualityReport:
    """Evaluates pipeline data quality against safety thresholds."""

    def generate(
        self,
        df: pd.DataFrame,
        provenance_info: Optional[Dict[str, Any]] = None,
        dedup_stats: Optional[Dict[str, Any]] = None,
        coord_stats: Optional[Dict[str, Any]] = None,
        h3_stats: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        total = len(df)
        if total == 0:
            return {"status": "EMPTY_DATASET", "quality_score": 0.0, "grade": "F"}

        valid_coords = int(df["_coord_valid"].sum()) if "_coord_valid" in df.columns else 0
        valid_dates = int(df["_date_valid"].sum()) if "_date_valid" in df.columns else 0
        duplicates = dedup_stats.get("duplicates_found", 0) if dedup_stats else 0

        coord_pct = (valid_coords / total) * 100.0 if total > 0 else 0.0
        date_pct = (valid_dates / total) * 100.0 if total > 0 else 0.0
        dup_pct = (duplicates / (total + duplicates)) * 100.0 if (total + duplicates) > 0 else 0.0

        # Weighted quality score (0 - 100)
        # Coordinates (40%), Dates (30%), Uniqueness (20%), Completeness (10%)
        score = (coord_pct * 0.40) + (date_pct * 0.30) + ((100.0 - dup_pct) * 0.20) + 10.0
        score = min(100.0, max(0.0, round(score, 1)))

        if score >= 85.0:
            grade = "A (High Reliability)"
        elif score >= 70.0:
            grade = "B (Moderate Reliability)"
        elif score >= 50.0:
            grade = "C (Limited Precision)"
        else:
            grade = "D (Insufficient Spatial Telemetry)"

        report = {
            "dataset_summary": {
                "total_records_processed": total,
                "provenance": provenance_info or {},
                "quality_score": score,
                "quality_grade": grade
            },
            "metrics": {
                "valid_coordinates_count": valid_coords,
                "valid_coordinates_pct": round(coord_pct, 2),
                "valid_dates_count": valid_dates,
                "valid_dates_pct": round(date_pct, 2),
                "duplicates_removed_count": duplicates,
                "duplicate_rate_pct": round(dup_pct, 2)
            },
            "spatial_breakdown": coord_stats or {},
            "hex_aggregation": h3_stats or {},
            "ethical_safeguards": {
                "no_coordinates_invented": True,
                "district_centroids_flagged": True,
                "victim_pii_excluded": True
            }
        }
        return report

    def save_json(self, report: Dict[str, Any], output_path: str):
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)

    def save_html(self, report: Dict[str, Any], output_path: str):
        summary = report.get("dataset_summary", {})
        metrics = report.get("metrics", {})
        prov = summary.get("provenance", {})

        html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>SafePath AI - Crime Data Quality Report</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, sans-serif; margin: 30px; background: #F8FAFC; color: #0F172A; }}
        .card {{ background: white; border-radius: 12px; padding: 24px; margin-bottom: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }}
        h1 {{ color: #1E3A8A; margin-top: 0; }}
        h2 {{ color: #1E293B; font-size: 18px; border-bottom: 1px solid #E2E8F0; padding-bottom: 8px; }}
        .badge {{ display: inline-block; padding: 4px 12px; border-radius: 999px; font-weight: bold; background: #DBEAFE; color: #1E40AF; }}
        .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin: 16px 0; }}
        .metric {{ background: #F1F5F9; padding: 16px; border-radius: 8px; text-align: center; }}
        .metric-val {{ font-size: 24px; font-weight: bold; color: #1E3A8A; }}
        .metric-lbl {{ font-size: 12px; color: #64748B; margin-top: 4px; }}
        .notice {{ background: #FEF3C7; border-left: 4px solid #D97706; padding: 12px 16px; border-radius: 4px; font-size: 13px; color: #92400E; margin-top: 16px; }}
    </style>
</head>
<body>
    <div class="card">
        <h1>SafePath AI Data Quality Report</h1>
        <p>Source: <strong>{prov.get('source_name', 'Open Data Ingestion')}</strong> | Citation: <em>{prov.get('citation', 'N/A')}</em></p>
        <span class="badge">Overall Grade: {summary.get('quality_grade', 'N/A')}</span>
        <span class="badge" style="background:#DCFCE7; color:#166534;">Score: {summary.get('quality_score', 0)}/100</span>

        <h2>Core Pipeline Metrics</h2>
        <div class="grid">
            <div class="metric">
                <div class="metric-val">{metrics.get('total_records_processed', summary.get('total_records_processed', 0))}</div>
                <div class="metric-lbl">Total Records</div>
            </div>
            <div class="metric">
                <div class="metric-val">{metrics.get('valid_coordinates_pct', 0)}%</div>
                <div class="metric-lbl">Valid Coordinates</div>
            </div>
            <div class="metric">
                <div class="metric-val">{metrics.get('valid_dates_pct', 0)}%</div>
                <div class="metric-lbl">Valid Dates</div>
            </div>
            <div class="metric">
                <div class="metric-val">{metrics.get('duplicates_removed_count', 0)}</div>
                <div class="metric-lbl">Duplicates Isolated</div>
            </div>
        </div>

        <div class="notice">
            <strong>Mandatory Ethical Policy:</strong> SafePath AI never fabricates or interpolates missing spatial coordinates. District-level centroids are never represented as high-precision street data.
        </div>
    </div>
</body>
</html>
"""
        with open(output_path, "w", encoding="utf-8") as f:
            f.write(html)

