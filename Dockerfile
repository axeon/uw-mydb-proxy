FROM dev.xili.pub:5000/eclipse-temurin:21-jre AS builder
WORKDIR /builder
COPY target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM dev.xili.pub:5000/eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

ENV JAVA_OPTS="" SPRING_OPTS=""

ENTRYPOINT ["exec","java","-jar","$JAVA_OPTS","application.jar","${SPRING_OPTS}"]