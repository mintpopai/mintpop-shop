# MintPop Shop

MintPop 品牌商店：前端分组展示商品，点击购买创建待支付订单。注册与支付由统一文档另行接入（订单表已预留 `user_id` 与状态扩展）。

## 技术栈

- **backend**：Spring Boot 4 单体（Java 21 / Maven）+ MyBatis-Plus + Flyway + MySQL
- **frontend**：Vue 3 + Vite + TypeScript（字体 Fontsource 自托管）
- 工具链与命令统一由根 `mise.toml` 管理

## 快速开始

1. 复制 `backend/config/application.example.yml` 为同目录 `application.yml`，填入你的 MySQL 连接信息（该文件已被 gitignore 且在 jar 之外，凭据不入库；账号有建库权限时会自动创建 `mintpop_shop` 库）。
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

## 接口

统一返回 `ApiResponse<T>`（`code` 0=成功 / `data` / `msg`），HTTP 一律 200。

| 接口 | 说明 |
|---|---|
| `GET /api/groups` | 分组列表（含各组上架商品） |
| `POST /api/orders` | 创建待支付订单，body `{productId, quantity}` |
