# syntax=docker/dockerfile:1
# Build context = monorepo kökü:  docker compose build order-service
#                                 (veya: docker build -f order-service/Dockerfile .)

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Önce yalnızca build tanımları: pom'lar değişmedikçe bağımlılık katmanı cache'ten gelir.
# Kök POM'daki her <module>'ün pom'u olmalı (yoksa Maven reaktörü okuyamaz). --parents klasörleri korur;
# yeni modül eklenince bu satır değişmez.
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY --parents */pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl order-service -am dependency:go-offline

# Yalnızca bu servisin ve derlediği modüllerin (-am) kaynağı; diğer servislerinki kopyalanmaz.
COPY --parents common/src order-service/src ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl order-service -am package -DskipTests

# Katmanlar sık değişenden seyreğe ayrılır; kod değişince yalnızca "application" katmanı yeniden yazılır.
# Boş kalan katmanın (örn. snapshot-dependencies) klasörü oluşmazsa runtime'daki COPY kırılmasın diye mkdir.
RUN cp order-service/target/order-service-*.jar app.jar \
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
EXPOSE 8088
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
