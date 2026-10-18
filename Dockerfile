ARG BASE_IMAGE=registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

FROM ${BASE_IMAGE} AS layers
USER 0
WORKDIR /layers
COPY beadle-backend/build/libs/beadle-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
LABEL org.opencontainers.image.title="BBH Beadle" \
      org.opencontainers.image.source="https://github.com/mateuszmatan/Beadle-GUI" \
      io.k8s.display-name="BBH Beadle" \
      io.k8s.description="Creates and follows ProTech changes with their change tasks and CyberTrack secure coding tickets" \
      io.openshift.expose-services="8081:http"
USER 0
WORKDIR /application
RUN mkdir -p /application/data /application/logs && chown 185:0 /application/data /application/logs && chmod 0775 /application/data /application/logs
COPY --from=layers --chown=185:0 /layers/extracted/dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=185:0 /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    BEADLE_DATA_DIR=/application/data \
    LOG_PATH=/application/logs
VOLUME /application/data
USER 185
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "application.jar"]
