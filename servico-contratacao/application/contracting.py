from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID, uuid4

from application.ports import (
    ContractRepository,
    CoreBankClient,
    CoreBankUnavailable,
    Event,
    EventPublisher,
)
from domain.models import Contract, CreditRelease, ReleaseStatus


@dataclass(frozen=True)
class Proposal:
    event_id: UUID
    solicitation_id: UUID
    proposal_id: UUID
    approved_amount: Decimal
    occurred_at: datetime


class ContractingService:
    def __init__(
        self,
        repository: ContractRepository,
        publisher: EventPublisher,
        core: CoreBankClient,
        max_attempts: int = 3,
        access_token: str | None = None,
    ) -> None:
        self._repository = repository
        self._publisher = publisher
        self._core = core
        self._max_attempts = max_attempts
        self._access_token = access_token

    def handle_proposal(self, proposal: Proposal) -> None:
        if self._repository.has_processed_event(proposal.event_id):
            return

        contract_id = uuid4()
        contract = Contract(
            solicitation_id=proposal.solicitation_id,
            approved_amount=proposal.approved_amount,
            proposal_id=proposal.proposal_id,
            contract_id=contract_id,
        )
        contract.contract()
        self._repository.save_contract(
            proposal.solicitation_id,
            proposal.proposal_id,
            contract_id,
            proposal.approved_amount,
            contract.status.value,
        )
        self._publisher.publish(
            Event(
                topic="contratacao.contrato.v1",
                key=proposal.solicitation_id,
                event_type="contratacao.contrato.v1",
                payload={
                    "eventoId": str(uuid4()),
                    "solicitacaoId": str(proposal.solicitation_id),
                    "contratoId": str(contract_id),
                    "propostaId": str(proposal.proposal_id),
                    "status": contract.status.value,
                    "dataContrato": datetime.now(UTC).isoformat(),
                },
            )
        )

        release = CreditRelease(
            solicitation_id=proposal.solicitation_id,
            contract_id=contract_id,
            approved_amount=proposal.approved_amount,
            max_attempts=self._max_attempts,
        )
        while release.status is not ReleaseStatus.RELEASED:
            try:
                self._core.release_credit(
                    proposal.solicitation_id,
                    proposal.approved_amount,
                    self._access_token,
                )
                release.mark_released()
            except CoreBankUnavailable:
                release.register_failure()
                self._repository.save_release(
                    proposal.solicitation_id,
                    contract_id,
                    proposal.approved_amount,
                    release.attempts,
                    release.status.value,
                )
                if release.status is ReleaseStatus.DLQ:
                    self._publisher.publish(
                        Event(
                            topic="contratacao.liberacao.v1",
                            key=proposal.solicitation_id,
                            event_type="contratacao.liberacao.v1",
                            payload={
                                "eventoId": str(uuid4()),
                                "solicitacaoId": str(proposal.solicitation_id),
                                "contratoId": str(contract_id),
                                "status": release.status.value,
                                "valorLiberado": str(proposal.approved_amount),
                                "dataLiberacao": datetime.now(UTC).isoformat(),
                            },
                        )
                    )
                    self._repository.mark_event_processed(proposal.event_id)
                    return
        self._repository.save_release(
            proposal.solicitation_id,
            contract_id,
            proposal.approved_amount,
            release.attempts,
            release.status.value,
        )
        self._publisher.publish(
            Event(
                topic="contratacao.liberacao.v1",
                key=proposal.solicitation_id,
                event_type="contratacao.liberacao.v1",
                payload={
                    "eventoId": str(uuid4()),
                    "solicitacaoId": str(proposal.solicitation_id),
                    "contratoId": str(contract_id),
                    "status": release.status.value,
                    "valorLiberado": str(proposal.approved_amount),
                    "dataLiberacao": datetime.now(UTC).isoformat(),
                },
            )
        )
        self._repository.mark_event_processed(proposal.event_id)
