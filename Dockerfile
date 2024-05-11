FROM eclipse-temurin:21-jre as builder
WORKDIR application
COPY target/*.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM eclipse-temurin:21-jre
WORKDIR application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./

ENV JAVA_OPTS="" SPRING_OPTS=""

ENTRYPOINT exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher ${SPRING_OPTS}
