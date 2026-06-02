FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY solr-spring-boot-autoconfigure/pom.xml solr-spring-boot-autoconfigure/pom.xml
COPY solr-spring-boot-starter/pom.xml solr-spring-boot-starter/pom.xml
COPY solr-spring-boot-sample/pom.xml solr-spring-boot-sample/pom.xml
COPY solr-spring-boot-data-curator/pom.xml solr-spring-boot-data-curator/pom.xml

RUN ./mvnw dependency:go-offline -N -q
RUN ./mvnw dependency:go-offline -pl solr-spring-boot-autoconfigure,solr-spring-boot-starter,solr-spring-boot-sample -q

COPY solr-spring-boot-autoconfigure/src solr-spring-boot-autoconfigure/src
COPY solr-spring-boot-sample/src solr-spring-boot-sample/src

RUN ./mvnw install -N -DskipTests -q && \
    ./mvnw install -pl solr-spring-boot-autoconfigure,solr-spring-boot-starter -DskipTests -q && \
    ./mvnw package -pl solr-spring-boot-sample -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/solr-spring-boot-sample/target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
