FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S amp && adduser -S amp -G amp
USER amp

COPY --from=build /build/target/bff-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
