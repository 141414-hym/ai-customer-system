FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY target/aiagent-0.1.0-SNAPSHOT.jar app.jar

RUN useradd --system --uid 10001 aiagent \
    && mkdir -p /app/data /app/logs \
    && chown -R aiagent:aiagent /app

USER aiagent
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
