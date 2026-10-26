package br.pucminas.aed.risco;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * A regra de dependencia como teste: toda seta aponta para dentro do hexagono. Quem importar
 * Spring no dominio, ou fizer o caso de uso chamar o adaptador JDBC direto, quebra o build aqui.
 */
class RegraDeDependenciaTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.pucminas.aed.risco");

    @Test
    void oDominioSoConheceOJdk() {
        noClasses().that().resideInAPackage("..risco.domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages("..risco.domain..", "java..")
                .check(CLASSES);
    }

    @Test
    void aAplicacaoSoConheceODominioEAsPropriasPortas() {
        noClasses().that().resideInAPackage("..risco.application..")
                .should().dependOnClassesThat().resideOutsideOfPackages(
                        "..risco.domain..", "..risco.application..", "java..", "org.slf4j..")
                .check(CLASSES);
    }

    @Test
    void asSetasApontamParaDentroEOsAdaptadoresNaoSeConhecem() {
        onionArchitecture()
                .withOptionalLayers(true)
                .domainModels("..risco.domain..")
                .applicationServices("..risco.application..")
                .adapter("kafka de entrada", "..risco.adapter.in.kafka..")
                .adapter("web", "..risco.adapter.in.web..")
                .adapter("kafka de saida", "..risco.adapter.out.kafka..")
                .adapter("jdbc", "..risco.adapter.out.jdbc..")
                .adapter("composicao", "..risco.adapter.config..")
                .check(CLASSES);
    }
}
