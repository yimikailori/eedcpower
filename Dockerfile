FROM openjdk:8-alpine

COPY target/uberjar/eedcpower.jar /eedcpower/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/eedcpower/app.jar"]
