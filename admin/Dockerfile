# ---- 构建阶段：debian slim + curl 装 mise，工具链版本只认根 mise.toml ----
FROM debian:13-slim AS build

# mise 自身版本在此钉死——自举工具无法由 mise.toml 管，这是唯一允许在 Dockerfile 钉版本的例外
ENV MISE_VERSION=v2026.6.0
ENV MISE_DATA_DIR=/mise MISE_CONFIG_DIR=/mise MISE_CACHE_DIR=/mise/cache \
    MISE_INSTALL_PATH=/usr/local/bin/mise PATH=/mise/shims:$PATH
# 关掉 mise run 跑 task 前的「自动装全部 [tools]」：本镜像只显式装 node/pnpm
ENV MISE_TASK_RUN_AUTO_INSTALL=false
# libatomic1：mise 装的 pnpm 二进制依赖 libatomic.so.1，debian slim 底座不自带
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git ca-certificates libatomic1 \
    && rm -rf /var/lib/apt/lists/* \
    && curl https://mise.run | sh

WORKDIR /app
COPY mise.toml ./
RUN mise trust && mise install node pnpm

# 依赖清单与源码分两次 COPY：改源码不触发依赖重装（层缓存）
COPY admin/package.json admin/pnpm-lock.yaml ./admin/
RUN mise run install-admin --frozen
COPY admin/ ./admin/
RUN mise run build-admin

# ---- 运行阶段：nginx 只装静态产物与站点配置 ----
FROM nginx:1.29-alpine AS runtime
COPY admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/admin/dist /usr/share/nginx/html
EXPOSE 80
