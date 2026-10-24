from __future__ import annotations

import json
from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID

from application.contracting import Proposal
from application.ports import Event


class EventoInvalido(ValueError):
    pass


def ler_proposta(
    headers: list[tuple[str, bytes]] | None, value: bytes | None
) -> Proposal:
    try:
        header_map = {key: (raw or b"").decode() for key, raw in headers or []}
        event_id = UUID(header_map["ce_id"])
        if header_map.get("ce_type") != "credito.proposta.v1":
            raise EventoInvalido("ce_type deve ser credito.proposta.v1")
        payload = json.loads(value or b"", parse_float=Decimal)
        return Proposal(
            event_id=event_id,
            solicitation_id=UUID(str(payload["solicitacaoId"])),
            proposal_id=UUID(str(payload["propostaId"])),
            approved_amount=Decimal(str(payload["valorAprovado"])),
            occurred_at=datetime.fromisoformat(payload["dataProposta"]),
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
        raise EventoInvalido(f"evento de proposta invalido: {error}") from error


def registro(event: Event) -> tuple[str, bytes, list[tuple[str, str]]]:
    event_id = UUID(str(event.payload["eventoId"]))
    now = datetime.now(UTC).isoformat()
    headers = [
        ("ce_specversion", "1.0"),
        ("ce_id", str(event_id)),
        ("ce_source", "/contratacao/eventos"),
        ("ce_type", event.event_type),
        ("ce_time", now),
        ("ce_datacontenttype", "application/json"),
    ]
    return str(event.key), json.dumps(event.payload).encode(), headers
