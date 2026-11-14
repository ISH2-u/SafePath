# SafePath AI — Area Risk Machine Learning Model

A Python machine learning component for estimating relative historical area risk across spatial grid cells.

---

## 🛡️ Critical Ethical Scope & Safeguards

- **No Individual Victim Prediction:** This model estimates **historical relative recorded incidence density** within geographic cells. It **does NOT** predict whether an individual person will become a victim of a crime.
- **No Safety Guarantees:** Model output represents relative historical statistical activity. SafePath AI **never** guarantees absolute safety or claims an area is free from risk.
- **Sparse Data Guardrail:** If an area contains insufficient historical observations, the system explicitly returns `INSUFFICIENT_DATA` rather than outputting a deceptive low-risk score.

---

## 📐 Modeling Methodology

1. **Spatial Aggregation:** Crimes are aggregated by discrete spatial cells (e.g. H3 hexagon or coordinate grid bins).
2. **Feature Engineering:**
   - 1-month, 2-month, and 3-month lagged incidence volumes.
   - 3-month and 6-month historical moving averages and standard deviations.
   - Violent-to-property crime ratios.
   - Calendar seasonality indicators (month, quarter).
3. **Chronological Splitting:**
   - Splits data strictly by time window: Train (earlier 65%), Validation (middle 15%), and Test (latest unseen 20%).
   - Zero future look-ahead data leakage.
4. **Calibrated Classification:**
   - Calibrated Gradient Boosting Classifier (`CalibratedClassifierCV`) predicting:
     - `0`: Low relative recorded activity
     - `1`: Moderate relative recorded activity
     - `2`: High relative recorded activity
     - `-1`: Insufficient historical data

---

## 🚀 Training Execution

```bash
cd SafePathAI/ml-risk
pip install -r requirements.txt
python train.py
```
