import json
from datetime import UTC, datetime
from decimal import Decimal
from uuid import uuid4

import pytest

from adapters.kafka import TOPICO_ANALISE, EventoInvalido, ler_elegibilidade, registro_da_analise
from domain.analise_antifraude import AnaliseAntifraude

SOLICITACAO_ID = "75ae6c98-2856-4717-9842-33a6f1f70953"
CE_ID = "0b5f6c1e-7f7b-4d5a-9d0e-2a6d8f3c1b21"
CABECALHOS = [("ce_specversion", b"1.0"), ("ce_id", CE_ID.encode()), ("ce_type", b"risco.elegibilidade.v1")]


def carga(**campos: object) -> bytes:
    base = {
        "eventoId": CE_ID,
        "solicitacaoId": SOLICITACAO_ID,
        "elegivel": True,
        "valorAprovado": 3000.10,
        "dataAnalise": "2026-10-22T10:00:00-03:00",
        "campoNovo": "ignorado",
    }
    return json.dumps(base | campos).encode()


def test_le_elegibilidade_com_ce_id_e_valor_decimal_exato() -> None:
    evento = ler_elegibilidade(CABECALHOS, carga())

    assert str(evento.evento_id) == CE_ID
    assert str(evento.solicitacao_id) == SOLICITACAO_ID
    assert evento.elegivel is True
    assert evento.valor_aprovado == Decimal("3000.10")


@pytest.mark.parametrize(
    ("cabecalhos", "valor"),
    [
        ([], carga()),
        ([("ce_id", b"evt-eleg-1")], carga()),
        (CABECALHOS, b"{nao e json"),
        (CABECALHOS, None),
        (CABECALHOS, b"[]"),
        (CABECALHOS, carga(solicitacaoId="sol-1")),
        (CABECALHOS, carga(elegivel="sim")),
        (CABECALHOS, carga(valorAprovado="3000")),
        (CABECALHOS, json.dumps({"solicitacaoId": SOLICITACAO_ID, "elegivel": True}).encode()),
    ],
)
def test_rejeita_evento_invalido(cabecalhos: list[tuple[str, bytes]], valor: bytes | None) -> None:
    with pytest.raises(EventoInvalido):
        ler_elegibilidade(cabecalhos, valor)


def test_registro_da_analise_segue_o_contrato_cloudevents() -> None:
    analise = AnaliseAntifraude(uuid4(), uuid4(), 10, True, datetime(2026, 10, 22, 13, 0, tzinfo=UTC))

    chave, valor, cabecalhos = registro_da_analise(analise)

    assert chave == str(analise.solicitacao_id)
    assert json.loads(valor) == {
        "eventoId": str(analise.id),
        "solicitacaoId": str(analise.solicitacao_id),
        "aprovada": True,
        "score": 10,
        "dataAnalise": "2026-10-22T10:00:00-03:00",
    }
    assert dict(cabecalhos) == {
        "ce_specversion": "1.0",
        "ce_id": str(analise.id),
        "ce_source": "/antifraude/analises",
        "ce_type": TOPICO_ANALISE,
        "ce_time": "2026-10-22T10:00:00-03:00",
        "ce_datacontenttype": "application/json",
    }
