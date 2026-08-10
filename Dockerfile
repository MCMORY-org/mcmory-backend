# CI의 image 잡이 docker build 전에 ./gradlew clean bootJar를 이미 돌린다.
# 그래서 이미지 안에서 다시 빌드하지 않고 산출물만 복사한다.
# jar { enabled = false }라 build/libs에는 bootJar 하나뿐이고 글롭이 정확히 1개에 매치된다.
FROM eclipse-temurin:17-jre

WORKDIR /app

# ponytail: 힙 상한을 지금 박지 않는다. 인스턴스 타입이 정해지면 -XX:MaxRAMPercentage를 붙인다(OPS-W001 T6)
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
