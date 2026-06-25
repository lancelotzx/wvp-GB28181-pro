# Stage 1: build frontend with node
FROM --platform=linux/amd64 node:20-alpine AS frontend-builder
WORKDIR /web
COPY web/package.json web/package-lock.json* ./
RUN npm config set registry https://registry.npmmirror.com && npm install
COPY web/ ./
RUN npm run build:prod
# vue.config.js outputDir is ../src/main/resources/static, so dist lands at /src/main/resources/static
RUN ls -la /src/main/resources/static && ls -la /src/main/resources/static/index.html

# Stage 2: build backend jar with maven
FROM --platform=linux/amd64 maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY .git ./.git
COPY --from=frontend-builder /src/main/resources/static /build/src/main/resources/static
# pom.xml 已配置 aliyun + ECC 双仓库, 不额外覆盖 settings.xml 以免挡掉 ECC 仓库
RUN mvn clean package -DskipTests -B
# finalName pattern: wvp-pro-${version}-${maven.build.timestamp}.jar, rename to fixed name
RUN cp $(ls target/wvp-pro-*.jar | head -1) /build/wvp.jar

# Stage 3: runtime with JRE only
FROM --platform=linux/amd64 eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
WORKDIR /app
COPY --from=backend-builder /build/wvp.jar /app/wvp.jar
ENV JAVA_OPTS="-Xms512m -Xmx2g -Dfile.encoding=UTF-8"
ENV WVP_CONF=/app/conf/application-dev.yml
VOLUME ["/app/conf", "/app/logs"]
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/wvp.jar --spring.config.location=file:$WVP_CONF"]
