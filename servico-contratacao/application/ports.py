from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol
from uuid import UUID


class CoreBankUnavailable(ConnectionError):
    pass


@dataclass(frozen=True)
class Event:
    topic: str
    key: UUID
    event_type: str
    payload: dict[str, object]


class EventPublisher(Protocol):
    def publish(self, event: Event) -> None: ...


class ContractRepository(Protocol):
    def has_processed_event(self, event_id: UUID) -> bool: ...

    def mark_event_processed(self, event_id: UUID) -> None: ...

    def save_contract(
        self,
        solicitation_id: UUID,
        proposal_id: UUID,
        contract_id: UUID,
        approved_amount: Decimal,
        status: str,
    ) -> None: ...

    def save_release(
        self,
        solicitation_id: UUID,
        contract_id: UUID,
        approved_amount: Decimal,
        attempts: int,
        status: str,
    ) -> None: ...


class CoreBankClient(Protocol):
    def release_credit(
        self, solicitation_id: UUID, amount: Decimal, access_token: str | None = None
    ) -> None: ...
