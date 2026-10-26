from __future__ import annotations

import logging
from threading import Event as StopEvent

from confluent_kafka import Consumer, Message, Producer
from opentelemetry import propagate, trace
from opentelemetry.context import Context
from opentelemetry.trace import SpanKind

from application.contracting import ContractingService
from application.ports import Event
from adapters.cloudevents import EventoInvalido, ler_proposta, registro

TOPICO_PROPOSTA = "credito.proposta.v1"
GRUPO = "contratacao-proposta-v1"
log = logging.getLogger(__name__)
# O confluent-kafka não tem instrumentação automática que propague contexto: o traceparent vai e
# volta à mão nos cabeçalhos, como no antifraude, para o trace atravessar o tópico.
rastreador = trace.get_tracer(__name__)


class PublicadorKafka:
    def __init__(self, servers: str) -> None:
        self._producer = Producer(
            {
                "bootstrap.servers": servers,
                "enable.idempotence": True,
            }
        )

    def publish(self, event: Event) -> None:
        key, value, headers = registro(event)
        errors: list[object] = []
        with rastreador.start_as_current_span(f"{event.topic} publish", kind=SpanKind.PRODUCER):
            contexto: dict[str, str] = {}
            propagate.inject(contexto)
            headers = [header for header in headers if header[0] not in contexto]
            self._producer.produce(
                event.topic,
                key=key,
                value=value,
                headers=[*headers, *contexto.items()],
                on_delivery=lambda error, _message: errors.append(error),
            )
            self._producer.flush(10)
        if errors and errors[0] is not None:
            raise RuntimeError(f"falha ao publicar evento: {errors[0]}")


class ConsumidorDePropostas:
    def __init__(self, servers: str, service: ContractingService) -> None:
        self._consumer = Consumer(
            {
                "bootstrap.servers": servers,
                "group.id": GRUPO,
                "enable.auto.commit": False,
                "auto.offset.reset": "earliest",
                "allow.auto.create.topics": True,
            }
        )
        self._service = service
        self._stop = StopEvent()

    def executar(self) -> None:
        try:
            self._consumer.subscribe([TOPICO_PROPOSTA])
            while not self._stop.is_set():
                message = self._consumer.poll(1.0)
                if message is None:
                    continue
                if message.error():
                    log.warning("Erro no consumo: %s", message.error())
                    continue
                self._processar(message)
        finally:
            self._consumer.close()

    def parar(self) -> None:
        self._stop.set()

    def _processar(self, message: Message) -> None:
        with rastreador.start_as_current_span(
            f"{message.topic()} process", context=_contexto_de(message), kind=SpanKind.CONSUMER
        ):
            try:
                self._service.handle_proposal(ler_proposta(message.headers(), message.value()))
            except EventoInvalido as error:
                log.warning("Evento inválido descartado: %s", error)
            else:
                self._consumer.commit(message=message, asynchronous=False)


def _contexto_de(message: Message) -> Context:
    return propagate.extract(
        {nome: valor.decode(errors="replace") for nome, valor in message.headers() or [] if valor}
    )
