FROM openjdk:11-jre-slim-buster

# copy the packaged jar file into our docker image
#COPY out/artifacts/course236351_project_template_jar/course236351-project-template.jar /transaction_server.jar
COPY server/build/libs/server.jar /transaction_server.jar

EXPOSE 8080

# set the startup command to execute the jar
CMD ["java", "-jar", "/transaction_server.jar"]
