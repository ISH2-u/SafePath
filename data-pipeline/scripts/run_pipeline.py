"""CLI runner for the SafePath AI Historical Crime Pipeline."""

import argparse
import sys
import os

# Add parent dir to sys.path so pipeline imports work seamlessly
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from pipeline.pipeline import SafePathCrimePipeline


def main():
    parser = argparse.ArgumentParser(
        description="SafePath AI — Historical Crime Data Processing & H3 Aggregation Pipeline"
    )
    parser.add_argument(
        "--input", "-i",
        required=True,
        help="Path to raw crime dataset (.csv, .xlsx, .xls)"
    )
    parser.add_argument(
        "--provenance-id", "-p",
        default="datagov_in",
        choices=["ncrb_cii", "datagov_in", "city_open_data"],
        help="Dataset provenance identifier"
    )
    parser.add_argument(
        "--output-dir", "-o",
        default="./output",
        help="Output directory for processed artifacts"
    )
    parser.add_argument(
        "--h3-res", "-r",
        type=int,
        default=8,
        choices=[7, 8, 9],
        help="H3 Spatial Resolution (7=City Ward, 8=Neighborhood, 9=Block)"
    )

    args = parser.parse_args()

    print("=" * 60)
    print("SafePath AI — Historical Crime Processing Pipeline")
    print(f"Input:         {args.input}")
    print(f"Provenance:    {args.provenance_id}")
    print(f"H3 Resolution: {args.h3_res}")
    print(f"Output:        {args.output_dir}")
    print("=" * 60)

    pipeline = SafePathCrimePipeline(h3_resolution=args.h3_res)
    result = pipeline.run(
        input_filepath=args.input,
        provenance_id=args.provenance_id,
        output_dir=args.output_dir
    )

    if result.success:
        print("\nPipeline Execution SUCCESSFUL!")
        print("-" * 60)
        print(f"Total Records In:       {result.records_in}")
        print(f"Clean Records Out:      {result.records_clean}")
        print(f"Duplicates Isolated:    {result.duplicates_isolated}")
        print(f"Quality Score:          {result.quality_score} / 100")
        print(f"Quality Grade:          {result.quality_grade}")
        print(f"Execution Duration:     {result.duration_seconds}s")
        print("-" * 60)
        print("Generated Output Files:")
        for name, path in result.output_files.items():
            print(f"  • {name:20s}: {path}")
        print("=" * 60)
        sys.exit(0)
    else:
        print(f"\nPipeline Execution FAILED: {result.error_message}")
        sys.exit(1)


if __name__ == "__main__":
    main()

