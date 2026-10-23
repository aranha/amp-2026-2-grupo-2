import json
import logging
from datetime import timedelta, timezone
from decimal import Decimal
from threading import Event
from uuid import UUID

from confluent_kafka import Consumer, KafkaException, Message, Producer, TopicPartition

from application.analisar_elegibilidade import AnalisarElegibilidade, ElegibilidadeAvaliada
from domain.analise_antifraude import AnaliseAntifraude, ErroDeDominio

TOPICO_ELEGIBILIDADE = "risco.elegibilidade.v1"
TOPICO_ANALISE = "antifraude.analise.v1"
GRUPO = "antifraude-analise-v1"
FONTE = "/antifraude/analises"
BRASILIA = timezone(timedelta(hours=-3))

log = logging.getLogger(__name__)


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


def registro_da_analise(analise: AnaliseAntifraude) -> tuple[str, bytes, list[tuple[str, str]]]:
    data = analise.analisada_em.astimezone(BRASILIA).isoformat()
    carga = {
        "eventoId": str(analise.id),
        "solicitacaoId": str(analise.solicitacao_id),
        "aprovada": analise.aprovada,
        "score": analise.score,
        "dataAnalise": data,
    }
    cabecalhos = [
        ("ce_specversion", "1.0"),
        ("ce_id", str(analise.id)),
        ("ce_source", FONTE),
        ("ce_type", TOPICO_ANALISE),
        ("ce_time", data),
        ("ce_datacontenttype", "application/json"),
    ]
    return str(analise.solicitacao_id), json.dumps(carga).encode(), cabecalhos


class PublicadorKafka:
    def __init__(self, servidores: str) -> None:
        self._produtor = Producer({
            "bootstrap.servers": servidores,
            "enable.idempotence": True,
            "partitioner": "murmur2_random",
        })

    def publicar(self, analise: AnaliseAntifraude) -> None:
        chave, valor, cabecalhos = registro_da_analise(analise)
        entregas: list[tuple[object, Message]] = []
        self._produtor.produce(
            TOPICO_ANALISE, key=chave, value=valor, headers=cabecalhos,
            on_delivery=lambda falha, mensagem: entregas.append((falha, mensagem)),
        )
        self._produtor.flush(10)
        if not entregas or entregas[0][0]:
            raise KafkaException(entregas[0][0] if entregas else "tempo esgotado ao publicar")
        mensagem = entregas[0][1]
        log.info("Evento %s publicado na particao %s e offset %s",
                 analise.id, mensagem.partition(), mensagem.offset())


class ConsumidorDeElegibilidade:
    def __init__(self, servidores: str, caso_de_uso: AnalisarElegibilidade) -> None:
        self._consumidor = Consumer({
            "bootstrap.servers": servidores,
            "group.id": GRUPO,
            "enable.auto.commit": False,
            "auto.offset.reset": "earliest",
            "allow.auto.create.topics": True,
        })
        self._caso_de_uso = caso_de_uso
        self._parada = Event()

    def executar(self) -> None:
        try:
            self._consumidor.subscribe([TOPICO_ELEGIBILIDADE])
            while not self._parada.is_set():
                mensagem = self._consumidor.poll(1.0)
                if mensagem is None:
                    continue
                if mensagem.error():
                    log.warning("Erro no consumo: %s", mensagem.error())
                    continue
                try:
                    self._processar(mensagem)
                except Exception:
                    log.exception("Falha ao processar, nova tentativa | %s", _posicao(mensagem))
                    self._consumidor.seek(
                        TopicPartition(mensagem.topic(), mensagem.partition(), mensagem.offset())
                    )
                    self._parada.wait(1)
        finally:
            self._consumidor.close()

    def parar(self) -> None:
        self._parada.set()

    def _processar(self, mensagem: Message) -> None:
        try:
            self._caso_de_uso.executar(ler_elegibilidade(mensagem.headers(), mensagem.value()))
        except (EventoInvalido, ErroDeDominio) as falha:
            log.warning("Evento invalido descartado | %s | %s", _posicao(mensagem), falha)
        self._consumidor.commit(message=mensagem, asynchronous=False)


def _posicao(mensagem: Message) -> str:
    return f"{mensagem.topic()}-{mensagem.partition()}@{mensagem.offset()}"
