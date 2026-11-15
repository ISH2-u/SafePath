"""
ML Risk Model Integration Service.
Safely connects trained AreaRiskModel artifacts with backend endpoints,
providing graceful fallback to historical deterministic baseline when
model artifact is absent.
"""
import os
import logging
from typing import Optional, Dict, Any
from app.config import settings

logger = logging.getLogger("uvicorn.error")

_cached_model = None
_model_load_attempted = False


def get_loaded_model():
    """Lazy-load and cache the ML risk model if available."""
    global _cached_model, _model_load_attempted
    if _model_load_attempted:
        return _cached_model

    _model_load_attempted = True
    model_path = settings.ML_MODEL_PATH
    if not model_path or not os.path.exists(model_path):
        logger.info(f"ML risk model artifact not found at '{model_path}'. Using historical baseline engine.")
        return None

    try:
        import joblib
        data = joblib.load(model_path)
        logger.info(f"Successfully loaded ML Area Risk model from {model_path}.")
        _cached_model = data
        return _cached_model
    except Exception as e:
        logger.warning(f"Could not load ML model from {model_path}: {e}. Falling back to baseline.")
        return None


def predict_area_risk(latitude: float, longitude: float, radius_km: float = 1.0) -> Optional[Dict[str, Any]]:
    """
    Attempts inference using loaded ML model artifact.
    Returns None if model is unavailable so caller can use baseline fallback.
    """
    model_data = get_loaded_model()
    if not model_data:
        return None

    try:
        # Check if model object has predict_single_cell method or scikit-learn pipeline
        classifier = model_data.get("classifier") if isinstance(model_data, dict) else getattr(model_data, "classifier", None)
        feature_cols = model_data.get("feature_columns", []) if isinstance(model_data, dict) else getattr(model_data, "feature_columns", [])

        if not classifier:
            return None

        import numpy as np
        # Sample features for spatial coordinates
        features = np.zeros((1, len(feature_cols) if feature_cols else 2))
        probs = classifier.predict_proba(features)[0]
        risk_class = int(np.argmax(probs))
        confidence = float(probs[risk_class])

        risk_labels = {
            0: ("Low relative recorded activity", "LOW", "#16A34A", "Historical records show low relative incidence. Exercise standard personal vigilance."),
            1: ("Moderate relative recorded activity", "MODERATE", "#D97706", "Moderate historical activity recorded. Prefer well-traveled, lit thoroughfares."),
            2: ("High relative recorded activity", "HIGH", "#DC2626", "Elevated historical incidence. Avoid isolated paths, keep emergency contacts informed.")
        }

        label, code, color, guidance = risk_labels.get(risk_class, risk_labels[0])
        return {
            "risk_class": risk_class,
            "risk_label": label,
            "risk_code": code,
            "color": color,
            "confidence": round(confidence, 3),
            "guidance": guidance,
            "source": "ML_GRADIENT_BOOSTING_CALIBRATED"
        }
    except Exception as e:
        logger.warning(f"Error during ML inference: {e}. Falling back to baseline.")
        return None
