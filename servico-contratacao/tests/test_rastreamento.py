import json
from collections.abc import Callable
from types import SimpleNamespace
from uuid import uuid4

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.sdk.trace.export.in_memory_span_exporter import InMemorySpanExporter

from adapters.kafka import ConsumidorDePropostas, PublicadorKafka
from application.ports import Event


class MensagemFalsa:
    def __init__(self, *cabecalhos: tuple[str, bytes]) -> None:
        self._cabecalhos = [
            ("ce_id", str(uuid4()).encode()),
            ("ce_type", b"credito.proposta.v1"),
            *cabecalhos,
        ]
        self._valor = json.dumps({
            "eventoId": str(uuid4()),
            "solicitacaoId": str(uuid4()),
            "propostaId": str(uuid4()),
            "valorAprovado": 3000,
            "dataProposta": "2026-10-26T10:00:00-03:00",
        }).encode()

    def headers(self) -> list[tuple[str, bytes]]:
        return self._cabecalhos

    def value(self) -> bytes:
        return self._valor

    def topic(self) -> str:
        return "credito.proposta.v1"


class ProdutorFalso:
    def __init__(self) -> None:
        self.cabecalhos: list[list[tuple[str, str | bytes]]] = []

    def produce(self, topic: str, key: object, value: object, headers: list, on_delivery: Callable) -> None:
        self.cabecalhos.append(headers)
        on_delivery(None, SimpleNamespace())

    def flush(self, timeout: float) -> int:
        return 0


class ConsumidorKafkaFalso:
    def commit(self, message: MensagemFalsa, asynchronous: bool) -> None:
        pass


def test_processamento_continua_o_trace_recebido_e_o_propaga_na_publicacao() -> None:
    exportador = InMemorySpanExporter()
    provedor = TracerProvider()
    provedor.add_span_processor(SimpleSpanProcessor(exportador))
    trace.set_tracer_provider(provedor)
    trace_id, span_pai = "4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7"
    publicador = PublicadorKafka("localhost:9")
    produtor = ProdutorFalso()
    publicador._producer = produtor
    evento = Event("contratacao.contrato.v1", uuid4(), "contratacao.contrato.v1", {"eventoId": str(uuid4())})
    consumo = ConsumidorDePropostas(
        "localhost:9", SimpleNamespace(handle_proposal=lambda _: publicador.publish(evento))
    )
    consumo._consumer = ConsumidorKafkaFalso()

    consumo._processar(MensagemFalsa(("traceparent", f"00-{trace_id}-{span_pai}-01".encode())))

    spans = {span.name: span for span in exportador.get_finished_spans()}
    processamento = spans["credito.proposta.v1 process"]
    assert format(processamento.context.trace_id, "032x") == trace_id
    assert format(processamento.parent.span_id, "016x") == span_pai
    assert spans["contratacao.contrato.v1 publish"].parent.span_id == processamento.context.span_id
    assert dict(produtor.cabecalhos[0])["traceparent"].split("-")[1] == trace_id
