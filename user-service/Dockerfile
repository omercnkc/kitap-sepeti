# syntax=docker/dockerfile:1
# Build context = monorepo kökü:  docker compose build user-service
#                                 (veya: docker build -f user-service/Dockerfile .)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Önce yalnızca build tanımları: pom'lar değişmedikçe bağımlılık katmanı cache'ten gelir.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY user-service/pom.xml user-service/
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl user-service -am dependency:go-offline

COPY user-service/src user-service/src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl user-service -am package -DskipTests

# Katmanlar sık değişenden seyreğe ayrılır; kod değişince yalnızca "application" katmanı yeniden yazılır.
# Boş kalan katmanın (örn. snapshot-dependencies) klasörü oluşmazsa runtime'daki COPY kırılmasın diye mkdir.
RUN cp user-service/target/user-service-*.jar app.jar \
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
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
