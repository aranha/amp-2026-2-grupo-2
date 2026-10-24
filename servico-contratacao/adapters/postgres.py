from __future__ import annotations

from uuid import UUID

import psycopg

from domain.models import ContractStatus, ReleaseStatus


SCHEMA = """
create table if not exists contrato (
    solicitacao_id uuid primary key,
    proposta_id uuid not null,
    contrato_id uuid not null unique,
    valor_aprovado numeric(19, 2) not null check (valor_aprovado > 0),
    status varchar(32) not null,
    criado_em timestamptz not null default current_timestamp
);
create table if not exists liberacao_credito (
    solicitacao_id uuid primary key references contrato (solicitacao_id),
    contrato_id uuid not null,
    valor_liberado numeric(19, 2) not null check (valor_liberado > 0),
    tentativas integer not null default 0 check (tentativas >= 0),
    status varchar(32) not null,
    atualizado_em timestamptz not null default current_timestamp
);
create table if not exists evento_processado (
    evento_id uuid primary key,
    processado_em timestamptz not null default current_timestamp
);
"""


class RepositorioPostgres:
    def __init__(self, url: str) -> None:
        self._url = url

    def criar_esquema(self) -> None:
        with self._conectar() as connection:
            connection.execute(SCHEMA)

    def has_processed_event(self, event_id: UUID) -> bool:
        with self._conectar() as connection:
            row = connection.execute(
                "select 1 from evento_processado where evento_id = %s", (event_id,)
            ).fetchone()
        return row is not None

    def mark_event_processed(self, event_id: UUID) -> None:
        with self._conectar() as connection:
            connection.execute(
                "insert into evento_processado (evento_id) values (%s) on conflict do nothing",
                (event_id,),
            )

    def save_contract(
        self,
        solicitation_id: UUID,
        proposal_id: UUID,
        contract_id: UUID,
        approved_amount: object,
        status: str,
    ) -> None:
        with self._conectar() as connection:
            connection.execute(
                """
                insert into contrato
                    (solicitacao_id, proposta_id, contrato_id, valor_aprovado, status)
                values (%s, %s, %s, %s, %s)
                on conflict (solicitacao_id) do update set
                    proposta_id = excluded.proposta_id,
                    contrato_id = excluded.contrato_id,
                    valor_aprovado = excluded.valor_aprovado,
                    status = excluded.status
                """,
                (solicitation_id, proposal_id, contract_id, approved_amount, status),
            )

    def save_release(
        self,
        solicitation_id: UUID,
        contract_id: UUID,
        approved_amount: object,
        attempts: int,
        status: str,
    ) -> None:
        with self._conectar() as connection:
            connection.execute(
                """
                insert into liberacao_credito
                    (solicitacao_id, contrato_id, valor_liberado, tentativas, status)
                values (%s, %s, %s, %s, %s)
                on conflict (solicitacao_id) do update set
                    contrato_id = excluded.contrato_id,
                    valor_liberado = excluded.valor_liberado,
                    tentativas = excluded.tentativas,
                    status = excluded.status,
                    atualizado_em = current_timestamp
                """,
                (solicitation_id, contract_id, approved_amount, attempts, status),
            )

    def _conectar(self) -> psycopg.Connection:
        return psycopg.connect(self._url, connect_timeout=10)
