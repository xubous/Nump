# ESTÁGIO 1: Build (Onde a mágica do Maven acontece)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
# 1º apenas o pom.xml → dependências numa camada cacheada.
#    Só invalida se o pom.xml mudar; mudanças de código NÃO baixam tudo de novo.
COPY pom.xml .
RUN mvn -B dependency:resolve dependency:resolve-plugins
# 2º o código fonte → compila e gera o .jar
COPY src ./src
RUN mvn -B clean package -DskipTests

# ESTÁGIO 2: Run (Onde sua aplicação realmente ganha vida)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# AGORA SIM: copiamos o arquivo gerado no estágio anterior (build)
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]