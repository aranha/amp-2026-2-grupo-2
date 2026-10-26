from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal
from typing import Self
from uuid import UUID

LIMITE_DE_APROVACAO = 70
PONTOS_POR_FAIXA_DE_VALOR = (
    (Decimal("5000"), 10),
    (Decimal("20000"), 30),
    (Decimal("50000"), 60),
)
PONTOS_ACIMA_DAS_FAIXAS = 90


class ErroDeDominio(ValueError):
    pass


@dataclass(frozen=True)
class AnaliseAntifraude:
    id: UUID
    solicitacao_id: UUID
    score: int
    aprovada: bool
    analisada_em: datetime

    @classmethod
    def avaliar(
        cls, id: UUID, solicitacao_id: UUID, valor_aprovado: Decimal, analisada_em: datetime
    ) -> Self:
        if valor_aprovado <= 0:
            raise ErroDeDominio("valor aprovado deve ser positivo")
        score = next(
            (pontos for teto, pontos in PONTOS_POR_FAIXA_DE_VALOR if valor_aprovado <= teto),
            PONTOS_ACIMA_DAS_FAIXAS,
        )
        return cls(id, solicitacao_id, score, score < LIMITE_DE_APROVACAO, analisada_em)

    @property
    def motivo(self) -> str:
        posicao = "abaixo do" if self.aprovada else "igual ou acima do"
        return f"Score {self.score} {posicao} limite de aprovação ({LIMITE_DE_APROVACAO})"
