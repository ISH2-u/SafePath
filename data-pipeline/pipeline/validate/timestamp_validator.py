"""Timestamp parsing, validation, and incident vs reporting date differentiation."""

from datetime import datetime, timezone
from typing import Optional, Tuple, Dict, Any
import pandas as pd


class TimestampValidator:
    """Parses dates across formats and enforces temporal causality."""

    DATE_FORMATS = [
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%d",
        "%d/%m/%Y",
        "%m/%d/%Y",
        "%d-%m-%Y",
        "%Y/%m/%d",
        "%d %b %Y",
        "%d %B %Y",
    ]

    def parse_date(self, raw_val: Any) -> Optional[datetime]:
        if raw_val is None or pd.isna(raw_val):
            return None

        if isinstance(raw_val, datetime):
            return raw_val

        raw_str = str(raw_val).strip()
        if not raw_str:
            return None

        for fmt in self.DATE_FORMATS:
            try:
                return datetime.strptime(raw_str, fmt)
            except ValueError:
                continue

        # Try pandas fallback parser
        try:
            ts = pd.to_datetime(raw_str, errors="coerce")
            if pd.notna(ts):
                return ts.to_pydatetime()
        except Exception:
            pass

        return None

    def validate_dates(self, incident_raw: Any, report_raw: Any) -> Tuple[bool, str, Optional[str], Optional[str]]:
        inc_dt = self.parse_date(incident_raw)
        rep_dt = self.parse_date(report_raw)

        inc_iso = inc_dt.strftime("%Y-%m-%d") if inc_dt else None
        rep_iso = rep_dt.strftime("%Y-%m-%d") if rep_dt else None

        now = datetime.now()

        # Check for future dates
        if inc_dt and inc_dt > now:
            return False, "INCIDENT_DATE_IN_FUTURE", inc_iso, rep_iso
        if rep_dt and rep_dt > now:
            return False, "REPORT_DATE_IN_FUTURE", inc_iso, rep_iso

        # Causality: Reporting date cannot precede incident occurrence date
        if inc_dt and rep_dt and rep_dt < inc_dt:
            return False, "REPORT_PRECEDES_INCIDENT", inc_iso, rep_iso

        if inc_dt is None and rep_dt is None:
            return False, "BOTH_DATES_MISSING", None, None

        return True, "VALID", inc_iso, rep_iso

    def validate_dataframe(self, df: pd.DataFrame, incident_col: str = "incident_date", report_col: str = "report_date") -> pd.DataFrame:
        valid_flags = []
        issue_reasons = []
        parsed_incidents = []
        parsed_reports = []

        has_inc = incident_col in df.columns
        has_rep = report_col in df.columns

        for _, row in df.iterrows():
            inc_val = row[incident_col] if has_inc else None
            rep_val = row[report_col] if has_rep else None

            is_valid, reason, inc_iso, rep_iso = self.validate_dates(inc_val, rep_val)
            valid_flags.append(is_valid)
            issue_reasons.append(reason)
            parsed_incidents.append(inc_iso)
            parsed_reports.append(rep_iso)

        df["_date_valid"] = valid_flags
        df["_date_issue"] = issue_reasons
        df["_incident_date_iso"] = parsed_incidents
        df["_report_date_iso"] = parsed_reports
        return df

