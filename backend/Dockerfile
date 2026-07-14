# ---- 构建阶段：debian slim + curl 装 mise，工具链版本只认根 mise.toml ----
FROM debian:13-slim AS build

# mise 自身版本在此钉死——自举工具无法由 mise.toml 管，这是唯一允许在 Dockerfile 钉版本的例外
ENV MISE_VERSION=v2026.6.0
ENV MISE_DATA_DIR=/mise MISE_CONFIG_DIR=/mise MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise PATH=/mise/shims:$PATH
# 关掉 mise run 跑 task 前的「自动装全部 [tools]」：本镜像只显式装 java/maven
ENV MISE_TASK_RUN_AUTO_INSTALL=false
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://mise.run | sh

WORKDIR /app
COPY mise.toml ./
RUN mise trust && mise install java maven

# 依赖清单与源码分两次 COPY：改源码不触发依赖重下（层缓存）
COPY backend/pom.xml ./backend/
RUN mise run install-backend
COPY backend/src ./backend/src
RUN mise run build-backend

# ---- 运行阶段：JRE 底座只装 jar；curl 供 compose healthcheck ----
FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# 部署时把外置 application.yml 挂到 /app/config/，Spring 启动自动读取（凭据不进镜像）
COPY --from=build /app/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
