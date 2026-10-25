import logging
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime
from threading import Thread
from uuid import UUID

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel

from adapters.kafka import BRASILIA, ConsumidorDeElegibilidade, PublicadorKafka
from adapters.postgres import RepositorioPostgres
from application.analisar_elegibilidade import AnalisarElegibilidade

saida = logging.StreamHandler()
saida.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s"))
logging.getLogger().addHandler(saida)
logging.getLogger().setLevel(logging.INFO)


class AnaliseResposta(BaseModel):
    solicitacaoId: UUID
    aprovada: bool
    score: int
    motivo: str
    dataAnalise: datetime


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI) -> AsyncIterator[None]:
    servidores = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:19092")
    app.state.repositorio = RepositorioPostgres(
        os.environ.get("BANCO_URL", "postgresql://antifraude_user:antifraude@localhost:15432/amp")
    )
    app.state.repositorio.criar_esquema()
    publicador = PublicadorKafka(servidores)
    consumidor = ConsumidorDeElegibilidade(
        servidores, AnalisarElegibilidade(app.state.repositorio, publicador), publicador
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


@app.get("/v1/analises/{solicitacao_id}", tags=["analises"],
         responses={404: {"description": "Nenhuma análise registrada para a solicitação"}})
def consultar_analise(solicitacao_id: UUID) -> AnaliseResposta:
    analise = app.state.repositorio.buscar_por_solicitacao(solicitacao_id)
    if analise is None:
        raise HTTPException(status_code=404, detail="analise nao encontrada")
    return AnaliseResposta(
        solicitacaoId=analise.solicitacao_id,
        aprovada=analise.aprovada,
        score=analise.score,
        motivo=analise.motivo,
        dataAnalise=analise.analisada_em.astimezone(BRASILIA),
    )
