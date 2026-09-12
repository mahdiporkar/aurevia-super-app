FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/test-sso-service-0.1.0.jar app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
