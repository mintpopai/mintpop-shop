# MintPop Shop

MintPop 品牌商店：前端分组展示商品，点击购买创建待支付订单。登录与支付均按 MintPop 统一规范接入（订单表已预留扩展）。

## 技术栈

- **backend**：Spring Boot 4 单体（Java 21 / Maven）+ MyBatis-Plus + Spring Security（OIDC 登录）+ Flyway + MySQL
- **frontend**：商城前端，Vue 3 + Vite + TypeScript（字体 Fontsource 自托管）
- **admin**：管理端前端（独立项目，同 frontend 技术栈），部署在独立子域 `admin.mintpop.ai`
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

## 支付（Stripe）

按 MintPop 统一支付接入规范接入：后端 PaymentIntent + webhook，前端拍平「微信支付 / 支付宝 / 银行卡」三个选项。

- **配置**：Stripe 密钥写在 jar 外 `backend/config/application.yml`（见 `application.example.yml` 的 `payment.stripe` 段），不进仓库。
- **Webhook**：Stripe Dashboard 给站点配置端点 `POST /api/v1/payment/webhook/stripe`，只需订阅
  `payment_intent.succeeded` 与 `payment_intent.payment_failed` 两个事件；签名密钥填入 `webhook-secret`。
- **本地联调**：用 Stripe CLI 把事件转发到本地后端：

  ```bash
  stripe listen --forward-to localhost:8080/api/v1/payment/webhook/stripe
  ```

  命令输出的 `whsec_...` 填入本地 `config/application.yml` 的 `webhook-secret`。
  测试卡号 `4242 4242 4242 4242`（任意未来有效期/CVC）；微信/支付宝在测试模式下扫码会进入 Stripe 模拟授权页。

> 若日后在前端启用 CSP 头，需在 `script-src` / `frame-src` 中放行 `https://*.stripe.com`。

## 常用命令

| 命令 | 说明 |
|---|---|
| `mise run run-backend` / `run-frontend` / `run-admin` | 启动后端 / 商城前端 / 管理端（管理端 5174，见「管理端」章节） |
| `mise run test-backend` | 后端单元测试 |
| `mise run build-backend` / `build-frontend` / `build-admin` | 构建后端 jar / 商城前端 / 管理端产物 |
| `mise run image-backend` / `image-frontend` / `image-admin` | 本地构建后端 / 商城前端 / 管理端 Docker 镜像 |
| `mise run release-backend` / `release-frontend` / `release-admin` | 发版（校验→打 tag→推送，tag 触发发布 workflow） |
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
| `GET /api/payment/checkout-info` | 需登录 | 收银台信息——可用支付方式 + Stripe publishable key |
| `POST /api/payment/orders/{orderNo}/intent` | 需登录 + 归属校验 | 懒创建/复用支付意图，返回 `client_secret` |
| `POST /api/payment/orders/verify` | 需登录 + 归属校验 | 主动核实并推进支付状态（轮询用） |
| `POST /api/payment/orders/{orderNo}/cancel` | 需登录 + 归属校验 | 取消订单 |
| `POST /api/v1/payment/webhook/stripe` | 验签，无登录态 | Stripe 事件回调 |
| `/api/admin/**` | 需登录 + 管理员 | 管理端接口（概览/商品/分组/订单/用户），非管理员返回业务码 110003 |

登录采用 MintPop 统一账号中心（Logto，OIDC 授权码 + PKCE）：后端为机密客户端（BFF），登录后自签会话 JWT（只含内部 userid）写 HttpOnly Cookie，账号中心 token 不进浏览器；用户主键为本地 `shop_user.id`，与账号中心 `sub` 通过 `user_identity` 映射表关联。

## 管理端

店主管理后台是独立前端项目（`admin/`），部署在独立子域 `admin.mintpop.ai`（概览 / 商品 / 分组 / 订单 / 用户），界面固定中文、不做双语。权限是**两道防线**：

1. **Cloudflare Zero Trust Access（第一道，生产必配）**：Access → Applications → Self-hosted 建应用，Domain 填 `admin.mintpop.ai`（整域，**不填 path**），Allow 策略限定管理员邮箱（邮箱 OTP 验证）。整域拦截意味着 `/auth/callback`、`/oauth2/*` 也在防线内——`CF_Authorization` 是 hostname 级 Cookie，OIDC 回调是带该 Cookie 的顶级导航，能正常通过。
2. **后端管理员白名单（第二道）**：外置 `application.yml` 配 `app.auth.admin-emails` 邮箱白名单（忽略大小写，见 `application.example.yml`）。`/api/admin/**` 由拦截器逐请求校验，非管理员返回业务码 110003。Cloudflare 被绕过（如源站直连）时该防线仍然成立。

管理端会话与商城**互不共享**：admin 子域自带 nginx 反代 `/api`、`/auth`、`/oauth2` 到同一个 backend，`mp_session` 以 host-only 形式单独下发在 `admin.mintpop.ai` 上。项目对授权请求强制 `prompt=login`，因此在商城登录过之后进管理端仍会重新走一遍账号中心登录——对管理端而言这是优点。

**账号中心（Logto）需登记的 redirect_uri**：
- `https://admin.mintpop.ai/auth/callback`（生产）
- `http://localhost:5174/auth/callback`（本地开发）

本地开发不经 Cloudflare，仅第二道防线生效——给自己的开发账号邮箱配进白名单即可。

> **本地开发注意**：本地商城（5173）与管理端（5174）都是 `localhost`，而 **Cookie 作用域不区分端口**，两者会共用同一份 `mp_session`。生产是不同 hostname，会话隔离才真正成立——不要拿本地行为推断生产行为。

## CI/CD 与发版

- **CI**：push main / PR 触发 `CI Backend` / `CI Frontend` / `CI Admin`（按目录过滤），复用 `Quality *` 质量门禁（后端 `mvn test`，两个前端各自类型检查+构建）。
- **发版**：`mise run release-backend [vX.Y.Z] ["更新说明"]`（frontend / admin 同理）。脚本校验通过后同步版本号文件、打 `<组件>-vX.Y.Z` tag 并推送；tag 触发 `Release *` workflow：质量门禁 → 构建镜像推 `ghcr.io/mintpopai/mintpop-shop/<组件>` → 创建 GitHub Release（tag 注释置顶 + 按类型过滤的变更日志）。缺省版本号取最新稳定 tag 的 patch+1；带说明时打 annotated tag。
- **通知**：`Action Notify` 把各流水线结果推飞书私聊，需在仓库 Secrets 配 `FEISHU_APP_ID` / `FEISHU_APP_SECRET` / `FEISHU_RECEIVE_ID`。

## 部署

部署机安装 Docker 后，取仓库根的 `docker-compose.yml`、`gateway.nginx.conf` 与一份填好的 `application.yml`（参照 `backend/config/application.example.yml`，MySQL 用外部实例）放同一目录：

```bash
docker login ghcr.io          # 私有镜像时需要
BACKEND_TAG=0.1.0 FRONTEND_TAG=0.1.0 ADMIN_TAG=0.1.0 docker compose up -d   # 缺省 latest；端口用 APP_PORT 覆盖（默认 80）
```

DNS 需把 `mintpop.ai` 与 `admin.mintpop.ai` 都指向这台源站并开启 Cloudflare 代理。

**gateway 是唯一入口**：按 Host 分流——`admin.*` 走管理端站点，其余（含未知 Host）走商城站点；frontend、admin、backend 三个容器都不映射宿主端口。两个前端站点各自反代 `/api`、`/auth`、`/oauth2` 到 backend，因此各子域上的 API 调用都是同源的。

部署注意：后端 8080 端口须仅经反向代理可达、不可直接暴露公网（已启用 `forward-headers-strategy: framework`，直连时 `X-Forwarded-*` 可被伪造）；gateway 与两个站点的 nginx 都必须透传原始 `Host` 头——后端靠它展开 OIDC 的 `redirect_uri`，被改写会导致登录失败。另外自签会话为无状态 JWT，登出仅清浏览器 Cookie、无服务端吊销，被窃 token 在有效期（默认 7 天）内仍有效，全员强制下线的手段是更换 `session-secret`。
