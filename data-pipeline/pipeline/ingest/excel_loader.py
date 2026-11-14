"""Excel (.xlsx, .xls) Dataset Loader with provenance attachment."""

import hashlib
import os
from datetime import datetime, timezone
from typing import Optional
import pandas as pd


class ExcelLoader:
    """Loads Excel spreadsheets with multi-sheet support and provenance tracking."""

    def load(self, filepath: str, provenance_id: str, sheet_name: Optional[str] = None) -> pd.DataFrame:
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Excel file not found: {filepath}")

        # Load specified or first sheet
        target_sheet = sheet_name if sheet_name is not None else 0
        df = pd.read_excel(filepath, sheet_name=target_sheet)

        with open(filepath, "rb") as f:
            file_hash = hashlib.sha256(f.read()).hexdigest()

        load_ts = datetime.now(timezone.utc).isoformat()
        basename = os.path.basename(filepath)

        df["_source_file"] = basename
        df["_source_id"] = provenance_id
        df["_file_hash"] = file_hash
        df["_load_timestamp"] = load_ts
        df["_row_hash"] = [
            hashlib.sha256(str(row).encode("utf-8")).hexdigest()[:16]
            for row in df.itertuples(index=False)
        ]

        df.attrs["provenance_id"] = provenance_id
        df.attrs["sheet_name"] = str(target_sheet)
        df.attrs["file_hash"] = file_hash
        df.attrs["source_file"] = basename
        df.attrs["raw_row_count"] = len(df)

        return df

