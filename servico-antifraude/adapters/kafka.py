import json
import logging
from datetime import timedelta, timezone
from decimal import Decimal
from threading import Event
from uuid import UUID

from confluent_kafka import Consumer, KafkaException, Message, Producer, TopicPartition
from opentelemetry import propagate, trace
from opentelemetry.context import Context
from opentelemetry.trace import SpanKind

from application.analisar_elegibilidade import AnalisarElegibilidade, ElegibilidadeAvaliada
from domain.analise_antifraude import AnaliseAntifraude, ErroDeDominio

TOPICO_ELEGIBILIDADE = "risco.elegibilidade.v1"
TOPICO_ANALISE = "antifraude.analise.v1"
SUFIXO_DLQ = ".dlq"
GRUPO = "antifraude-analise-v1"
FONTE = "/antifraude/analises"
BRASILIA = timezone(timedelta(hours=-3))
RETENTATIVAS = 4
PERMANENTE = "PERMANENTE"
TRANSITORIA = "TRANSITORIA"

Cabecalhos = list[tuple[str, str | bytes]]

log = logging.getLogger(__name__)
rastreador = trace.get_tracer(__name__)


class EventoInvalido(ValueError):
    pass


def ler_elegibilidade(
    cabecalhos: list[tuple[str, bytes]] | None, valor: bytes | None
) -> ElegibilidadeAvaliada:
    try:
        ce_id = UUID((dict(cabecalhos or []).get("ce_id") or b"").decode())
        carga = json.loads(valor or b"", parse_float=Decimal)
        solicitacao_id = UUID(str(carga["solicitacaoId"]))
        elegivel = carga["elegivel"]
        valor_aprovado = carga["valorAprovado"]
    except (KeyError, TypeError, ValueError) as falha:
        raise EventoInvalido(f"evento invalido: {falha!r}") from falha
    if not isinstance(elegivel, bool):
        raise EventoInvalido("elegivel deve ser booleano")
    if isinstance(valor_aprovado, bool) or not isinstance(valor_aprovado, int | Decimal):
        raise EventoInvalido("valorAprovado deve ser numerico")
    return ElegibilidadeAvaliada(ce_id, solicitacao_id, elegivel, Decimal(valor_aprovado))


def registro_da_analise(analise: AnaliseAntifraude) -> tuple[str, bytes, Cabecalhos]:
    data = analise.analisada_em.astimezone(BRASILIA).isoformat()
    carga = {
        "eventoId": str(analise.id),
        "solicitacaoId": str(analise.solicitacao_id),
        "aprovada": analise.aprovada,
        "score": analise.score,
        "motivo": analise.motivo,
        "dataAnalise": data,
    }
    cabecalhos: Cabecalhos = [
        ("ce_specversion", "1.0"),
        ("ce_id", str(analise.id)),
        ("ce_source", FONTE),
        ("ce_type", TOPICO_ANALISE),
        ("ce_time", data),
        ("ce_datacontenttype", "application/json"),
    ]
    return str(analise.solicitacao_id), json.dumps(carga, ensure_ascii=False).encode(), cabecalhos


def cabecalhos_da_dlq(mensagem: Message, falha: Exception, classificacao: str) -> Cabecalhos:
    return [
        *(mensagem.headers() or []),
        ("classificacao", classificacao),
        ("kafka_dlt-exception-message", str(falha)),
        ("kafka_dlt-original-topic", mensagem.topic()),
        ("kafka_dlt-original-partition", mensagem.partition().to_bytes(4, "big")),
        ("kafka_dlt-original-offset", mensagem.offset().to_bytes(8, "big")),
    ]


class PublicadorKafka:
    def __init__(self, servidores: str) -> None:
        self._produtor = Producer({
            "bootstrap.servers": servidores,
            "enable.idempotence": True,
            "partitioner": "murmur2_random",
        })

    def publicar(self, analise: AnaliseAntifraude) -> None:
        entregue = self._enviar(TOPICO_ANALISE, *registro_da_analise(analise))
        log.info("Evento %s publicado na particao %s e offset %s",
                 analise.id, entregue.partition(), entregue.offset())

    def enviar_para_dlq(self, mensagem: Message, falha: Exception, classificacao: str) -> None:
        self._enviar(mensagem.topic() + SUFIXO_DLQ, mensagem.key(), mensagem.value(),
                     cabecalhos_da_dlq(mensagem, falha, classificacao))
        log.warning("Evento enviado para a DLQ | %s | %s | %s", _posicao(mensagem), classificacao, falha)

    def _enviar(self, topico: str, chave: str | bytes | None, valor: bytes | None,
                cabecalhos: Cabecalhos) -> Message:
        entregas: list[tuple[object, Message]] = []
        with rastreador.start_as_current_span(f"{topico} publish", kind=SpanKind.PRODUCER):
            contexto: dict[str, str] = {}
            propagate.inject(contexto)
            cabecalhos = [cabecalho for cabecalho in cabecalhos if cabecalho[0] not in contexto]
            self._produtor.produce(
                topico, key=chave, value=valor, headers=[*cabecalhos, *contexto.items()],
                on_delivery=lambda falha, mensagem: entregas.append((falha, mensagem)),
            )
            self._produtor.flush(10)
        if not entregas or entregas[0][0]:
            raise KafkaException(entregas[0][0] if entregas else "tempo esgotado ao publicar")
        return entregas[0][1]


class ConsumidorDeElegibilidade:
    def __init__(
        self, servidores: str, caso_de_uso: AnalisarElegibilidade, publicador: PublicadorKafka
    ) -> None:
        self._consumidor = Consumer({
            "bootstrap.servers": servidores,
            "group.id": GRUPO,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
            "allow.auto.create.topics": True,
        })
        self._caso_de_uso = caso_de_uso
        self._publicador = publicador
        self._parada = Event()

    def executar(self) -> None:
        falhas: dict[tuple[str, int, int], int] = {}
        try:
            self._consumidor.subscribe([TOPICO_ELEGIBILIDADE])
            while not self._parada.is_set():
                mensagem = self._consumidor.poll(1.0)
                if mensagem is None:
                    continue
                if mensagem.error():
                    log.warning("Erro no consumo: %s", mensagem.error())
                    continue
                posicao = (mensagem.topic(), mensagem.partition(), mensagem.offset())
                try:
                    self._processar(mensagem, falhas.get(posicao, 0))
                    falhas.pop(posicao, None)
                except Exception:
                    tentativa = falhas[posicao] = falhas.get(posicao, 0) + 1
                    log.exception("Falha ao processar, tentativa %s | %s", tentativa, _posicao(mensagem))
                    self._consumidor.seek(TopicPartition(*posicao))
                    self._parada.wait(min(0.5 * 2 ** (tentativa - 1), 5))
        finally:
            self._consumidor.close()

    def parar(self) -> None:
        self._parada.set()

    def _processar(self, mensagem: Message, falhas_anteriores: int) -> None:
        with rastreador.start_as_current_span(
            f"{mensagem.topic()} process", context=_contexto_de(mensagem), kind=SpanKind.CONSUMER
        ):
            try:
                self._caso_de_uso.executar(ler_elegibilidade(mensagem.headers(), mensagem.value()))
            except (EventoInvalido, ErroDeDominio) as falha:
                self._publicador.enviar_para_dlq(mensagem, falha, PERMANENTE)
            except Exception as falha:
                if falhas_anteriores < RETENTATIVAS:
                    raise
                self._publicador.enviar_para_dlq(mensagem, falha, TRANSITORIA)
            self._consumidor.commit(message=mensagem, asynchronous=False)


def _contexto_de(mensagem: Message) -> Context:
    return propagate.extract(
        {nome: valor.decode(errors="replace") for nome, valor in mensagem.headers() or [] if valor}
    )


def _posicao(mensagem: Message) -> str:
    return f"{mensagem.topic()}-{mensagem.partition()}@{mensagem.offset()}"
