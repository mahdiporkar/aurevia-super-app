FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -pl services/superapp-bff -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system aurevia && useradd --system --gid aurevia --home-dir /app --shell /usr/sbin/nologin aurevia
COPY --chown=aurevia:aurevia --from=build /workspace/services/superapp-bff/target/superapp-bff-*.jar app.jar
USER aurevia
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
