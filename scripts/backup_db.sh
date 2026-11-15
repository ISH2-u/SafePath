#!/usr/bin/env bash
# ─── SafePath AI — Automated PostgreSQL Database Backup Script ──────────────
# Safely dumps the production database from the isolated Docker container,
# compresses with gzip, and enforces a 7-day retention policy.
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="${PROJECT_ROOT}/backups"
TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
BACKUP_FILE="${BACKUP_DIR}/safepath_backup_${TIMESTAMP}.sql.gz"
ENV_FILE="${PROJECT_ROOT}/.env.prod"

# Ensure backup directory exists
mkdir -p "${BACKUP_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
    echo "ERROR: Environment file not found at ${ENV_FILE}" >&2
    exit 1
fi

# Extract DB credentials safely from .env.prod
POSTGRES_USER="$(grep -E '^POSTGRES_USER=' "${ENV_FILE}" | cut -d '=' -f2- | tr -d '"'\''')"
POSTGRES_DB="$(grep -E '^POSTGRES_DB=' "${ENV_FILE}" | cut -d '=' -f2- | tr -d '"'\''')"

if [[ -z "${POSTGRES_USER}" || -z "${POSTGRES_DB}" ]]; then
    echo "ERROR: Could not parse POSTGRES_USER or POSTGRES_DB from ${ENV_FILE}" >&2
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup for database '${POSTGRES_DB}' as user '${POSTGRES_USER}'..."

# Dump database through Docker Compose exec without exposing port 5432
docker compose -f "${PROJECT_ROOT}/docker-compose.prod.yml" exec -T db \
    pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "${BACKUP_FILE}"

BACKUP_SIZE="$(du -h "${BACKUP_FILE}" | cut -f1)"
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup successfully created: ${BACKUP_FILE} (${BACKUP_SIZE})"

# Retain backups for 7 days (purge older)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Purging backups older than 7 days..."
find "${BACKUP_DIR}" -name "safepath_backup_*.sql.gz" -type f -mtime +7 -delete

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup cycle completed cleanly."
