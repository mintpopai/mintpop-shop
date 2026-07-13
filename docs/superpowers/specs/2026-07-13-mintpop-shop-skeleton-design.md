# MintPop Shop 骨架设计

> 日期：2026-07-13 · 状态：已确认

## 一、目标与范围

做一版商店骨架：前端展示**分组 → 分组下商品 → 点击购买**，购买动作在后端**落库创建待支付订单**。注册与付款由统一文档另行接入，本期不做。

**明确不做（YAGNI）**：注册/登录、支付、购物车、管理端、库存扣减、微服务拆分。表结构与状态枚举为上述能力预留扩展点。

## 二、技术选型（评估结论）

| 项 | 选型 | 理由 |
|---|---|---|
| 后端 | Spring Boot 4.x 单体（Java 21，Maven；实施时已核实为 4.1.0） | 当前需求用 Spring Cloud Alibaba（Nacos/Gateway 等）属于过度设计；单体按模块分包，将来拆微服务成本低 |
| ORM | MyBatis-Plus | 阿里系事实标准，与 mapper 分层规范契合 |
| 数据库 | MySQL（连接用户现有的独立 MySQL 服务，不随仓库拉起） | 电商标配；DDL 带中文注释；连接凭据走 gitignore 的 `mise.local.toml`，仓库只提交 example 模板 |
| 迁移 | Flyway | DDL 与种子数据版本化，启动即建表带数据 |
| 前端 | Vue 3 + Vite + TypeScript | 不引重型 UI 库，按 MintPop 设计基线手写轻量样式 token |
| 工具链 | mise 统一（java/maven/node/pnpm 钉具体版本，实施时锁定最新稳定版） | 单一来源、可复现 |

## 三、仓库结构（monorepo）

```
mintpop-shop/
├── mise.toml               # 唯一工具链与命令入口（仅根目录一份）
├── mise.local.example.toml # MySQL 连接环境变量模板（复制为 mise.local.toml 填真实凭据，后者 gitignore）
├── .gitignore              # 排除 .idea/ .claude/ mise.local.toml node_modules/ target/ 等
├── apps/
│   ├── web/                # Vue 3 + Vite + TS 前端
│   └── api/                # Spring Boot 单体后端
└── docs/
```

mise tasks（动作-组件命名）：`install`（聚合）、`install-web`、`run-web`、`run-api`、`build-web`、`build-api`、`test-api`。CI/发版 workflow 本期不做（骨架阶段无发布需求），后续按 `monorepo-cicd-naming.md` 补。

数据库连接：连用户现有的独立 MySQL 服务。`application.yml` 用 `${MYSQL_HOST}` 等占位符读环境变量，真实值写在 gitignore 的 `mise.local.toml` `[env]` 里（mise 运行 task 时自动注入）；JDBC URL 带 `createDatabaseIfNotExist=true`，账号有建库权限时自动建 `mintpop_shop` 库。

## 四、数据库设计（snake_case + 全中文 COMMENT）

价格一律以**分**为单位用 `BIGINT` 存储。

- **`product_group`** 商品分组：`id`、`name`（分组名）、`sort_order`（排序号，小的在前）、`created_at`、`updated_at`
- **`product`** 商品：`id`、`group_id`（所属分组）、`name`、`description`、`price_cents`（价格·分）、`image_url`、`on_sale`（是否上架）、`created_at`、`updated_at`
- **`shop_order`** 订单（`order` 是保留字故加前缀）：`id`、`order_no`（对外订单号，唯一）、`product_id`、`quantity`、`amount_cents`（订单金额·分）、`status`（`PENDING_PAYMENT` 等，SCREAMING_SNAKE_CASE）、`user_id`（预留，可空）、`created_at`、`updated_at`

Flyway：`V1__schema.sql`（建表）+ `V2__seed.sql`（3 个分组、每组 3~4 个示例商品）。

## 五、后端接口（统一 ApiResponse）

统一返回 `ApiResponse<T>`：`code`（0=成功，业务失败用 6 位分段码，`11xxxx` 通用起步）、`data`、`msg`。HTTP 一律 200，全局异常处理器（`@RestControllerAdvice`）收口。

| 接口 | 说明 |
|---|---|
| `GET /api/groups` | 分组列表，含各组**上架**商品（骨架阶段数据量小，一次拉全） |
| `POST /api/orders` | body `{productId, quantity}` → 校验商品存在且上架 → 创建 `PENDING_PAYMENT` 订单 → 返回 `{orderNo, amountCents}` |

分包：`controller / service / mapper / entity / request / response / enumeration / exception / config`。层间依赖一律构造器注入（DI），禁止业务代码内自取依赖。

订单号生成：时间戳 + 随机段（骨架阶段够用，无分布式诉求）。

## 六、前端设计（单页）

页面结构（自上而下）：

1. **品牌条**：MintPop 词标（Fredoka 字体）
2. **分组导航**：药丸（pill）标签，点击切换当前分组
3. **商品网格**：卡片（图、名称、描述、价格、Mint 主色购买按钮）
4. **购买反馈**：点击购买 → `POST /api/orders`（数量固定 1）→ 成功 toast 显示订单号，失败 toast 显示 `msg`

设计基线落地：主色 `#17D1A7` / hover `#0FB389`、文本 `#0B0B0C`、背景 `#FFFFFF`/`#F4F8F6`、卡片圆角 8px、按钮 6px、间距 4 的倍数；字体 Inter + Fredoka 走 Fontsource **自托管**，零外链第三方资源（全球可达含中国大陆）。对比度守 WCAG AA，按钮带清晰 hover/focus 态。

前端只看 `code === 0` 判成败。开发期 Vite 代理 `/api` 到后端（默认 `localhost:8080`）。

## 七、错误处理

- 商品不存在 / 已下架 → 业务码 + 中文 msg（如 `210001 商品不存在或已下架`）
- 参数校验（quantity ≥ 1）→ Bean Validation，统一转 ApiResponse
- 前端网络失败 → toast 通用错误提示

## 八、测试

骨架阶段做到：后端 service 层单测（下单成功 / 商品不存在 / 已下架），controller 层用 MockMvc 冒烟 2 个接口；前端跑通 `pnpm vite build` 型检查即可，组件测试后续补。

## 九、验收标准

复制 `mise.local.example.toml` 为 `mise.local.toml` 填好 MySQL 连接信息后，`mise run run-api` + `mise run run-web` 两条命令，浏览器打开页面能看到分组与种子商品，点击购买弹出订单号，`shop_order` 表新增一行 `PENDING_PAYMENT` 记录。
