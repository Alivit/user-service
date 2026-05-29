FROM gradle:jdk25 AS build
WORKDIR /app

ENV GRADLE_OPTS="-Xmx1536m -XX:MaxMetaspaceSize=384m -Dorg.gradle.jvmargs=-Xmx1536m"

COPY build.gradle settings.gradle ./
RUN --mount=type=cache,target=/root/.gradle/caches \
    gradle dependencies --no-daemon --max-workers=1

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle/caches \
    gradle clean generateProto bootJar --no-daemon -x test --max-workers=1

RUN mv build/libs/$(ls build/libs/ | grep -v plain) /app/app.jar

FROM eclipse-temurin:25-jdk AS optimizer
WORKDIR /app
COPY --from=build /app/app.jar app.jar

RUN jar -xf app.jar && rm app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring --no-create-home spring
USER spring:spring

COPY --from=optimizer /app/BOOT-INF/lib/ ./BOOT-INF/lib/
COPY --from=optimizer /app/META-INF/ ./META-INF/
COPY --from=optimizer /app/BOOT-INF/classes/ ./BOOT-INF/classes/

EXPOSE 8081

ENTRYPOINT [ \
    "java", \
    "-XX:+UseG1GC", \
    "-cp", "BOOT-INF/classes:BOOT-INF/lib/*", \
    "com.minispring.userservice.UserServiceApplication" \
]