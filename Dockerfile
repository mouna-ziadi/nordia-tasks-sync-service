# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 nordia \
 && mkdir -p /app/data \
 && chown -R nordia:nordia /app
COPY --from=build --chown=nordia:nordia /build/target/*.jar /app/app.jar
USER nordia
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]