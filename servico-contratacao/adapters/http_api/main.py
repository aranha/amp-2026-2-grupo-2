from fastapi import FastAPI

app = FastAPI(
    title="Serviço de Contratação e Liberação",
    version="0.1.0",
    description="API preliminar; contratos Kafka e M2M serão integrados após aceite.",
)


@app.get("/health", tags=["operacao"])
def health() -> dict[str, str]:
    return {"status": "UP"}
