# Multi-stage: o JDK e o Maven ficam no estágio de build e não vão para a imagem final.
# Checklist B57. A imagem publicada carrega só o JRE e o jar.

# ---------------------------------------------------------------- build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml primeiro: a camada de dependências só é invalidada quando o pom muda,
# não a cada alteração de código.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuário sem privilégio: nada aqui precisa de root.
RUN addgroup -S amp && adduser -S amp -G amp
USER amp

COPY --from=build /build/target/bff-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
