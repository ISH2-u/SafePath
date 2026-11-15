# Multi-arch production Dockerfile (Supports linux/arm64 and linux/amd64)
FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Install runtime dependencies and build dependencies in a single cached layer
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    libpq5 \
    libpq-dev \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python production dependencies
COPY requirements.txt requirements-prod.txt ./
RUN pip install -r requirements-prod.txt \
    && apt-get purge -y --auto-remove build-essential libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Create dedicated non-root application user
RUN groupadd -g 10001 safepath && \
    useradd -u 10001 -g safepath -m -s /bin/bash safepath

# Copy application source and migration files
COPY . .

# Set appropriate ownership and permissions
RUN chmod +x entrypoint.sh && \
    mkdir -p /app/models /app/data && \
    chown -R safepath:safepath /app

USER safepath:safepath

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

ENTRYPOINT ["./entrypoint.sh"]
