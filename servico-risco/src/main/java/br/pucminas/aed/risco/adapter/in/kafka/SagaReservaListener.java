package br.pucminas.aed.risco.adapter.in.kafka;

import br.pucminas.aed.risco.application.port.in.AcompanharReserva;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Eventos da saga de reserva de limite, herdada da AED: o topico decide a transicao. */
@Component
public class SagaReservaListener {

    private final AcompanharReserva acompanharReserva;
    private final ObjectMapper json;
    private final String topicoLimiteReservado;
    private final String topicoReservaCancelada;

    public SagaReservaListener(AcompanharReserva acompanharReserva, ObjectMapper json,
                               @Value("${app.kafka.topico.limite-reservado}") String topicoLimiteReservado,
                               @Value("${app.kafka.topico.reserva-cancelada}") String topicoReservaCancelada) {
        this.acompanharReserva = acompanharReserva;
        this.json = json;
        this.topicoLimiteReservado = topicoLimiteReservado;
        this.topicoReservaCancelada = topicoReservaCancelada;
    }

    @KafkaListener(
            topics = {"${app.kafka.topico.limite-reservado}", "${app.kafka.topico.reserva-cancelada}"},
            groupId = "${app.kafka.grupo.saga-reserva}")
    public void receber(ConsumerRecord<String, String> registro, Acknowledgment confirmacao)
            throws JsonProcessingException {
        String eventoId = CargaDoEvento.idDoEvento(registro);
        UUID solicitacaoId = CargaDoEvento.uuid(CargaDoEvento.objeto(json, registro.value()), "solicitacaoId");
        if (topicoLimiteReservado.equals(registro.topic())) {
            acompanharReserva.registrarReserva(eventoId, solicitacaoId);
        } else if (topicoReservaCancelada.equals(registro.topic())) {
            acompanharReserva.registrarCancelamento(eventoId, solicitacaoId);
        } else {
            throw new IllegalArgumentException("topico da saga nao reconhecido: " + registro.topic());
        }
        confirmacao.acknowledge();
    }
}
