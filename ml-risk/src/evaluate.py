"""Chronological evaluation, Precision/Recall, PR-AUC, and calibration reporting."""

from typing import Dict, Any, Tuple
import numpy as np
import pandas as pd
from sklearn.metrics import (
    precision_score, recall_score, f1_score,
    classification_report, brier_score_loss, precision_recall_curve, auc
)


class ModelEvaluator:
    """Enforces strict chronological train/val/test splits without look-ahead data leakage."""

    @staticmethod
    def chronological_split(
        df: pd.DataFrame,
        date_col: str = "period_date",
        train_ratio: float = 0.65,
        val_ratio: float = 0.15
    ) -> Tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
        df_sorted = df.sort_values(by=date_col).reset_index(drop=True)
        unique_dates = df_sorted[date_col].drop_duplicates().sort_values().values

        n_dates = len(unique_dates)
        train_cutoff = unique_dates[int(n_dates * train_ratio)]
        val_cutoff = unique_dates[int(n_dates * (train_ratio + val_ratio))]

        train_df = df_sorted[df_sorted[date_col] <= train_cutoff].copy()
        val_df = df_sorted[(df_sorted[date_col] > train_cutoff) & (df_sorted[date_col] <= val_cutoff)].copy()
        test_df = df_sorted[df_sorted[date_col] > val_cutoff].copy()

        return train_df, val_df, test_df

    @staticmethod
    def evaluate(
        y_true: np.ndarray,
        y_pred: np.ndarray,
        y_proba: np.ndarray
    ) -> Dict[str, Any]:
        precision_macro = precision_score(y_true, y_pred, average="macro", zero_division=0)
        recall_macro = recall_score(y_true, y_pred, average="macro", zero_division=0)
        f1_macro = f1_score(y_true, y_pred, average="macro", zero_division=0)

        # High risk (class 2) specific precision and recall
        is_high_true = (y_true == 2).astype(int)
        if y_proba.shape[1] >= 3:
            high_proba = y_proba[:, 2]
            precision_curve, recall_curve, _ = precision_recall_curve(is_high_true, high_proba)
            pr_auc = float(auc(recall_curve, precision_curve))
            brier_loss = float(brier_score_loss(is_high_true, high_proba))
        else:
            pr_auc = 0.0
            brier_loss = 0.0

        return {
            "macro_precision": round(float(precision_macro), 3),
            "macro_recall": round(float(recall_macro), 3),
            "macro_f1": round(float(f1_macro), 3),
            "high_risk_pr_auc": round(pr_auc, 3),
            "high_risk_brier_score": round(brier_loss, 3),
            "classification_report": classification_report(y_true, y_pred, zero_division=0, output_dict=True)
        }
