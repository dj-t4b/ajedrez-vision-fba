# Etapa 1: compila el .war
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: WildFly 41 con el .war desplegado
FROM quay.io/wildfly/wildfly:41.0.0.Final-jdk21
COPY --from=build /src/target/ajedrez.war /opt/jboss/wildfly/standalone/deployments/ajedrez.war
EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
