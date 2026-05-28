FROM gradle:jdk25-alpine AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

COPY src ./src
RUN gradle bootJar --no-daemon -x test

RUN mv build/libs/$(ls build/libs/ | grep -v plain) /app/app.jar

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/app.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-jar", "app.jar"]