import json
from decimal import Decimal
from uuid import uuid4

import pytest

from adapters.cloudevents import EventoInvalido, ler_proposta, registro
from application.ports import Event


def test_le_proposta_preserva_decimal_e_ignora_campos_adicionais() -> None:
    event_id = uuid4()
    solicitation_id = uuid4()
    proposal_id = uuid4()
    payload = {
        "eventoId": str(event_id),
        "solicitacaoId": str(solicitation_id),
        "propostaId": str(proposal_id),
        "valorAprovado": 3000.10,
        "dataProposta": "2026-10-24T10:00:00-03:00",
        "campoNovo": "compatível",
    }

    proposal = ler_proposta(
        [
            ("ce_id", str(event_id).encode()),
            ("ce_type", b"credito.proposta.v1"),
        ],
        json.dumps(payload).encode(),
    )

    assert proposal.event_id == event_id
    assert proposal.solicitation_id == solicitation_id
    assert proposal.proposal_id == proposal_id
    assert proposal.approved_amount == Decimal("3000.1")


@pytest.mark.parametrize(
    "headers",
    [
        [],
        [("ce_id", b"not-an-uuid"), ("ce_type", b"credito.proposta.v1")],
        [("ce_id", str(uuid4()).encode()), ("ce_type", b"other.v1")],
    ],
)
def test_rejeita_headers_invalidos(headers: list[tuple[str, bytes]]) -> None:
    with pytest.raises(EventoInvalido):
        ler_proposta(headers, b"{}")


def test_registro_usa_chave_e_headers_cloudevents() -> None:
    event_id = uuid4()
    solicitation_id = uuid4()
    event = Event(
        topic="contratacao.contrato.v1",
        key=solicitation_id,
        event_type="contratacao.contrato.v1",
        payload={"eventoId": str(event_id), "status": "CONTRACTED"},
    )

    key, value, headers = registro(event)

    assert key == str(solicitation_id)
    assert json.loads(value) == event.payload
    assert dict(headers)["ce_id"] == str(event_id)
    assert dict(headers)["ce_type"] == "contratacao.contrato.v1"
