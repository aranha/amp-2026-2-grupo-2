from decimal import Decimal
from uuid import uuid4

import pytest

from domain.models import (
    Contract,
    ContractStatus,
    CreditRelease,
    DomainError,
    ReleaseStatus,
)


def test_contract_can_be_contracted_once() -> None:
    contract = Contract(uuid4(), Decimal("100.00"))

    contract.contract()

    assert contract.status is ContractStatus.CONTRACTED


def test_contract_cannot_be_contracted_twice() -> None:
    contract = Contract(uuid4(), Decimal("100.00"))
    contract.contract()

    with pytest.raises(DomainError, match="already contracted"):
        contract.contract()


def test_contract_rejects_non_positive_amount() -> None:
    with pytest.raises(DomainError, match="greater than zero"):
        Contract(uuid4(), Decimal("0"))


def test_release_retries_until_dlq_after_three_failures() -> None:
    release = CreditRelease(uuid4(), max_attempts=3)

    assert release.register_failure() is ReleaseStatus.RETRYING
    assert release.register_failure() is ReleaseStatus.RETRYING
    assert release.register_failure() is ReleaseStatus.DLQ
    assert release.attempts == 3


def test_release_cannot_retry_after_dlq() -> None:
    release = CreditRelease(uuid4(), max_attempts=1)
    release.register_failure()

    with pytest.raises(DomainError, match="already in the DLQ"):
        release.register_failure()


def test_release_can_be_completed_before_retry_limit() -> None:
    release = CreditRelease(uuid4())

    release.mark_released()

    assert release.status is ReleaseStatus.RELEASED
