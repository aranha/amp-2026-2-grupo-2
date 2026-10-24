package br.pucminas.aed.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BffApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void healthRespondeUp() {
        ResponseEntity<String> resposta = http.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"status\":\"UP\"");
    }
}
