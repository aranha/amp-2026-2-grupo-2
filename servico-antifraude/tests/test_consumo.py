import json
from collections.abc import Callable
from datetime import UTC, datetime
from types import SimpleNamespace
from uuid import uuid4

import pytest
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from adapters.kafka import (
    PERMANENTE,
    RETENTATIVAS,
    TRANSITORIA,
    ConsumidorDeElegibilidade,
    PublicadorKafka,
    cabecalhos_da_dlq,
)
from application.analisar_elegibilidade import ElegibilidadeAvaliada
from domain.analise_antifraude import AnaliseAntifraude, ErroDeDominio


class MensagemFalsa:
    def __init__(self, ce_id: str, *cabecalhos: tuple[str, bytes]) -> None:
        self._cabecalhos = [("ce_id", ce_id.encode()), *cabecalhos]
        self._valor = json.dumps(
            {"solicitacaoId": str(uuid4()), "elegivel": True, "valorAprovado": 3000}
        ).encode()

    def headers(self) -> list[tuple[str, bytes]]:
        return self._cabecalhos

    def key(self) -> bytes:
        return b"chave"

    def value(self) -> bytes:
        return self._valor

    def topic(self) -> str:
        return "risco.elegibilidade.v1"

    def partition(self) -> int:
        return 2

    def offset(self) -> int:
        return 7


class ConsumidorKafkaFalso:
    def __init__(self) -> None:
        self.confirmadas: list[MensagemFalsa] = []

    def commit(self, message: MensagemFalsa, asynchronous: bool) -> None:
        self.confirmadas.append(message)


class PublicadorFalso:
    def __init__(self) -> None:
        self.na_dlq: list[tuple[MensagemFalsa, str]] = []

    def enviar_para_dlq(self, mensagem: MensagemFalsa, falha: Exception, classificacao: str) -> None:
        self.na_dlq.append((mensagem, classificacao))


class CasoDeUsoFalso:
    def __init__(self, falha: Exception | None = None) -> None:
        self.falha = falha
        self.recebidas: list[ElegibilidadeAvaliada] = []

    def executar(self, elegibilidade: ElegibilidadeAvaliada) -> None:
        if self.falha:
            raise self.falha
        self.recebidas.append(elegibilidade)


def consumidor(
    caso_de_uso: CasoDeUsoFalso,
) -> tuple[ConsumidorDeElegibilidade, ConsumidorKafkaFalso, PublicadorFalso]:
    publicador = PublicadorFalso()
    consumo = ConsumidorDeElegibilidade("localhost:9", caso_de_uso, publicador)
    kafka = ConsumidorKafkaFalso()
    consumo._consumidor = kafka
    return consumo, kafka, publicador


def test_evento_valido_e_processado_e_confirmado() -> None:
    caso_de_uso = CasoDeUsoFalso()
    consumo, kafka, publicador = consumidor(caso_de_uso)
    mensagem = MensagemFalsa(str(uuid4()))

    consumo._processar(mensagem, 0)

    assert len(caso_de_uso.recebidas) == 1
    assert kafka.confirmadas == [mensagem]
    assert publicador.na_dlq == []


@pytest.mark.parametrize(
    ("mensagem", "falha"),
    [(MensagemFalsa("nao-e-uuid"), None), (MensagemFalsa(str(uuid4())), ErroDeDominio("valor"))],
)
def test_evento_invalido_vai_direto_para_a_dlq_como_permanente(
    mensagem: MensagemFalsa, falha: Exception | None
) -> None:
    consumo, kafka, publicador = consumidor(CasoDeUsoFalso(falha))

    consumo._processar(mensagem, 0)

    assert publicador.na_dlq == [(mensagem, PERMANENTE)]
    assert kafka.confirmadas == [mensagem]


@pytest.mark.parametrize("falha", [ConnectionError("banco fora"), ValueError("erro inesperado")])
def test_falha_de_infraestrutura_propaga_enquanto_houver_retentativa(falha: Exception) -> None:
    consumo, kafka, publicador = consumidor(CasoDeUsoFalso(falha))

    with pytest.raises(type(falha)):
        consumo._processar(MensagemFalsa(str(uuid4())), RETENTATIVAS - 1)

    assert kafka.confirmadas == []
    assert publicador.na_dlq == []


def test_falha_de_infraestrutura_vai_para_a_dlq_ao_esgotar_as_retentativas() -> None:
    consumo, kafka, publicador = consumidor(CasoDeUsoFalso(ConnectionError("banco fora")))
    mensagem = MensagemFalsa(str(uuid4()))

    consumo._processar(mensagem, RETENTATIVAS)

    assert publicador.na_dlq == [(mensagem, TRANSITORIA)]
    assert kafka.confirmadas == [mensagem]


def test_cabecalhos_da_dlq_preservam_os_originais_e_registram_motivo_e_origem() -> None:
    mensagem = MensagemFalsa("evt-1")

    cabecalhos = dict(cabecalhos_da_dlq(mensagem, ValueError("motivo"), PERMANENTE))

    assert cabecalhos == {
        "ce_id": b"evt-1",
        "classificacao": PERMANENTE,
        "kafka_dlt-exception-message": "motivo",
        "kafka_dlt-original-topic": "risco.elegibilidade.v1",
        "kafka_dlt-original-partition": (2).to_bytes(4, "big"),
        "kafka_dlt-original-offset": (7).to_bytes(8, "big"),
    }


class ProdutorFalso:
    def __init__(self) -> None:
        self.cabecalhos: list[list[tuple[str, str | bytes]]] = []

    def produce(self, topico: str, key: object, value: object, headers: list, on_delivery: Callable) -> None:
        self.cabecalhos.append(headers)
        on_delivery(None, SimpleNamespace(partition=lambda: 0, offset=lambda: 0))

    def flush(self, timeout: float) -> int:
        return 0


def test_processamento_continua_o_trace_recebido_e_o_propaga_na_publicacao() -> None:
    exportador = InMemorySpanExporter()
    provedor = TracerProvider()
    provedor.add_span_processor(SimpleSpanProcessor(exportador))
    trace.set_tracer_provider(provedor)
    trace_id, span_pai = "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7"
    publicador = PublicadorKafka("localhost:9")
    produtor = ProdutorFalso()
    publicador._produtor = produtor
    analise = AnaliseAntifraude(uuid4(), uuid4(), 10, True, datetime.now(UTC))
    consumo = ConsumidorDeElegibilidade(
        "localhost:9", SimpleNamespace(executar=lambda _: publicador.publicar(analise)), publicador
    )
    consumo._consumidor = ConsumidorKafkaFalso()

    traceparent = ("traceparent", f"00-{trace_id}-{span_pai}-01".encode())

    consumo._processar(MensagemFalsa(str(uuid4()), traceparent), 0)

    spans = {span.name: span for span in exportador.get_finished_spans()}
    processamento = spans["risco.elegibilidade.v1 process"]
    assert format(processamento.context.trace_id, "032x") == trace_id
    assert format(processamento.parent.span_id, "016x") == span_pai
    assert spans["antifraude.analise.v1 publish"].parent.span_id == processamento.context.span_id
    assert dict(produtor.cabecalhos[0])["traceparent"].split("-")[1] == trace_id
