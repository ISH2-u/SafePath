"""Reproducible training and chronological validation pipeline for Area Risk Estimation."""

import os
import sys
import numpy as np
import pandas as pd

# Add current dir to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from src.features import FeatureEngineer
from src.baseline import HistoricalFrequencyBaseline
from src.model import AreaRiskModel
from src.evaluate import ModelEvaluator


def generate_synthetic_historical_data(n_cells: int = 50, n_months: int = 36) -> pd.DataFrame:
    """Generates a reproducible longitudinal spatial dataset for training verification."""
    np.random.seed(42)
    dates = pd.date_range(start="2020-01-01", periods=n_months, freq="M")

    records = []
    for cell_idx in range(n_cells):
        cell_id = f"hex_cell_{cell_idx:03d}"
        # Base crime rate varies by cell
        base_rate = np.random.uniform(0.5, 8.0)

        for dt in dates:
            # Seasonal factor + random noise
            month_factor = 1.0 + 0.2 * np.sin(2 * np.pi * dt.month / 12)
            incidents = int(np.random.poisson(base_rate * month_factor))
            violent = int(incidents * np.random.uniform(0.1, 0.4))

            records.append({
                "spatial_cell_id": cell_id,
                "period_date": dt.strftime("%Y-%m-%d"),
                "total_incidents": incidents,
                "violent_incidents": violent
            })

    return pd.DataFrame(records)


def main():
    print("=" * 60)
    print("SafePath AI — Area Risk ML Model Training Pipeline")
    print("=" * 60)

    # 1. Prepare longitudinal data
    print("1. Generating / Loading longitudinal spatial grid data...")
    df_raw = generate_synthetic_historical_data(n_cells=60, n_months=36)
    print(f"   Total grid observations: {len(df_raw)} records across {df_raw['spatial_cell_id'].nunique()} cells.")

    # 2. Feature Engineering
    print("2. Engineering chronological lags and rolling window features...")
    fe = FeatureEngineer()
    df_features, feature_cols = fe.build_features(df_raw)
    print(f"   Constructed {len(feature_cols)} features: {feature_cols}")

    # 3. Chronological Train / Val / Test Split
    print("3. Performing strict chronological split (no future lookahead)...")
    train_df, val_df, test_df = ModelEvaluator.chronological_split(df_features, date_col="period_date")
    print(f"   Train set: {len(train_df)} rows ({train_df['period_date'].min().date()} to {train_df['period_date'].max().date()})")
    print(f"   Val set:   {len(val_df)} rows ({val_df['period_date'].min().date()} to {val_df['period_date'].max().date()})")
    print(f"   Test set:  {len(test_df)} rows ({test_df['period_date'].min().date()} to {test_df['period_date'].max().date()})")

    # 4. Train Baseline Model
    print("4. Fitting historical-frequency baseline model...")
    baseline = HistoricalFrequencyBaseline()
    baseline.fit(train_df)
    baseline_preds = baseline.predict(test_df)
    baseline_probas = baseline.predict_proba(test_df)
    baseline_eval = ModelEvaluator.evaluate(test_df["target_risk_class"].values, baseline_preds, baseline_probas)

    # 5. Train Machine Learning Model (Gradient Boosting with Calibration)
    print("5. Training Calibrated Gradient Boosting risk classifier...")
    model = AreaRiskModel(model_type="gradient_boosting")
    model.fit(train_df, train_df["target_risk_class"], feature_cols)

    test_X = test_df[feature_cols].values
    ml_preds = model.classifier.predict(test_X)
    ml_probas = model.classifier.predict_proba(test_X)
    ml_eval = ModelEvaluator.evaluate(test_df["target_risk_class"].values, ml_preds, ml_probas)

    # 6. Evaluation Comparison Table
    print("\n" + "=" * 60)
    print("EVALUATION RESULTS (CHRONOLOGICAL TEST SET)")
    print("=" * 60)
    print(f"{'Metric':<25} | {'Baseline':<12} | {'ML Model':<12}")
    print("-" * 55)
    print(f"{'Macro Precision':<25} | {baseline_eval['macro_precision']:<12.3f} | {ml_eval['macro_precision']:<12.3f}")
    print(f"{'Macro Recall':<25} | {baseline_eval['macro_recall']:<12.3f} | {ml_eval['macro_recall']:<12.3f}")
    print(f"{'Macro F1-Score':<25} | {baseline_eval['macro_f1']:<12.3f} | {ml_eval['macro_f1']:<12.3f}")
    print(f"{'High Risk PR-AUC':<25} | {baseline_eval['high_risk_pr_auc']:<12.3f} | {ml_eval['high_risk_pr_auc']:<12.3f}")
    print(f"{'Brier Calibration Score':<25} | {baseline_eval['high_risk_brier_score']:<12.3f} | {ml_eval['high_risk_brier_score']:<12.3f}")
    print("=" * 60)

    # 7. Save Model Artifact
    os.makedirs("models", exist_ok=True)
    model_save_path = os.path.join("models", "area_risk_model.joblib")
    model.save(model_save_path)
    print(f"\nModel artifact successfully serialized to: {model_save_path}")

    # 8. Test Single Cell Inference with Guardrail
    print("\n8. Testing single cell inference guardrail...")
    sample_feat = {col: 3.0 for col in feature_cols}
    normal_pred = model.predict_single_cell(sample_feat, is_data_insufficient=False)
    print(f"   Standard Query Result: {normal_pred['risk_label']} (confidence: {normal_pred['confidence']})")

    insufficient_pred = model.predict_single_cell(sample_feat, is_data_insufficient=True)
    print(f"   Sparse Data Query:     {insufficient_pred['risk_label']} ({insufficient_pred['data_status']})")
    print("   Mandatory Disclaimer:  " + normal_pred["disclaimer"])
    print("\nPipeline run complete.")


if __name__ == "__main__":
    main()
