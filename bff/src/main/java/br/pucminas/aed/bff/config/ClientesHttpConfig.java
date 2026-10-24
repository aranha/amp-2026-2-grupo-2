package br.pucminas.aed.bff.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ClientesHttpConfig {

    private final DownstreamsProperties propriedades;

    public ClientesHttpConfig(DownstreamsProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Bean
    RestClient creditoRestClient(RestClient.Builder builder) {
        return cliente(builder, propriedades.credito());
    }

    @Bean
    RestClient riscoRestClient(RestClient.Builder builder) {
        return cliente(builder, propriedades.risco());
    }

    @Bean
    RestClient antifraudeRestClient(RestClient.Builder builder) {
        return cliente(builder, propriedades.antifraude());
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    Executor executorDeAgregacao() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private RestClient cliente(RestClient.Builder builder, String baseUrl) {
        ClientHttpRequestFactorySettings ajustes = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(propriedades.conexao())
            .withReadTimeout(propriedades.leitura());
        ClientHttpRequestFactory fabrica = ClientHttpRequestFactories.get(ajustes);
        return builder.clone().baseUrl(baseUrl).requestFactory(fabrica).build();
    }
}
