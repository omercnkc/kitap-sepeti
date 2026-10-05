# syntax=docker/dockerfile:1
# Build context = monorepo kökü:  docker compose build api-gateway
#                                 (veya: docker build -f api-gateway/Dockerfile .)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Önce yalnızca build tanımları: pom'lar değişmedikçe bağımlılık katmanı cache'ten gelir.
# Kök POM'daki her <module>'ün pom'u olmalı (yoksa Maven reaktörü okuyamaz). --parents klasörleri korur.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY --parents */pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl api-gateway -am dependency:go-offline

# Yalnızca bu servisin kaynağı kopyalanır.
COPY --parents api-gateway/src ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl api-gateway -am package -DskipTests

# Spring Boot katman ayrıştırma (layered jar)
RUN cp api-gateway/target/api-gateway-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted \
 && mkdir -p extracted/dependencies extracted/spring-boot-loader extracted/snapshot-dependencies extracted/application

FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
ENV SERVER_PORT=8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
