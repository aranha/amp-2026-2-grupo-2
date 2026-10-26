package br.pucminas.aed.risco.adapter.in.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** Cabecalho do {@code /v3/api-docs}. O contrato revisado fica em docs/contratos/openapi-risco.yaml. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Serviço de Risco",
        version = "1.0.0",
        description = "Pareceres de elegibilidade. Interno à rede dos serviços: o BFF é quem expõe."))
public class DocumentacaoDaApi {
}
