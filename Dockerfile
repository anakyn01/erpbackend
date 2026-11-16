# 1단계: 빌드 환경 (Gradle을 이용해 프로젝트 빌드)
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test

# 2단계: 실행 환경 (가벼운 JRE 환경에서 서버 구동)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 빌드된 jar 파일을 복사
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080

# [수정] 0.0.0.0 바인딩과 렌더 포트 환경변수(${PORT})를 반영하도록 수정
ENTRYPOINT ["java", "-Xmx400m", "-Xms256m", "-Dserver.address=0.0.0.0", "-Dserver.port=${PORT:-8080}", "-jar", "app.jar"]