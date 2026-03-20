FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY smart-pantry-common/pom.xml smart-pantry-common/pom.xml
COPY migros-service/pom.xml migros-service/pom.xml
COPY yemeksepeti-service/pom.xml yemeksepeti-service/pom.xml
COPY discovery-server/pom.xml discovery-server/pom.xml

RUN chmod +x mvnw && ./mvnw -q -DskipTests -pl migros-service,yemeksepeti-service -am dependency:go-offline

COPY smart-pantry-common/src smart-pantry-common/src
COPY migros-service/src migros-service/src
COPY yemeksepeti-service/src yemeksepeti-service/src

RUN ./mvnw -q -DskipTests -pl migros-service,yemeksepeti-service -am package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache chromium chromium-chromedriver

ENV CHROME_BIN=/usr/bin/chromium
ENV APP_MODULE=migros-service

COPY --from=build /app/migros-service/target/smart-pantry-migros-service.jar /app/migros-service.jar
COPY --from=build /app/yemeksepeti-service/target/smart-pantry-yemeksepeti-service.jar /app/yemeksepeti-service.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "if [ \"$APP_MODULE\" = \"yemeksepeti-service\" ]; then exec java -jar /app/yemeksepeti-service.jar; else exec java -jar /app/migros-service.jar; fi"]
