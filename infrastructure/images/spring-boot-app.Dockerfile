# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-noble@sha256:3eb81ed94d8c1a34422f19f8188548bdf02cae69c91d0328afdbb7abed90f617 AS build

ARG APP_NAME

WORKDIR /workspace

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY build-logic ./build-logic
COPY apps ./apps
COPY components ./components
COPY core ./core
COPY integrations ./integrations

RUN chmod 0755 gradlew \
    && test -n "${APP_NAME}" \
    && test -f "apps/${APP_NAME}/build.gradle.kts"

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --stacktrace ":apps:${APP_NAME}:bootJar" \
    && jar_file="$(find "apps/${APP_NAME}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "${jar_file}" \
    && cp "${jar_file}" /workspace/application.jar \
    && java -Djarmode=tools -jar /workspace/application.jar extract --layers --destination /workspace/extracted

FROM bellsoft/liberica-runtime-container:jre-25-glibc@sha256:604f4046888dad8a8454532b366e945c2775510763f0494eda856c030c335b19

ARG APP_NAME
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="OrgMemory ${APP_NAME}" \
      org.opencontainers.image.source="https://github.com/kl3inIT/OrgMemory" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

RUN addgroup -S -g 1654 orgmemory \
    && adduser -S -D -H -u 1654 -G orgmemory orgmemory

WORKDIR /application

COPY --from=build --chown=1654:1654 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=1654:1654 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=1654:1654 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=1654:1654 /workspace/extracted/application/ ./

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

USER 1654:1654

ENTRYPOINT ["java", "-jar", "application.jar"]
