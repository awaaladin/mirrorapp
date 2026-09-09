# Multi-stage build: compile with a full JDK, run on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/mirror-backend-0.1.0.jar app.jar
# Render sets $PORT itself; this default is only used for local `docker run`.
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
