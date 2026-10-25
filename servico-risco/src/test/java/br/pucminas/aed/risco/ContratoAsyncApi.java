package br.pucminas.aed.risco;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Le o {@code docs/contratos/asyncapi.yaml} do repositorio. Os testes de contrato comparam o que o
 * servico produz e consome com o que o documento promete: se um dos dois mudar sozinho, o build
 * quebra aqui, e nao em runtime no servico de outra pessoa.
 */
public final class ContratoAsyncApi {

    private static final Path ARQUIVO = Path.of("..", "docs", "contratos", "asyncapi.yaml");

    private final Map<String, Object> raiz;

    private ContratoAsyncApi(Map<String, Object> raiz) {
        this.raiz = raiz;
    }

    public static ContratoAsyncApi carregar() {
        try (InputStream entrada = Files.newInputStream(ARQUIVO)) {
            return new ContratoAsyncApi(new Yaml().load(entrada));
        } catch (IOException falha) {
            throw new UncheckedIOException("contrato nao encontrado em " + ARQUIVO.toAbsolutePath(), falha);
        }
    }

    /** Campos obrigatorios da carga de uma mensagem de {@code components.messages}. */
    public List<String> camposObrigatorios(String mensagem) {
        return lista(carga(mensagem), "required");
    }

    /** O {@code type} JSON Schema de cada campo da carga. */
    @SuppressWarnings("unchecked")
    public String tipo(String mensagem, String campo) {
        var propriedades = (Map<String, Object>) carga(mensagem).get("properties");
        return (String) ((Map<String, Object>) propriedades.get(campo)).get("type");
    }

    @SuppressWarnings("unchecked")
    public String formato(String mensagem, String campo) {
        var propriedades = (Map<String, Object>) carga(mensagem).get("properties");
        return (String) ((Map<String, Object>) propriedades.get(campo)).get("format");
    }

    /** Cabecalhos CloudEvents obrigatorios, comuns a todas as mensagens. */
    @SuppressWarnings("unchecked")
    public List<String> cabecalhosObrigatorios() {
        var esquemas = (Map<String, Object>) componentes().get("schemas");
        return lista((Map<String, Object>) esquemas.get("CloudEventsHeaders"), "required");
    }

    /** O endereco (nome do topico) de um canal. */
    @SuppressWarnings("unchecked")
    public String topico(String canal) {
        var canais = (Map<String, Object>) raiz.get("channels");
        return (String) ((Map<String, Object>) canais.get(canal)).get("address");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> carga(String mensagem) {
        var mensagens = (Map<String, Object>) componentes().get("messages");
        return (Map<String, Object>) ((Map<String, Object>) mensagens.get(mensagem)).get("payload");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> componentes() {
        return (Map<String, Object>) raiz.get("components");
    }

    @SuppressWarnings("unchecked")
    private static List<String> lista(Map<String, Object> mapa, String chave) {
        return (List<String>) mapa.get(chave);
    }
}
