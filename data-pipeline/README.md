# SafePath AI — Historical Crime Data Processing Pipeline

A modular Python data pipeline designed to ingest, validate, deduplicate, normalize, and spatially aggregate historical crime datasets for **SafePath AI** area-risk estimation.

---

## 🛡️ Mandatory Ethical Safeguards

1. **Zero Spatial Invention:** The pipeline **never** invents, extrapolates, or interpolates missing spatial coordinates. Records lacking valid coordinates are explicitly preserved with a `MISSING` quality tag and excluded from micro-grid risk indices.
2. **No False Street Precision:** District and city-level centroids are never falsely represented as street-level incidence locations. They carry a mandatory disclaimer:
   > *"District centroid only — not street-level. Do not use for precise risk mapping."*
3. **Victim Privacy (No PII):** Personal information regarding victims, complainants, or witnesses is strictly barred from ingestion. The pipeline only handles aggregated, anonymized public records.
4. **Provenance & Licensing Integrity:** Every ingested row maintains an immutable link to its data provider, license (GODL, NDSAP, CC BY 4.0), and file SHA-256 hash.

---

## 🏗️ Pipeline Architecture

```
[Raw Dataset: CSV / Excel]
           │
           ▼
┌─────────────────────────┐
│ 1. INGEST               │ ── Auto-detects encoding, computes SHA-256 row hashes,
│    CsvLoader / Excel    │    and registers source provenance & licensing.
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 2. VALIDATE             │ ── Validates India bounding box (6.4°N–37.6°N, 68.7°E–97.4°E),
│    Coord / Timestamp    │    detects Null Island (0,0), and verifies temporal causality.
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 3. CLEAN                │ ── Isolates duplicates without destructive loss,
│    Dedup / Normalizer   │    maps raw text to standard CrimeCategory, and grades
│    LocationHandler      │    spatial resolution (EXACT, STREET, NEIGHBORHOOD, etc.).
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 4. AGGREGATE            │ ── Assigns H3 hexagonal cells (resolution 7, 8, 9)
│    H3 / Temporal        │    and compiles monthly/yearly temporal frequency trends.
└──────────┬──────────────┘
           │
           ▼
┌─────────────────────────┐
│ 5. REPORT & EXPORT      │ ── Produces Data Quality Score & Grade (A/B/C/D),
│    Quality / Coverage   │    generates HTML/JSON reports, and exports clean CSV/Parquet.
└─────────────────────────┘
```

---

## 📦 Supported Data Sources

- **National Crime Records Bureau (NCRB):** *Crime in India (CII)* annual publications.
- **Open Government Data (OGD) Platform India (`data.gov.in`):** Public domain urban safety records.
- **Municipal Open Data Portals (`opencity.in`):** Geo-tagged police station jurisdictions and incidence reports.

---

## 🚀 Quick Start

### 1. Installation
```bash
cd SafePathAI/data-pipeline
pip install -r requirements.txt
```

### 2. Execute Pipeline with Sample Dataset
```bash
python scripts/run_pipeline.py --input sample_data/sample_crime_data.csv --provenance-id datagov_in --output-dir ./output --h3-res 8
```

### 3. Generate or Refresh Quality Reports
```bash
python scripts/generate_report.py --input output/cleaned_crime_data.csv --output-dir ./output
```

### 4. Run Unit Tests
```bash
pytest tests/ -v
```

---

## 📊 Generated Artifacts

| Output File | Description |
|---|---|
| `cleaned_crime_data.csv` | Full dataset with normalized categories, location quality tags, and H3 cell indexes. |
| `cleaned_crime_data.parquet` | Compressed columnar format optimized for spatial analysis and ML training. |
| `isolated_duplicates.csv` | Preserved duplicate records for audit trail (never silently deleted). |
| `h3_hex_risk_summary.csv` | Aggregated crime incident counts grouped by H3 hexagon index and crime type. |
| `data_quality_report.html` | Visual HTML dashboard displaying data quality grade, coordinate validity %, and metrics. |
| `data_quality_report.json` | Programmatic summary of dataset reliability for downstream risk models. |

---

## 🏷️ Standard Crime Category Taxonomy

| Category | Typical Inclusions |
|---|---|
| **THEFT** | Snatching, pickpocketing, motor vehicle theft, larceny (IPC 378, 379) |
| **ROBBERY** | Armed robbery, dacoity, mugging (IPC 390, 392, 395) |
| **ASSAULT** | Physical violence, grievous hurt, battery, road rage affray (IPC 323, 324) |
| **BURGLARY** | Housebreaking, residential trespass, nighttime break-in (IPC 445, 457) |
| **HARASSMENT** | Stalking, street harassment, eve teasing, modesty offenses (IPC 354, 509) |
| **FRAUD** | Cheating, forgery, cyber financial scams (IPC 420, 468) |
| **OTHER** | Miscellaneous non-violent infractions |

