FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY backend/src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S paysi && adduser -S paysi -G paysi
WORKDIR /app
COPY --from=build --chown=paysi:paysi /workspace/target/paysi-backend-*.jar app.jar

USER paysi
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

