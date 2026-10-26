from datetime import UTC, datetime
from decimal import Decimal
from uuid import uuid4

import pytest

from domain.analise_antifraude import AnaliseAntifraude, ErroDeDominio


def avaliar(valor: str) -> AnaliseAntifraude:
    return AnaliseAntifraude.avaliar(uuid4(), uuid4(), Decimal(valor), datetime.now(UTC))


@pytest.mark.parametrize(
    ("valor", "score"),
    [("0.01", 10), ("5000.00", 10), ("5000.01", 30), ("20000.00", 30), ("50000.00", 60), ("50000.01", 90)],
)
def test_score_cresce_por_faixa_de_valor(valor: str, score: int) -> None:
    assert avaliar(valor).score == score


def test_aprova_score_abaixo_do_limite() -> None:
    assert avaliar("50000.00").aprovada


def test_reprova_score_acima_do_limite() -> None:
    assert not avaliar("50000.01").aprovada


@pytest.mark.parametrize(
    ("valor", "motivo"),
    [
        ("5000.00", "Score 10 abaixo do limite de aprovação (70)"),
        ("50000.01", "Score 90 igual ou acima do limite de aprovação (70)"),
    ],
)
def test_motivo_explica_o_score_frente_ao_limite(valor: str, motivo: str) -> None:
    assert avaliar(valor).motivo == motivo


@pytest.mark.parametrize("valor", ["0", "-1.00"])
def test_rejeita_valor_nao_positivo(valor: str) -> None:
    with pytest.raises(ErroDeDominio, match="positivo"):
        avaliar(valor)
