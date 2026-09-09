FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml .
COPY *.java .

RUN mvn -q -DskipTests compile dependency:copy-dependencies -DoutputDirectory=target/dependency

RUN mkdir -p data

CMD ["java","--add-modules","jdk.httpserver","-cp","target/classes:target/dependency/*","BankingSystem"]
