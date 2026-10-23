import json
from uuid import uuid4

import pytest

from adapters.kafka import ConsumidorDeElegibilidade
from application.analisar_elegibilidade import ElegibilidadeAvaliada
from domain.analise_antifraude import ErroDeDominio


class MensagemFalsa:
    def __init__(self, ce_id: str, elegivel: object = True) -> None:
        self._cabecalhos = [("ce_id", ce_id.encode())]
        self._valor = json.dumps(
            {"solicitacaoId": str(uuid4()), "elegivel": elegivel, "valorAprovado": 3000}
        ).encode()

    def headers(self) -> list[tuple[str, bytes]]:
        return self._cabecalhos

    def value(self) -> bytes:
        return self._valor

    def topic(self) -> str:
        return "risco.elegibilidade.v1"

    def partition(self) -> int:
        return 0

    def offset(self) -> int:
        return 7


class ConsumidorKafkaFalso:
    def __init__(self) -> None:
        self.confirmadas: list[MensagemFalsa] = []

    def commit(self, message: MensagemFalsa, asynchronous: bool) -> None:
        self.confirmadas.append(message)


class CasoDeUsoFalso:
    def __init__(self, falha: Exception | None = None) -> None:
        self.falha = falha
        self.recebidas: list[ElegibilidadeAvaliada] = []

    def executar(self, elegibilidade: ElegibilidadeAvaliada) -> None:
        if self.falha:
            raise self.falha
        self.recebidas.append(elegibilidade)


def consumidor(caso_de_uso: CasoDeUsoFalso) -> tuple[ConsumidorDeElegibilidade, ConsumidorKafkaFalso]:
    consumo = ConsumidorDeElegibilidade("localhost:9", caso_de_uso)
    kafka = ConsumidorKafkaFalso()
    consumo._consumidor = kafka
    return consumo, kafka


def test_evento_valido_e_processado_e_confirmado() -> None:
    caso_de_uso = CasoDeUsoFalso()
    consumo, kafka = consumidor(caso_de_uso)
    mensagem = MensagemFalsa(str(uuid4()))

    consumo._processar(mensagem)

    assert len(caso_de_uso.recebidas) == 1
    assert kafka.confirmadas == [mensagem]


@pytest.mark.parametrize(
    ("mensagem", "falha"),
    [(MensagemFalsa("nao-e-uuid"), None), (MensagemFalsa(str(uuid4())), ErroDeDominio("valor"))],
)
def test_evento_invalido_e_descartado_e_confirmado(mensagem: MensagemFalsa, falha: Exception | None) -> None:
    consumo, kafka = consumidor(CasoDeUsoFalso(falha))

    consumo._processar(mensagem)

    assert kafka.confirmadas == [mensagem]


@pytest.mark.parametrize("falha", [ConnectionError("banco fora"), ValueError("erro inesperado")])
def test_falha_de_infraestrutura_propaga_sem_confirmar(falha: Exception) -> None:
    consumo, kafka = consumidor(CasoDeUsoFalso(falha))

    with pytest.raises(type(falha)):
        consumo._processar(MensagemFalsa(str(uuid4())))

    assert kafka.confirmadas == []
