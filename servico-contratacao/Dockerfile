FROM python:3.12-slim AS builder

WORKDIR /build
COPY pyproject.toml .
RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip wheel --no-cache-dir --wheel-dir /wheels ".[test]"

FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1
WORKDIR /app
COPY --from=builder /wheels /wheels
RUN python -m pip install --no-cache-dir /wheels/*.whl \
    && rm -rf /wheels
COPY domain ./domain
COPY application ./application
COPY adapters ./adapters

EXPOSE 8085
CMD ["uvicorn", "adapters.http_api.main:app", "--host", "0.0.0.0", "--port", "8085"]
