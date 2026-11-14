"""CLI tool to generate and refresh data quality reports from processed datasets."""

import argparse
import sys
import os
import pandas as pd

current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from pipeline.report.quality_report import DataQualityReport
from pipeline.report.coverage_report import CoverageReport


def main():
    parser = argparse.ArgumentParser(description="Generate Quality & Coverage Reports")
    parser.add_argument("--input", "-i", required=True, help="Path to cleaned crime dataset (.csv)")
    parser.add_argument("--output-dir", "-o", default="./output", help="Output directory")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        print(f"Error: Input file {args.input} does not exist.")
        sys.exit(1)

    df = pd.read_csv(args.input)
    os.makedirs(args.output_dir, exist_ok=True)

    quality_reporter = DataQualityReport()
    coverage_reporter = CoverageReport()

    q_report = quality_reporter.generate(df)
    c_report = coverage_reporter.generate(df)

    json_path = os.path.join(args.output_dir, "refreshed_quality_report.json")
    html_path = os.path.join(args.output_dir, "refreshed_quality_report.html")

    quality_reporter.save_json(q_report, json_path)
    quality_reporter.save_html(q_report, html_path)

    print(f"Quality Report generated at: {html_path}")
    print(f"JSON Summary generated at:   {json_path}")


if __name__ == "__main__":
    main()

