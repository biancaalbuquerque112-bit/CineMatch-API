FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Arquivos do Maven Wrapper
COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw

# Codigo-fonte
COPY src ./src

# Compila a API
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

# Copia o programa compilado
COPY --from=build /app/target/*.jar app.jar

# Porta padrao para testes locais
EXPOSE 8081

# Inicia o Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
