from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Protocol
from uuid import UUID


@dataclass(frozen=True)
class Event:
    topic: str
    key: UUID
    event_type: str
    payload: dict[str, object]


class EventPublisher(Protocol):
    def publish(self, event: Event) -> None: ...


class ContractRepository(Protocol):
    def save_contract(
        self, solicitation_id: UUID, approved_amount: Decimal, status: str
    ) -> None: ...

    def save_release(
        self, solicitation_id: UUID, attempts: int, status: str
    ) -> None: ...


class CoreBankClient(Protocol):
    def release_credit(self, solicitation_id: UUID) -> None: ...
