# ESTÁGIO 1: Build (Onde a mágica do Maven acontece)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# Copia os arquivos de configuração do Maven e o código fonte
COPY . .
# Roda o comando para gerar o arquivo .jar dentro do servidor do Render
RUN mvn clean package -DskipTests

# ESTÁGIO 2: Run (Onde sua aplicação realmente ganha vida)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# AGORA SIM: copiamos o arquivo gerado no estágio anterior (build)
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]