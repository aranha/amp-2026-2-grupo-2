package br.pucminas.aed.risco.adapter.out.kafka;

import br.pucminas.aed.risco.application.port.out.PublicadorDeElegibilidade;
import br.pucminas.aed.risco.domain.ElegibilidadeAvaliada;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador de saida: {@code risco.elegibilidade.v1} em CloudEvents 1.0, binary mode, como no
 * {@code asyncapi.yaml}. Chave {@code solicitacaoId}, para cair na mesma particao dos demais
 * eventos da solicitacao; {@code ce_id} igual ao {@code eventoId} da carga.
 *
 * <p>O envio espera a confirmacao do broker ({@code acks=all}). Sem ela, a falha sobe, a
 * transacao da analise e desfeita e a retentativa do consumidor avalia de novo.
 */
@Component
public class KafkaPublicadorDeElegibilidade implements PublicadorDeElegibilidade {

    static final String TIPO = "risco.elegibilidade.v1";
    static final String FONTE = "/risco/analises";
    private static final Duration PRAZO_DA_CONFIRMACAO = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> publicadorDeEventos;
    private final ObjectMapper json;
    private final String topico;

    public KafkaPublicadorDeElegibilidade(KafkaTemplate<String, String> publicadorDeEventos, ObjectMapper json,
                                          @Value("${app.kafka.topico.elegibilidade}") String topico) {
        this.publicadorDeEventos = publicadorDeEventos;
        this.json = json;
        this.topico = topico;
    }

    @Override
    public void publicar(ElegibilidadeAvaliada evento) {
        // ce_time e dataAnalise saem do mesmo formatador: sempre com segundos e com o offset.
        String dataAnalise = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(evento.dataAnalise());
        var registro = new ProducerRecord<>(topico, evento.solicitacaoId().toString(), carga(evento, dataAnalise));
        cabecalho(registro, "ce_specversion", "1.0");
        cabecalho(registro, "ce_id", evento.eventoId().toString());
        cabecalho(registro, "ce_source", FONTE);
        cabecalho(registro, "ce_type", TIPO);
        cabecalho(registro, "ce_time", dataAnalise);
        cabecalho(registro, "ce_datacontenttype", "application/json");
        try {
            publicadorDeEventos.send(registro).get(PRAZO_DA_CONFIRMACAO.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new PublicacaoFalhouException(evento, interrompido);
        } catch (ExecutionException | TimeoutException falha) {
            throw new PublicacaoFalhouException(evento, falha);
        }
    }

    private String carga(ElegibilidadeAvaliada evento, String dataAnalise) {
        try {
            return json.writeValueAsString(new Carga(evento.eventoId().toString(),
                    evento.solicitacaoId().toString(), evento.elegivel(), evento.valorAprovado(),
                    evento.motivo(), dataAnalise));
        } catch (JsonProcessingException falha) {
            throw new IllegalStateException("carga de elegibilidade nao serializou", falha);
        }
    }

    private static void cabecalho(ProducerRecord<String, String> registro, String nome, String valor) {
        registro.headers().add(nome, valor.getBytes(StandardCharsets.UTF_8));
    }

    /** O payload de {@code ElegibilidadeAvaliada}, na ordem do contrato. */
    record Carga(String eventoId, String solicitacaoId, boolean elegivel, BigDecimal valorAprovado,
                 String motivo, String dataAnalise) {
    }
}
