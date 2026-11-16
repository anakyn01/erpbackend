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
# 램 256MB 제한 옵션을 걸고 실행
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]