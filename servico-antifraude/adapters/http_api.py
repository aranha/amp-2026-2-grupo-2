import logging
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from threading import Thread

from fastapi import FastAPI, Response

from adapters.kafka import ConsumidorDeElegibilidade, PublicadorKafka
from adapters.postgres import RepositorioPostgres
from application.analisar_elegibilidade import AnalisarElegibilidade

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI) -> AsyncIterator[None]:
    servidores = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092")
    repositorio = RepositorioPostgres(
        os.environ.get("BANCO_URL", "postgresql://antifraude_user:antifraude@localhost:15432/amp")
    )
    repositorio.criar_esquema()
    consumidor = ConsumidorDeElegibilidade(
        servidores, AnalisarElegibilidade(repositorio, PublicadorKafka(servidores))
    )
    app.state.consumo = Thread(target=consumidor.executar, name="consumidor-elegibilidade")
    app.state.consumo.start()
    yield
    consumidor.parar()
    app.state.consumo.join()


app = FastAPI(title="Serviço de Antifraude", version="0.1.0", lifespan=ciclo_de_vida)


@app.get("/health", tags=["operacao"])
def health(response: Response) -> dict[str, str]:
    if not app.state.consumo.is_alive():
        response.status_code = 503
        return {"status": "DOWN"}
    return {"status": "UP"}
