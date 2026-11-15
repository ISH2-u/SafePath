#!/bin/sh
set -e

echo "=== SafePath AI Production Backend Starting ==="

# Wait for PostgreSQL database connection if a remote database is configured
if [ -n "$DATABASE_URL" ] && [ "$(echo "$DATABASE_URL" | grep -E '^postgresql')" ]; then
    echo "Checking database connection..."
    python - << 'EOF'
import os
import sys
import time
from sqlalchemy import create_engine

db_url = os.environ.get("DATABASE_URL", "")
max_retries = 30
retry_interval = 2

for attempt in range(1, max_retries + 1):
    try:
        engine = create_engine(db_url, connect_args={"connect_timeout": 5})
        with engine.connect() as conn:
            print(f"Database connection verified (attempt {attempt}).")
            sys.exit(0)
    except Exception as e:
        print(f"Database not ready (attempt {attempt}/{max_retries}): {e}")
        time.sleep(retry_interval)

print("ERROR: Could not establish database connection after timeout.")
sys.exit(1)
EOF
fi

echo "Applying database schema migrations (alembic upgrade head)..."
alembic upgrade head
echo "Database migrations applied successfully."

# Start uvicorn server with production settings
WORKERS=${WEB_CONCURRENCY:-2}
PORT=${PORT:-8000}

echo "Starting Uvicorn server on port $PORT with $WORKERS worker(s)..."
exec uvicorn app.main:app --host 0.0.0.0 --port "$PORT" --workers "$WORKERS" --no-access-log
