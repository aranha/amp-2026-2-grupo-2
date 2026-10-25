from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum
from uuid import UUID


class DomainError(ValueError):
    """Raised when a domain invariant is violated."""


class ContractStatus(StrEnum):
    RECEIVED = "RECEIVED"
    CONTRACTED = "CONTRACTED"
    CANCELLED = "CANCELLED"


class ReleaseStatus(StrEnum):
    PENDING = "PENDING"
    RELEASED = "RELEASED"
    RETRYING = "RETRYING"
    DLQ = "DLQ"


@dataclass
class Contract:
    solicitation_id: UUID
    approved_amount: Decimal
    proposal_id: UUID | None = None
    contract_id: UUID | None = None
    status: ContractStatus = ContractStatus.RECEIVED

    def __post_init__(self) -> None:
        if self.approved_amount <= Decimal("0"):
            raise DomainError("approved_amount must be greater than zero")

    def contract(self) -> None:
        if self.status is ContractStatus.CANCELLED:
            raise DomainError("a cancelled contract cannot be contracted")
        if self.status is ContractStatus.CONTRACTED:
            raise DomainError("the contract is already contracted")
        self.status = ContractStatus.CONTRACTED

    def cancel(self) -> None:
        if self.status is ContractStatus.CONTRACTED:
            raise DomainError("a contracted contract cannot be cancelled")
        self.status = ContractStatus.CANCELLED


@dataclass
class CreditRelease:
    solicitation_id: UUID
    contract_id: UUID | None = None
    approved_amount: Decimal | None = None
    max_attempts: int = 3
    attempts: int = 0
    status: ReleaseStatus = ReleaseStatus.PENDING

    def __post_init__(self) -> None:
        if self.max_attempts < 1:
            raise DomainError("max_attempts must be at least one")
        if self.attempts < 0 or self.attempts > self.max_attempts:
            raise DomainError("attempts must be between zero and max_attempts")

    def mark_released(self) -> None:
        if self.status is ReleaseStatus.RELEASED:
            raise DomainError("the credit is already released")
        if self.status is ReleaseStatus.DLQ:
            raise DomainError("a release in the DLQ cannot be released automatically")
        self.status = ReleaseStatus.RELEASED

    def register_failure(self) -> ReleaseStatus:
        if self.status is ReleaseStatus.RELEASED:
            raise DomainError("a released credit cannot fail")
        if self.status is ReleaseStatus.DLQ:
            raise DomainError("the release is already in the DLQ")

        self.attempts += 1
        self.status = (
            ReleaseStatus.DLQ
            if self.attempts >= self.max_attempts
            else ReleaseStatus.RETRYING
        )
        return self.status
