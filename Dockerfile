FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package
FROM eclipse-temurin:25-jre
RUN groupadd -g 10001 contigo && useradd -u 10001 -g contigo -s /usr/sbin/nologin contigo && mkdir -p /app/evidence && chown -R contigo:contigo /app
WORKDIR /app
COPY --from=build /workspace/target/contigo-1.0.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-Duser.timezone=UTC","-jar","app.jar"]
