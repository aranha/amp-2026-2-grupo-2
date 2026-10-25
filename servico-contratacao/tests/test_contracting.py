from datetime import UTC, datetime
from decimal import Decimal
from uuid import uuid4

from application.contracting import ContractingService, Proposal
from application.ports import CoreBankUnavailable, Event
from domain.models import ReleaseStatus


class RepositoryFake:
    def __init__(self) -> None:
        self.processed: set[object] = set()
        self.contracts: list[tuple[object, ...]] = []
        self.releases: list[tuple[object, ...]] = []

    def has_processed_event(self, event_id: object) -> bool:
        return event_id in self.processed

    def mark_event_processed(self, event_id: object) -> None:
        self.processed.add(event_id)

    def save_contract(self, *values: object) -> None:
        self.contracts.append(values)

    def save_release(self, *values: object) -> None:
        self.releases.append(values)


class PublisherFake:
    def __init__(self) -> None:
        self.events: list[Event] = []

    def publish(self, event: Event) -> None:
        self.events.append(event)


class CoreFake:
    def __init__(self, failures: int) -> None:
        self.failures = failures
        self.calls = 0

    def release_credit(self, solicitation_id: object, amount: Decimal, access_token: str | None = None) -> None:
        self.calls += 1
        if self.calls <= self.failures:
            raise CoreBankUnavailable("core unavailable")


def proposal() -> Proposal:
    return Proposal(
        event_id=uuid4(),
        solicitation_id=uuid4(),
        proposal_id=uuid4(),
        approved_amount=Decimal("3000.10"),
        occurred_at=datetime.now(UTC),
    )


def test_contracting_publishes_contract_and_release_after_core_success() -> None:
    repository = RepositoryFake()
    publisher = PublisherFake()
    core = CoreFake(failures=2)
    service = ContractingService(repository, publisher, core)

    service.handle_proposal(proposal())

    assert core.calls == 3
    assert [event.topic for event in publisher.events] == [
        "contratacao.contrato.v1",
        "contratacao.liberacao.v1",
    ]
    assert repository.releases[-1][-1] == ReleaseStatus.RELEASED.value
    assert len(repository.processed) == 1


def test_contracting_publishes_dlq_after_three_failures() -> None:
    repository = RepositoryFake()
    publisher = PublisherFake()
    service = ContractingService(repository, publisher, CoreFake(failures=3))

    service.handle_proposal(proposal())

    assert publisher.events[-1].topic == "contratacao.liberacao.v1"
    assert publisher.events[-1].payload["status"] == ReleaseStatus.DLQ.value
    assert repository.releases[-1][-1] == ReleaseStatus.DLQ.value


def test_duplicate_input_event_is_idempotent() -> None:
    repository = RepositoryFake()
    publisher = PublisherFake()
    core = CoreFake(failures=0)
    service = ContractingService(repository, publisher, core)
    event = proposal()

    service.handle_proposal(event)
    service.handle_proposal(event)

    assert len(publisher.events) == 2
    assert core.calls == 1
