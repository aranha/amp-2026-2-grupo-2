from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal
from typing import Protocol
from uuid import UUID, uuid4

from domain.analise_antifraude import AnaliseAntifraude


@dataclass(frozen=True)
class ElegibilidadeAvaliada:
    evento_id: UUID
    solicitacao_id: UUID
    elegivel: bool
    valor_aprovado: Decimal


class RepositorioDeAnalises(Protocol):
    def registrar(self, evento_origem_id: UUID, analise: AnaliseAntifraude) -> AnaliseAntifraude: ...


class PublicadorDeAnalises(Protocol):
    def publicar(self, analise: AnaliseAntifraude) -> None: ...


class AnalisarElegibilidade:
    def __init__(
        self,
        repositorio: RepositorioDeAnalises,
        publicador: PublicadorDeAnalises,
        relogio: Callable[[], datetime] = lambda: datetime.now(UTC),
        gerar_id: Callable[[], UUID] = uuid4,
    ) -> None:
        self._repositorio = repositorio
        self._publicador = publicador
        self._relogio = relogio
        self._gerar_id = gerar_id

    def executar(self, elegibilidade: ElegibilidadeAvaliada) -> AnaliseAntifraude | None:
        if not elegibilidade.elegivel:
            return None
        analise = AnaliseAntifraude.avaliar(
            self._gerar_id(), elegibilidade.solicitacao_id, elegibilidade.valor_aprovado, self._relogio()
        )
        registrada = self._repositorio.registrar(elegibilidade.evento_id, analise)
        self._publicador.publicar(registrada)
        return registrada
