FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/weather-service-*.jar app.jar

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 appuser && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
