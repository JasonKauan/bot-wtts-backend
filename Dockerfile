# ---- Stage 1: build (Maven + JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache de dependências: copia só o pom primeiro e baixa as deps.
# Assim, mudança em src/ não re-baixa o mundo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Código e empacotamento (testes não rodam no build).
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: runtime (só JRE 21, imagem menor) ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Usuário não-root (segurança).
RUN useradd -r -u 1001 appuser

# spring-boot:repackage gera um único *.jar executável em target/.
COPY --from=build /app/target/*.jar app.jar
USER appuser

# O Render injeta a env PORT (o app já faz bind em ${PORT:8081}).
# MaxRAMPercentage respeita o limite de memória do container.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# exec → java vira PID 1 e recebe SIGTERM (shutdown gracioso no deploy/stop do Render).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
