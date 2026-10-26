# Estágio 1 — build: Maven e JDK só existem aqui.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependências numa camada própria: mudar só o código não baixa o Maven Central de novo.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Os testes rodam no job de teste do CI; aqui só empacota.
COPY src ./src
RUN mvn -B clean package -DskipTests

# Estágio 2 — runtime: só a JRE e o jar. Sem Maven, sem JDK, sem código-fonte.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Agente OpenTelemetry (ADR-010): instrumenta Spring MVC, RestClient, JDBC e Kafka sem tocar no
# código e propaga o traceparent nos cabeçalhos do Kafka. Versão e checksum fixos; desligado com
# OTEL_SDK_DISABLED=true, como no Kubernetes.
ADD --chmod=644 --checksum=sha256:4cff4ab46179260a61fc0d884f3f170cfbd9d2962dd260be2cff31262d0c7618 \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar \
    /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar"

RUN addgroup -S amp && adduser -S amp -G amp
USER amp

COPY --from=build /build/target/servico-credito-*.jar app.jar

# Na rede do compose e no Kubernetes os serviços Java escutam em 8080; no host, 8082.
ENV SERVER_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
