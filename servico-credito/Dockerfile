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

RUN addgroup -S amp && adduser -S amp -G amp
USER amp

COPY --from=build /build/target/servico-credito-*.jar app.jar

# Na rede do compose e no Kubernetes os serviços Java escutam em 8080; no host, 8082.
ENV SERVER_PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
