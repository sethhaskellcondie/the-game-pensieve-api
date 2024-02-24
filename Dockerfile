# from declares the base image that we will build off of (FROM is always the first line of a Dockerfile)
# eclipse-temurin provides a java runtime container, this project uses java 17
FROM eclipse-temurin:17

# these two lines will copy a built jar file to to the container the JAR_FILE is passed in during the docker build command
# example: docker build --build-arg JAR_FILE=target/*.jar -t myorg/myapp .
ARG JAR_FILE
# copy everything in the : path from host machine <space> to the path on the container
COPY ${JAR_FILE} app.jar

#COPY target/*.jar app.jar

# this will run the jar file in the container
ENTRYPOINT ["java","-jar","/app.jar"]

# run in the terminal (in this directory) with the command
# docker build -t persive-api .
# docker : build : -t (create the image with this tag name) : <tag-name> : . (directory of Dockerfile, here)
# -----or run this with a compose.yaml file-----
