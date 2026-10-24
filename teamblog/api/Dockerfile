# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom，让依赖层可以被缓存：改代码不会重下依赖
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine AS runtime

# 时区必须显式设置：业务规则按 Asia/Shanghai 的自然日切分（J-14），
# 容器默认是 UTC，会让凌晨 0-8 点的打卡算到前一天
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

RUN apk add --no-cache tzdata curl && \
    addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# 上传目录：由 compose 挂成卷，Nginx 直接读同一个卷对外服务
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080

# 健康检查打进镜像，compose 里的 depends_on/healthcheck 才能用
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
