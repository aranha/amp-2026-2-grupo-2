package br.pucminas.aed.risco.adapter.out.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class PublicacaoKafkaConfig {

    /** O Risco e o produtor, entao e ele quem declara o topico: 3 particoes, como os demais. */
    @Bean
    public NewTopic topicoDeElegibilidade(@Value("${app.kafka.topico.elegibilidade}") String topico) {
        return new NewTopic(topico, 3, (short) 1);
    }

    /** Valor ja serializado pelo adaptador: nenhum cabecalho de tipo Java vaza para o contrato. */
    @Bean
    public KafkaTemplate<String, String> publicadorDeEventos(KafkaProperties propriedades) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                propriedades.buildProducerProperties(null), new StringSerializer(), new StringSerializer()));
    }
}
