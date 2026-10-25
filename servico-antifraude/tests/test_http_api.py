from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient

from adapters.http_api import app
from domain.analise_antifraude import AnaliseAntifraude

ANALISE = AnaliseAntifraude(uuid4(), uuid4(), 10, True, datetime(2026, 10, 22, 13, 0, tzinfo=UTC))


class RepositorioFalso:
    def buscar_por_solicitacao(self, solicitacao_id: UUID) -> AnaliseAntifraude | None:
        return ANALISE if solicitacao_id == ANALISE.solicitacao_id else None


@pytest.fixture
def cliente() -> TestClient:
    app.state.repositorio = RepositorioFalso()
    return TestClient(app)


@pytest.mark.parametrize(("vivo", "codigo", "status"), [(True, 200, "UP"), (False, 503, "DOWN")])
def test_health_reflete_o_consumo(cliente: TestClient, vivo: bool, codigo: int, status: str) -> None:
    app.state.consumo = SimpleNamespace(is_alive=lambda: vivo)

    response = cliente.get("/health")

    assert response.status_code == codigo
    assert response.json() == {"status": status}


def test_consulta_devolve_a_analise_no_formato_do_bff(cliente: TestClient) -> None:
    response = cliente.get(f"/v1/analises/{ANALISE.solicitacao_id}")

    assert response.status_code == 200
    assert response.json() == {
        "solicitacaoId": str(ANALISE.solicitacao_id),
        "aprovada": True,
        "score": 10,
        "motivo": ANALISE.motivo,
        "dataAnalise": "2026-10-22T10:00:00-03:00",
    }


def test_consulta_sem_analise_responde_404(cliente: TestClient) -> None:
    assert cliente.get(f"/v1/analises/{uuid4()}").status_code == 404
