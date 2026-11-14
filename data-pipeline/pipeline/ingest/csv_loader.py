"""CSV Dataset Loader with encoding auto-detection and row-level hash provenance."""

import hashlib
import os
from datetime import datetime, timezone
from typing import Optional
import pandas as pd


class CsvLoader:
    """Loads CSV crime datasets while preserving full original raw records."""

    SUPPORTED_ENCODINGS = ["utf-8", "utf-8-sig", "latin-1", "cp1252", "iso-8859-1"]

    def load(self, filepath: str, provenance_id: str) -> pd.DataFrame:
        if not os.path.exists(filepath):
            raise FileNotFoundError(f"Source file not found: {filepath}")

        df = None
        used_encoding = None

        for enc in self.SUPPORTED_ENCODINGS:
            try:
                df = pd.read_csv(filepath, encoding=enc)
                used_encoding = enc
                break
            except (UnicodeDecodeError, Exception):
                continue

        if df is None:
            raise ValueError(f"Failed to decode CSV {filepath} with supported encodings.")

        # Compute file hash
        with open(filepath, "rb") as f:
            file_hash = hashlib.sha256(f.read()).hexdigest()

        load_ts = datetime.now(timezone.utc).isoformat()
        basename = os.path.basename(filepath)

        # Attach provenance columns without overwriting raw source fields
        df["_source_file"] = basename
        df["_source_id"] = provenance_id
        df["_file_hash"] = file_hash
        df["_load_timestamp"] = load_ts
        df["_row_hash"] = [
            hashlib.sha256(str(row).encode("utf-8")).hexdigest()[:16]
            for row in df.itertuples(index=False)
        ]

        df.attrs["provenance_id"] = provenance_id
        df.attrs["encoding"] = used_encoding
        df.attrs["file_hash"] = file_hash
        df.attrs["source_file"] = basename
        df.attrs["raw_row_count"] = len(df)

        return df

