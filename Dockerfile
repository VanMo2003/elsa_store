# --- Giai đoạn 1: build bằng Maven + JDK 21 ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Tải dependency trước để Docker cache lại khi chỉ sửa mã nguồn.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Bỏ qua Spotless (định dạng lại toàn bộ mã) và test (test đầy đủ cần MySQL) khi đóng gói.
RUN mvn -B -q package -DskipTests -Dspotless.apply.skip=true -Dspotless.check.skip=true

# --- Giai đoạn 2: chạy bằng JRE 21 ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata && mkdir -p uploads secrets
ENV TZ=Asia/Ho_Chi_Minh

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
