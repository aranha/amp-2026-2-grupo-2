from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID, uuid4

from application.analisar_elegibilidade import AnalisarElegibilidade, ElegibilidadeAvaliada
from domain.analise_antifraude import AnaliseAntifraude

EVENTO_ID = uuid4()


class RepositorioEmMemoria:
    def __init__(self) -> None:
        self.analises: dict[UUID, AnaliseAntifraude] = {}

    def registrar(self, evento_origem_id: UUID, analise: AnaliseAntifraude) -> AnaliseAntifraude:
        return self.analises.setdefault(evento_origem_id, analise)


class PublicadorEmMemoria:
    def __init__(self) -> None:
        self.publicadas: list[AnaliseAntifraude] = []

    def publicar(self, analise: AnaliseAntifraude) -> None:
        self.publicadas.append(analise)


def preparar() -> tuple[AnalisarElegibilidade, RepositorioEmMemoria, PublicadorEmMemoria]:
    repositorio, publicador = RepositorioEmMemoria(), PublicadorEmMemoria()
    instante = datetime(2026, 10, 22, 13, 0, tzinfo=UTC)
    return AnalisarElegibilidade(repositorio, publicador, relogio=lambda: instante), repositorio, publicador


def elegibilidade(elegivel: bool = True) -> ElegibilidadeAvaliada:
    return ElegibilidadeAvaliada(EVENTO_ID, uuid4(), elegivel, Decimal("3000.00"))


def test_elegibilidade_aprovada_registra_e_publica_a_analise() -> None:
    caso_de_uso, repositorio, publicador = preparar()
    evento = elegibilidade()

    analise = caso_de_uso.executar(evento)

    assert analise is not None
    assert analise.solicitacao_id == evento.solicitacao_id
    assert repositorio.analises == {EVENTO_ID: analise}
    assert publicador.publicadas == [analise]


def test_elegibilidade_recusada_encerra_o_fluxo_sem_analise() -> None:
    caso_de_uso, repositorio, publicador = preparar()

    assert caso_de_uso.executar(elegibilidade(elegivel=False)) is None
    assert repositorio.analises == {}
    assert publicador.publicadas == []


def test_reentrega_republica_a_mesma_analise() -> None:
    caso_de_uso, repositorio, publicador = preparar()
    evento = elegibilidade()

    primeira = caso_de_uso.executar(evento)
    segunda = caso_de_uso.executar(evento)

    assert segunda == primeira
    assert len(repositorio.analises) == 1
    assert publicador.publicadas == [primeira, primeira]
