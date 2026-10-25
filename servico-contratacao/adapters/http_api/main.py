from contextlib import asynccontextmanager
import os
from threading import Thread

from fastapi import FastAPI

from adapters.core_bank import ClientCredentialsToken, HttpCoreBank, InProcessCoreBank
from adapters.kafka import ConsumidorDePropostas, PublicadorKafka
from adapters.postgres import RepositorioPostgres
from application.contracting import ContractingService


def _consumer_thread() -> tuple[Thread, ConsumidorDePropostas]:
    database_url = os.environ["CONTRACTING_DATABASE_URL"]
    kafka_servers = os.environ["KAFKA_BOOTSTRAP_SERVERS"]
    repository = RepositorioPostgres(database_url)
    repository.criar_esquema()
    publisher = PublicadorKafka(kafka_servers)
    core = InProcessCoreBank()
    if os.getenv("CONTRACTING_CORE_BANK_MODE", "inprocess") == "http":
        core = HttpCoreBank(
            os.environ["CONTRACTING_CORE_BANK_URL"],
            ClientCredentialsToken(
                os.environ["CONTRACTING_OIDC_ISSUER"],
                os.environ["CONTRACTING_OIDC_CLIENT_ID"],
                os.environ["CONTRACTING_OIDC_CLIENT_SECRET"],
            ),
        )
    service = ContractingService(repository, publisher, core)
    consumer = ConsumidorDePropostas(kafka_servers, service)
    thread = Thread(target=consumer.executar, name="contratacao-kafka", daemon=True)
    thread.start()
    return thread, consumer


@asynccontextmanager
async def lifespan(_: FastAPI):
    consumer_resources: tuple[Thread, ConsumidorDePropostas] | None = None
    if os.getenv("CONTRACTING_ENABLE_KAFKA", "false").lower() == "true":
        consumer_resources = _consumer_thread()
    try:
        yield
    finally:
        if consumer_resources:
            thread, consumer = consumer_resources
            consumer.parar()
            thread.join(timeout=5)

app = FastAPI(
    title="Serviço de Contratação e Liberação",
    version="0.1.0",
    description="Contratação e liberação de crédito orientadas a eventos.",
    lifespan=lifespan,
)


@app.get("/health", tags=["operacao"])
def health() -> dict[str, str]:
    return {"status": "UP"}
