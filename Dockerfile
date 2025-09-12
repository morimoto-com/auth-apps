# 実行専用イメージ（JRE）
FROM eclipse-temurin:21-jre-alpine

# JAR の配置（例: backend/authjava/build/libs/app.jar を作る想定）
ARG JAR_FILE=backend/authjava/build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar


EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
