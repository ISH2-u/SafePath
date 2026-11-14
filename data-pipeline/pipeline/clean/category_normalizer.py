"""Crime category taxonomy normalization."""

import re
from typing import Dict, Any, List
import pandas as pd
from schemas.crime_record_schema import CrimeCategory


class CategoryNormalizer:
    """Maps varied police report descriptions and IPC references to standardized taxonomy."""

    KEYWORD_MAPPINGS: Dict[CrimeCategory, List[str]] = {
        CrimeCategory.THEFT: [
            "theft", "snatching", "pickpocket", "stealing", "chori", "चोरी",
            "378", "379", "vehicle theft", "auto theft", "purse snatching"
        ],
        CrimeCategory.ROBBERY: [
            "robbery", "dacoity", "loot", "armed robbery", "mugging", "390", "391", "392", "395"
        ],
        CrimeCategory.ASSAULT: [
            "assault", "battery", "grievous hurt", "affray", "attack", "violence",
            "323", "324", "325", "326", "351", "352", "physical attack"
        ],
        CrimeCategory.BURGLARY: [
            "burglary", "housebreaking", "trespass", "break-in", "445", "457", "458"
        ],
        CrimeCategory.HARASSMENT: [
            "harassment", "stalking", "eve teasing", "molestation", "modesty",
            "354", "354a", "354d", "509", "indecent"
        ],
        CrimeCategory.FRAUD: [
            "fraud", "cheating", "forgery", "cyber fraud", "embezzlement", "420", "468", "impersonation"
        ]
    }

    def normalize(self, raw_category: Any) -> CrimeCategory:
        if raw_category is None or pd.isna(raw_category):
            return CrimeCategory.OTHER

        text = str(raw_category).lower().strip()
        if not text:
            return CrimeCategory.OTHER

        for category, keywords in self.KEYWORD_MAPPINGS.items():
            for kw in keywords:
                if kw in text:
                    return category

        return CrimeCategory.OTHER

    def normalize_dataframe(self, df: pd.DataFrame, category_col: str = "crime_type") -> pd.DataFrame:
        normalized_list = []
        original_list = []

        has_col = category_col in df.columns

        for _, row in df.iterrows():
            raw_val = row[category_col] if has_col else None
            norm_cat = self.normalize(raw_val)
            normalized_list.append(norm_cat.value)
            original_list.append(str(raw_val) if raw_val is not None else "UNKNOWN")

        df["_crime_category"] = normalized_list
        df["_original_category"] = original_list
        return df

