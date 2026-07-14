# MintPop Shop

MintPop 品牌商店：前端分组展示商品，点击购买创建待支付订单。注册与支付由统一文档另行接入（订单表已预留 `user_id` 与状态扩展）。

## 技术栈

- **backend**：Spring Boot 4 单体（Java 21 / Maven）+ MyBatis-Plus + Spring Security（OIDC 登录）+ Flyway + MySQL
- **frontend**：Vue 3 + Vite + TypeScript（字体 Fontsource 自托管）
- 工具链与命令统一由根 `mise.toml` 管理

## 快速开始

1. 复制 `backend/config/application.example.yml` 为同目录 `application.yml`，填入你的 MySQL 连接信息（该文件已被 gitignore 且在 jar 之外，凭据不入库；账号有建库权限时会自动创建 `mintpop_shop` 库）；同时按模板填入统一账号中心（Logto）的 OIDC 参数与会话密钥（向账号中心管理员申请；本地回调地址登记为 `http://localhost:5173/auth/callback`）。
2. 依次执行：

```bash
mise install          # 安装工具链（java/maven/node/pnpm）
mise run install      # 安装项目依赖
mise run run-backend      # 终端 1：启动后端（8080，Flyway 自动建表并写入种子数据）
mise run run-frontend      # 终端 2：启动前端（5173，/api 代理到 8080）
```

打开 http://localhost:5173 即可看到商店页面。

## 常用命令

| 命令 | 说明 |
|---|---|
| `mise run run-backend` / `run-frontend` | 启动后端 / 前端 |
| `mise run test-backend` | 后端单元测试 |
| `mise run build-backend` / `build-frontend` | 构建后端 jar / 前端产物 |
| `mise run image-backend` / `image-frontend` | 本地构建后端 / 前端 Docker 镜像 |
| `mise run release-backend` / `release-frontend` | 发版（校验→打 tag→推送，tag 触发发布 workflow） |
| `mise run up` / `down` | 以 docker-compose 启停已发布镜像 |

## 接口

统一返回 `ApiResponse<T>`（`code` 0=成功 / `data` / `msg`），HTTP 一律 200；未登录访问需登录接口时由鉴权层返回 HTTP 401。

| 接口 | 鉴权 | 说明 |
|---|---|---|
| `GET /api/groups` | 公开 | 分组列表（含各组上架商品） |
| `GET /api/me` | 需登录 | 当前用户档案 |
| `POST /api/orders` | 需登录 | 创建待支付订单，body `{productId, quantity}` |
| `GET /api/orders` | 需登录 | 我的订单列表（倒序） |
| `GET /auth/login` | — | 跳转统一账号中心登录（OIDC） |
| `GET /auth/logout` | — | 登出（清会话 + 账号中心单点登出） |

登录采用 MintPop 统一账号中心（Logto，OIDC 授权码 + PKCE）：后端为机密客户端（BFF），登录后自签会话 JWT（只含内部 userid）写 HttpOnly Cookie，账号中心 token 不进浏览器；用户主键为本地 `shop_user.id`，与账号中心 `sub` 通过 `user_identity` 映射表关联。

## CI/CD 与发版

- **CI**：push main / PR 触发 `CI Backend` / `CI Frontend`（按目录过滤），复用 `Quality *` 质量门禁（后端 `mvn test`，前端类型检查+构建）。
- **发版**：`mise run release-backend [vX.Y.Z] ["更新说明"]`（frontend 同理）。脚本校验通过后同步版本号文件、打 `<组件>-vX.Y.Z` tag 并推送；tag 触发 `Release *` workflow：质量门禁 → 构建镜像推 `ghcr.io/mintpopai/mintpop-shop/<组件>` → 创建 GitHub Release（tag 注释置顶 + 按类型过滤的变更日志）。缺省版本号取最新稳定 tag 的 patch+1；带说明时打 annotated tag。
- **通知**：`Action Notify` 把各流水线结果推飞书私聊，需在仓库 Secrets 配 `FEISHU_APP_ID` / `FEISHU_APP_SECRET` / `FEISHU_RECEIVE_ID`。

## 部署

部署机安装 Docker 后，取仓库根的 `docker-compose.yml` 与一份填好的 `application.yml`（参照 `backend/config/application.example.yml`，MySQL 用外部实例）放同一目录：

```bash
docker login ghcr.io          # 私有镜像时需要
BACKEND_TAG=0.1.0 FRONTEND_TAG=0.1.0 docker compose up -d   # 缺省 latest；端口用 APP_PORT 覆盖（默认 80）
```

前端 nginx 是唯一入口：静态资源 + 反代 `/api`、`/auth` 到 backend（backend 不映射宿主端口，满足「8080 仅经反代可达」）；SPA 深链已 fallback 到 `index.html`。

部署注意：后端 8080 端口须仅经反向代理可达、不可直接暴露公网（已启用 `forward-headers-strategy: framework`，直连时 `X-Forwarded-*` 可被伪造）；生产反代需将前端深链（如 `/orders`）fallback 到 `index.html`。另外自签会话为无状态 JWT，登出仅清浏览器 Cookie、无服务端吊销，被窃 token 在有效期（默认 7 天）内仍有效，全员强制下线的手段是更换 `session-secret`。
