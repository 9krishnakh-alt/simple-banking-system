FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY BankingSystem.java .
RUN javac BankingSystem.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/*.class .
CMD ["java", "BankingSystem"]
