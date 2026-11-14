"""Deduplication module — Identifies duplicates while preserving them for auditability."""

import hashlib
from typing import Tuple, Dict, Any, List
import pandas as pd


class Deduplicator:
    """Detects and isolates duplicate crime records without destructive data loss."""

    def deduplicate(
        self,
        df: pd.DataFrame,
        key_columns: List[str]
    ) -> Tuple[pd.DataFrame, pd.DataFrame, Dict[str, Any]]:
        total_in = len(df)
        if total_in == 0:
            return df, pd.DataFrame(), {"total_in": 0, "unique_records": 0, "duplicates_found": 0}

        # Filter key_columns that actually exist
        valid_keys = [c for c in key_columns if c in df.columns]

        if not valid_keys:
            # Fallback to row hash
            valid_keys = ["_row_hash"] if "_row_hash" in df.columns else list(df.columns[:3])

        # Flag duplicate entries (keep first instance in clean, preserve remaining in duplicates)
        is_dup = df.duplicated(subset=valid_keys, keep="first")

        df_clean = df[~is_dup].copy()
        df_dupes = df[is_dup].copy()

        df_clean["_is_duplicate"] = False
        df_dupes["_is_duplicate"] = True

        stats = {
            "total_in": total_in,
            "unique_records": len(df_clean),
            "duplicates_found": len(df_dupes),
            "duplicate_pct": round((len(df_dupes) / total_in) * 100.0, 2),
            "dedup_keys_used": valid_keys
        }

        return df_clean, df_dupes, stats

