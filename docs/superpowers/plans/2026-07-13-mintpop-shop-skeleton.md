# MintPop Shop 骨架实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打出 MintPop 商店骨架——前端分组展示商品、点击购买后端落库建待支付订单。

**Architecture:** monorepo（`apps/web` Vue 3 前端 + `apps/api` Spring Boot 4 单体后端），连接用户现有的独立 MySQL 服务（凭据走 gitignore 的 `mise.local.toml` 环境变量，仓库只提交 example 模板），Flyway 管 DDL 与种子数据，统一 `ApiResponse<T>` 返回。前端单页：分组 pill 导航 + 商品卡片网格 + 购买 toast。

**Tech Stack:** Spring Boot 4.1.0（Java 21 / Maven）、MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）、Flyway、MySQL（用户现有服务）、Vue 3.5 + Vite 8 + TypeScript 5.9、Fontsource 自托管字体、mise 统一工具链。

## Global Constraints

- 回复/文档/注释/提交信息一律**简体中文**；代码、命令、标识符保持英文。
- 工具链版本钉死在根 `mise.toml`：`java = "temurin-21.0.11+10.0.LTS"`、`maven = "3.9.16"`、`node = "24.18.0"`、`pnpm = "11.12.0"`；子目录禁止再放 `mise.toml`。
- mise task 命名「动作-组件」（`run-api`、`build-web`）；task 直接调底层命令；`package.json` 不留 scripts。
- Spring Boot 4 注意：web starter 是 **`spring-boot-starter-webmvc`**（不是 3.x 的 `spring-boot-starter-web`）。
- 统一返回 `ApiResponse<T>`（`code`/`data`/`msg`，0=成功），HTTP 一律 200；业务码 6 位分段：`11xxxx` 通用、`21xxxx` 商品模块。
- 枚举成员名与字符串取值 SCREAMING_SNAKE_CASE 且逐字一致（如 `PENDING_PAYMENT`）。
- 数据库表/列 snake_case + 全中文 COMMENT；金额以**分**（BIGINT）存储。
- 层间依赖构造器注入（Lombok `@RequiredArgsConstructor` + final 字段），禁止业务代码自取依赖。
- 前端零外链第三方资源（字体走 `@fontsource/*` 自托管）；设计 token：主色 `#17D1A7`、hover `#0FB389`、文本 `#0B0B0C`、次要 `#6B7280`、背景 `#FFFFFF`/`#F4F8F6`、分隔线 `#E5E7EB`、卡片圆角 8px、按钮 6px、药丸 999px、间距 4 的倍数。
- 数据库连用户现有的独立 MySQL 服务：真实凭据只写在 gitignore 的 `mise.local.toml` `[env]`（mise 跑 task 时自动注入环境变量），仓库只提交 `mise.local.example.toml` 模板；`application.yml` 用 `${MYSQL_HOST}` 等占位符。**执行涉及真实数据库的验证步骤前，若 `mise.local.toml` 不存在，先停下向用户要连接信息。**
- 测试不起 Spring 上下文：service 层用 Mockito 纯单测，controller 层用 `MockMvcBuilders.standaloneSetup`（规避 Boot 4 测试切片注解包路径变动）。
- 提交信息中文，结尾带 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。

---

### Task 1: 仓库基建（.gitignore / mise.toml / MySQL 连接模板）

**Files:**
- Create: `.gitignore`
- Create: `mise.toml`
- Create: `mise.local.example.toml`

**Interfaces:**
- Consumes: 无
- Produces: `mise run install|install-web|run-api|run-web|build-api|build-web|test-api` 任务；环境变量约定 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD`（由 gitignore 的 `mise.local.toml` 提供，Task 2 的 `application.yml` 消费）

- [ ] **Step 1: 写 `.gitignore`**

```gitignore
# IDE 与本地工具
.idea/
.claude/
.DS_Store

# 构建产物与依赖
node_modules/
dist/
target/

# 本地环境文件（含数据库凭据的 mise.local.toml 绝不入库）
mise.local.toml
*.local
```

- [ ] **Step 2: 写根 `mise.toml`**

```toml
[tools]
java = "temurin-21.0.11+10.0.LTS"
maven = "3.9.16"
node = "24.18.0"
pnpm = "11.12.0"

# —— 安装 ——
[tasks.install]
description = "安装全部组件依赖（api 由 Maven 构建时自动解析，无需单独安装）"
depends = ["install-web"]

[tasks.install-web]
description = "安装 web 前端依赖"
usage = '''
flag "--frozen" help="按 lockfile 精确安装、不更新 lockfile（CI/Docker 用）"
'''
dir = "apps/web"
run = "pnpm install ${usage_frozen:+--frozen-lockfile}"

# —— 运行 ——
[tasks.run-api]
description = "启动 api 后端（Spring Boot，端口 8080）"
dir = "apps/api"
run = "mvn spring-boot:run"

[tasks.run-web]
description = "启动 web 前端开发服务器（Vite，/api 代理到 8080）"
dir = "apps/web"
run = "pnpm vite"

# —— 构建 ——
[tasks.build-api]
description = "构建 api 后端 jar"
dir = "apps/api"
run = "mvn -DskipTests package"

[tasks.build-web]
description = "类型检查并构建 web 前端产物"
dir = "apps/web"
run = "pnpm vue-tsc --noEmit && pnpm vite build"

# —— 质量 ——
[tasks.test-api]
description = "运行 api 后端单元测试"
dir = "apps/api"
run = "mvn test"
```

- [ ] **Step 3: 写 `mise.local.example.toml`（连接模板，复制为 `mise.local.toml` 填真实值）**

```toml
# 复制本文件为 mise.local.toml 并填入你的 MySQL 连接信息。
# mise.local.toml 已被 .gitignore 排除，真实凭据不会入库；
# mise 运行任何 task（如 run-api）时会自动把 [env] 注入环境变量。
[env]
MYSQL_HOST = "127.0.0.1"
MYSQL_PORT = "3306"
MYSQL_DATABASE = "mintpop_shop"
MYSQL_USER = "填用户名"
MYSQL_PASSWORD = "填密码"
```

- [ ] **Step 4: 验证工具链**

```bash
mise install
mise ls
```

预期：`mise install` 装齐 java/maven/node/pnpm 四个工具，`mise ls` 显示钉住的版本。

- [ ] **Step 5: 提交**

```bash
git add .gitignore mise.toml mise.local.example.toml
git commit -m "chore: 仓库基建——mise 工具链、MySQL 连接模板、gitignore

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: 后端应用骨架（pom / 启动类 / 配置 / Flyway 迁移）

**Files:**
- Create: `apps/api/pom.xml`
- Create: `apps/api/src/main/java/com/mintpop/shop/ShopApplication.java`
- Create: `apps/api/src/main/resources/application.yml`
- Create: `apps/api/src/main/resources/db/migration/V1__schema.sql`
- Create: `apps/api/src/main/resources/db/migration/V2__seed.sql`

**Interfaces:**
- Consumes: Task 1 约定的环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD`（来自用户填写的 `mise.local.toml`）
- Produces: 可启动的 Spring Boot 应用（端口 8080）；三张表 `product_group` / `product` / `shop_order` 及种子数据；Java 包根 `com.mintpop.shop`

- [ ] **Step 1: 写 `apps/api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.mintpop</groupId>
    <artifactId>mintpop-shop-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>mintpop-shop-api</name>
    <description>MintPop 商店后端（单体）</description>

    <properties>
        <java.version>21</java.version>
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写启动类 `ShopApplication.java`**

```java
package com.mintpop.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MintPop 商店后端启动类。
 */
@SpringBootApplication
@MapperScan("com.mintpop.shop.mapper")
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

- [ ] **Step 3: 写 `application.yml`**

```yaml
spring:
  application:
    name: mintpop-shop-api
  datasource:
    # 连接信息全部来自环境变量（由 gitignore 的 mise.local.toml 注入），真实凭据不入库；
    # createDatabaseIfNotExist：账号有建库权限时自动创建目标库
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:mintpop_shop}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}

server:
  port: 8080
```

（Flyway 在 classpath 上默认启用，迁移目录用默认 `classpath:db/migration`。）

- [ ] **Step 4: 写 `V1__schema.sql`**

```sql
-- 商品分组
CREATE TABLE product_group (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64)     NOT NULL COMMENT '分组名',
    sort_order INT             NOT NULL DEFAULT 0 COMMENT '排序号，小的在前',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商品分组';

-- 商品
CREATE TABLE product (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    group_id    BIGINT UNSIGNED NOT NULL COMMENT '所属分组ID（product_group.id）',
    name        VARCHAR(128)    NOT NULL COMMENT '商品名',
    description VARCHAR(512)    NULL COMMENT '商品描述',
    price_cents BIGINT          NOT NULL COMMENT '价格，单位分',
    image_url   VARCHAR(512)    NULL COMMENT '商品图URL，可空（空时前端渲染占位图）',
    on_sale     TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否上架：1=上架 0=下架',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_group_id (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='商品';

-- 订单（order 是保留字，表名加 shop_ 前缀）
CREATE TABLE shop_order (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)     NOT NULL COMMENT '对外订单号，唯一',
    product_id   BIGINT UNSIGNED NOT NULL COMMENT '商品ID（product.id）',
    quantity     INT             NOT NULL COMMENT '购买数量',
    amount_cents BIGINT          NOT NULL COMMENT '订单金额，单位分',
    status       VARCHAR(32)     NOT NULL COMMENT '订单状态：PENDING_PAYMENT=待支付（后续扩展 PAID/CANCELLED 等）',
    user_id      BIGINT UNSIGNED NULL COMMENT '下单用户ID，预留（接入注册后填写）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='订单';
```

- [ ] **Step 5: 写 `V2__seed.sql`**

```sql
-- 种子数据：3 个分组，每组 3~4 个示例商品（image_url 留空，前端渲染占位图）
INSERT INTO product_group (id, name, sort_order) VALUES
    (1, '盲盒系列', 1),
    (2, '手办摆件', 2),
    (3, '周边小物', 3);

INSERT INTO product (group_id, name, description, price_cents, on_sale) VALUES
    (1, '薄荷精灵盲盒', '经典款盲盒，内含 12 款随机造型', 5900, 1),
    (1, '云朵萌宠盲盒', '软萌云朵系列，隐藏款概率 1/72', 6900, 1),
    (1, '星海航员盲盒', '太空主题限定系列', 7900, 1),
    (1, '复古街机盲盒', '像素风怀旧系列（已下架示例）', 5900, 0),
    (2, '薄荷猫手办', '18cm 高经典薄荷猫，含展示底座', 19900, 1),
    (2, '气泡熊摆件', '半透明气泡质感，桌面点缀首选', 12900, 1),
    (2, '月光兔手办', '夜光材质，关灯后微微发光', 15900, 1),
    (3, '薄荷帆布袋', '加厚帆布，MintPop 字标印花', 3900, 1),
    (3, '亚克力钥匙扣', '随机角色，双面印刷', 1500, 1),
    (3, '贴纸套装', '30 张防水贴纸，装点电脑与水杯', 1900, 1);
```

- [ ] **Step 6: 启动验证（建表 + 种子数据落库）**

前置：`mise.local.toml` 已由用户填好真实连接信息（不存在则先停下向用户索要）。

```bash
mise run run-api
```

预期：启动日志出现 Flyway `Migrating schema ... to version "1 - schema"`、`"2 - seed"`、`Successfully applied 2 migrations`，随后 `Started ShopApplication`。验证后停掉应用。（若本机装有 mysql 客户端，可选加验：`SHOW TABLES` 应有三张表 + `flyway_schema_history`，`product` 表 10 行。）

- [ ] **Step 7: 提交**

```bash
git add apps/api
git commit -m "feat: api 应用骨架——Spring Boot 4 + MyBatis-Plus + Flyway 建表与种子数据

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: 统一返回与全局异常（ApiResponse / BizCodeEnum / BizException / 全局处理器）

**Files:**
- Create: `apps/api/src/main/java/com/mintpop/shop/response/ApiResponse.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/enumeration/BizCodeEnum.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/exception/BizException.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/exception/GlobalExceptionHandler.java`
- Test: `apps/api/src/test/java/com/mintpop/shop/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `ApiResponse.success(T)` / `ApiResponse.fail(BizCodeEnum)` / `new ApiResponse<>(code, data, msg)`；`BizCodeEnum.SYSTEM_ERROR(110001)` / `PARAM_INVALID(110002)` / `PRODUCT_NOT_ON_SALE(210001, "商品不存在或已下架")`；`new BizException(BizCodeEnum)` + `getBizCode()`；`GlobalExceptionHandler`（供 controller 测试 `setControllerAdvice` 使用）

- [ ] **Step 1: 写失败测试 `GlobalExceptionHandlerTest.java`**

```java
package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常转为对应业务码")
    void bizExceptionMappedToBizCode() {
        ApiResponse<Void> resp = handler.handleBizException(
                new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));
        assertThat(resp.getCode()).isEqualTo(210001);
        assertThat(resp.getMsg()).isEqualTo("商品不存在或已下架");
        assertThat(resp.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("未预期异常转为系统错误码")
    void unexpectedExceptionMappedToSystemError() {
        ApiResponse<Void> resp = handler.handleUnexpected(new IllegalStateException("boom"));
        assertThat(resp.getCode()).isEqualTo(110001);
    }

    @Test
    @DisplayName("成功工厂方法 code 为 0")
    void successFactoryReturnsZeroCode() {
        ApiResponse<String> resp = ApiResponse.success("data");
        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isEqualTo("data");
        assertThat(resp.isSuccess()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败**

Run: `cd apps/api && mvn test`
Expected: 编译错误（`ApiResponse`、`BizCodeEnum` 等不存在）。

- [ ] **Step 3: 实现四个类**

`response/ApiResponse.java`：

```java
package com.mintpop.shop.response;

import com.mintpop.shop.enumeration.BizCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回结构：code=0 表示成功，非 0 为业务失败；HTTP 状态码一律 200。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    /** 业务状态码：0=成功，非0=失败（6 位分段业务码） */
    private Integer code;
    /** 业务数据 */
    private T data;
    /** 描述/错误信息 */
    private String msg;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, null, null);
    }

    public static <T> ApiResponse<T> fail(BizCodeEnum bizCode) {
        return new ApiResponse<>(bizCode.getCode(), null, bizCode.getMessage());
    }

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
```

`enumeration/BizCodeEnum.java`：

```java
package com.mintpop.shop.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码：6 位分段编码，前两位为模块号（11=通用，21=商品），后四位为段内序号。
 */
@Getter
@AllArgsConstructor
public enum BizCodeEnum {

    SYSTEM_ERROR(110001, "系统繁忙，请稍后重试"),
    PARAM_INVALID(110002, "参数校验失败"),

    PRODUCT_NOT_ON_SALE(210001, "商品不存在或已下架");

    private final int code;
    private final String message;
}
```

`exception/BizException.java`：

```java
package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import lombok.Getter;

/**
 * 业务异常：携带业务码，由全局异常处理器统一转为 ApiResponse。
 */
@Getter
public class BizException extends RuntimeException {

    private final BizCodeEnum bizCode;

    public BizException(BizCodeEnum bizCode) {
        super(bizCode.getMessage());
        this.bizCode = bizCode;
    }
}
```

`exception/GlobalExceptionHandler.java`：

```java
package com.mintpop.shop.exception;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把业务异常与未预期异常统一收口成 ApiResponse（HTTP 200）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.fail(e.getBizCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("；"));
        return new ApiResponse<>(BizCodeEnum.PARAM_INVALID.getCode(), null,
                BizCodeEnum.PARAM_INVALID.getMessage() + "：" + detail);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnexpected(Exception e) {
        log.error("未预期异常", e);
        return ApiResponse.fail(BizCodeEnum.SYSTEM_ERROR);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd apps/api && mvn test`
Expected: `GlobalExceptionHandlerTest` 3 个用例全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add apps/api/src
git commit -m "feat: 统一 ApiResponse 返回、业务码枚举与全局异常处理

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: 分组商品查询接口 GET /api/groups

**Files:**
- Create: `apps/api/src/main/java/com/mintpop/shop/entity/ProductGroup.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/entity/Product.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/mapper/ProductGroupMapper.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/mapper/ProductMapper.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/response/ProductResponse.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/response/GroupWithProductsResponse.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/service/GroupService.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/controller/GroupController.java`
- Test: `apps/api/src/test/java/com/mintpop/shop/service/GroupServiceTest.java`
- Test: `apps/api/src/test/java/com/mintpop/shop/controller/GroupControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ApiResponse`
- Produces: 实体 `Product`（`getId()/getGroupId()/getName()/getDescription()/getPriceCents():Long/getImageUrl()/getOnSale():Boolean`，Task 5 复用）、`ProductMapper extends BaseMapper<Product>`（Task 5 复用）；`GroupService.listGroupsWithProducts(): List<GroupWithProductsResponse>`；HTTP `GET /api/groups` → `ApiResponse<List<GroupWithProductsResponse>>`（字段 `id/name/products[]`，product 字段 `id/name/description/priceCents/imageUrl`）

- [ ] **Step 1: 写失败测试 `GroupServiceTest.java`**

```java
package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.response.GroupWithProductsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private ProductGroupMapper productGroupMapper;
    @Mock
    private ProductMapper productMapper;
    @InjectMocks
    private GroupService groupService;

    private ProductGroup group(long id, String name) {
        ProductGroup g = new ProductGroup();
        g.setId(id);
        g.setName(name);
        return g;
    }

    private Product product(long id, long groupId, String name) {
        Product p = new Product();
        p.setId(id);
        p.setGroupId(groupId);
        p.setName(name);
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    @Test
    @DisplayName("商品按分组归组，空分组返回空商品列表")
    void productsGroupedByGroupId() {
        when(productGroupMapper.selectList(any())).thenReturn(
                List.of(group(1L, "盲盒系列"), group(2L, "手办摆件")));
        when(productMapper.selectList(any())).thenReturn(
                List.of(product(11L, 1L, "薄荷精灵盲盒"), product(12L, 1L, "云朵萌宠盲盒")));

        List<GroupWithProductsResponse> result = groupService.listGroupsWithProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProducts()).hasSize(2);
        assertThat(result.get(0).getProducts().get(0).getName()).isEqualTo("薄荷精灵盲盒");
        assertThat(result.get(1).getProducts()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败**

Run: `cd apps/api && mvn test`
Expected: 编译错误（实体、mapper、service 不存在）。

- [ ] **Step 3: 实现实体、mapper、响应体、service、controller**

`entity/ProductGroup.java`：

```java
package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分组实体（表 product_group）。
 */
@Data
@TableName("product_group")
public class ProductGroup {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分组名 */
    private String name;
    /** 排序号，小的在前 */
    private Integer sortOrder;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
```

`entity/Product.java`：

```java
package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品实体（表 product）。
 */
@Data
@TableName("product")
public class Product {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属分组ID */
    private Long groupId;
    /** 商品名 */
    private String name;
    /** 商品描述 */
    private String description;
    /** 价格，单位分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;
    /** 是否上架 */
    private Boolean onSale;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
```

`mapper/ProductGroupMapper.java`：

```java
package com.mintpop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mintpop.shop.entity.ProductGroup;

/**
 * 商品分组 Mapper。
 */
public interface ProductGroupMapper extends BaseMapper<ProductGroup> {
}
```

`mapper/ProductMapper.java`：

```java
package com.mintpop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mintpop.shop.entity.Product;

/**
 * 商品 Mapper。
 */
public interface ProductMapper extends BaseMapper<Product> {
}
```

`response/ProductResponse.java`：

```java
package com.mintpop.shop.response;

import com.mintpop.shop.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 商品响应体。
 */
@Data
@AllArgsConstructor
public class ProductResponse {

    /** 商品ID */
    private Long id;
    /** 商品名 */
    private String name;
    /** 商品描述 */
    private String description;
    /** 价格，单位分 */
    private Long priceCents;
    /** 商品图URL，可空 */
    private String imageUrl;

    public static ProductResponse of(Product product) {
        return new ProductResponse(product.getId(), product.getName(),
                product.getDescription(), product.getPriceCents(), product.getImageUrl());
    }
}
```

`response/GroupWithProductsResponse.java`：

```java
package com.mintpop.shop.response;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分组（含分组下上架商品）响应体。
 */
@Data
@AllArgsConstructor
public class GroupWithProductsResponse {

    /** 分组ID */
    private Long id;
    /** 分组名 */
    private String name;
    /** 分组下上架商品列表 */
    private List<ProductResponse> products;

    public static GroupWithProductsResponse of(ProductGroup group, List<Product> products) {
        return new GroupWithProductsResponse(group.getId(), group.getName(),
                products.stream().map(ProductResponse::of).toList());
    }
}
```

`service/GroupService.java`：

```java
package com.mintpop.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ProductGroup;
import com.mintpop.shop.mapper.ProductGroupMapper;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.response.GroupWithProductsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分组查询服务。
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final ProductGroupMapper productGroupMapper;
    private final ProductMapper productMapper;

    /**
     * 查询全部分组及各组上架商品（骨架阶段数据量小，一次拉全）。
     */
    public List<GroupWithProductsResponse> listGroupsWithProducts() {
        List<ProductGroup> groups = productGroupMapper.selectList(
                new LambdaQueryWrapper<ProductGroup>().orderByAsc(ProductGroup::getSortOrder));
        List<Product> onSaleProducts = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getOnSale, true));
        Map<Long, List<Product>> productsByGroup = onSaleProducts.stream()
                .collect(Collectors.groupingBy(Product::getGroupId));
        return groups.stream()
                .map(g -> GroupWithProductsResponse.of(g, productsByGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }
}
```

`controller/GroupController.java`：

```java
package com.mintpop.shop.controller;

import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.GroupWithProductsResponse;
import com.mintpop.shop.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分组接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /** 分组列表（含各组上架商品） */
    @GetMapping("/groups")
    public ApiResponse<List<GroupWithProductsResponse>> listGroups() {
        return ApiResponse.success(groupService.listGroupsWithProducts());
    }
}
```

- [ ] **Step 4: 运行测试确认 service 测试通过**

Run: `cd apps/api && mvn test`
Expected: `GroupServiceTest` PASS。

- [ ] **Step 5: 写 controller 冒烟测试 `GroupControllerTest.java`（standalone MockMvc，不起 Spring 上下文）**

```java
package com.mintpop.shop.controller;

import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.GroupWithProductsResponse;
import com.mintpop.shop.response.ProductResponse;
import com.mintpop.shop.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupControllerTest {

    private MockMvc mockMvc;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new GroupController(groupService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/groups 返回 code 0 与分组数据")
    void listGroupsReturnsData() throws Exception {
        when(groupService.listGroupsWithProducts()).thenReturn(List.of(
                new GroupWithProductsResponse(1L, "盲盒系列", List.of(
                        new ProductResponse(11L, "薄荷精灵盲盒", "经典款", 5900L, null)))));

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("盲盒系列"))
                .andExpect(jsonPath("$.data[0].products[0].priceCents").value(5900));
    }
}
```

- [ ] **Step 6: 运行全部测试确认通过**

Run: `cd apps/api && mvn test`
Expected: 全部 PASS。

- [ ] **Step 7: 联调冒烟（真实数据库）**

```bash
mise run run-api
# 另开终端：
curl -s http://localhost:8080/api/groups
```

预期：`code:0`，3 个分组，「盲盒系列」下 3 个商品（已下架的「复古街机盲盒」不出现）。验证后停掉应用。

- [ ] **Step 8: 提交**

```bash
git add apps/api/src
git commit -m "feat: 分组商品查询接口 GET /api/groups

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: 下单接口 POST /api/orders

**Files:**
- Create: `apps/api/src/main/java/com/mintpop/shop/entity/ShopOrder.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/enumeration/OrderStatusEnum.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/mapper/ShopOrderMapper.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/request/CreateOrderRequest.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/response/CreateOrderResponse.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/service/OrderService.java`
- Create: `apps/api/src/main/java/com/mintpop/shop/controller/OrderController.java`
- Test: `apps/api/src/test/java/com/mintpop/shop/service/OrderServiceTest.java`
- Test: `apps/api/src/test/java/com/mintpop/shop/controller/OrderControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ApiResponse`/`BizCodeEnum.PRODUCT_NOT_ON_SALE`/`BizException`；Task 4 的 `Product` 实体与 `ProductMapper`
- Produces: `OrderService.createOrder(CreateOrderRequest): CreateOrderResponse`；HTTP `POST /api/orders`，请求体 `{productId: number, quantity: number}`，成功返回 `data = {orderNo: string, amountCents: number}`

- [ ] **Step 1: 写失败测试 `OrderServiceTest.java`**

```java
package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.CreateOrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ShopOrderMapper shopOrderMapper;
    @InjectMocks
    private OrderService orderService;

    private Product onSaleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setPriceCents(5900L);
        p.setOnSale(true);
        return p;
    }

    @Test
    @DisplayName("下单成功：金额=单价×数量，状态为待支付")
    void createOrderSuccess() {
        when(productMapper.selectById(1L)).thenReturn(onSaleProduct());

        CreateOrderResponse resp = orderService.createOrder(new CreateOrderRequest(1L, 2));

        assertThat(resp.getAmountCents()).isEqualTo(11800L);
        assertThat(resp.getOrderNo()).startsWith("MP");

        ArgumentCaptor<ShopOrder> captor = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatusEnum.PENDING_PAYMENT);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("商品不存在：抛业务异常 210001")
    void productNotFoundThrows() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(99L, 1)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("商品已下架：抛业务异常 210001")
    void productOffSaleThrows() {
        Product offSale = onSaleProduct();
        offSale.setOnSale(false);
        when(productMapper.selectById(1L)).thenReturn(offSale);

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest(1L, 1)))
                .isInstanceOf(BizException.class);
    }
}
```

- [ ] **Step 2: 运行测试确认编译失败**

Run: `cd apps/api && mvn test`
Expected: 编译错误（`ShopOrder`、`OrderService` 等不存在）。

- [ ] **Step 3: 实现订单模块**

`enumeration/OrderStatusEnum.java`：

```java
package com.mintpop.shop.enumeration;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态：成员名与持久化字符串取值逐字一致（SCREAMING_SNAKE_CASE）。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING_PAYMENT("PENDING_PAYMENT", "待支付");

    /** 持久化到数据库的取值 */
    @EnumValue
    private final String value;
    /** 中文描述 */
    private final String label;
}
```

`entity/ShopOrder.java`：

```java
package com.mintpop.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单实体（表 shop_order）。
 */
@Data
@TableName("shop_order")
public class ShopOrder {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 对外订单号，唯一 */
    private String orderNo;
    /** 商品ID */
    private Long productId;
    /** 购买数量 */
    private Integer quantity;
    /** 订单金额，单位分 */
    private Long amountCents;
    /** 订单状态 */
    private OrderStatusEnum status;
    /** 下单用户ID，预留（接入注册后填写） */
    private Long userId;
    /** 创建时间（数据库默认值维护） */
    private LocalDateTime createdAt;
    /** 更新时间（数据库默认值维护） */
    private LocalDateTime updatedAt;
}
```

`mapper/ShopOrderMapper.java`：

```java
package com.mintpop.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mintpop.shop.entity.ShopOrder;

/**
 * 订单 Mapper。
 */
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {
}
```

`request/CreateOrderRequest.java`：

```java
package com.mintpop.shop.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {

    /** 商品ID */
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    /** 购买数量（1~99） */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为 1")
    @Max(value = 99, message = "购买数量最多为 99")
    private Integer quantity;
}
```

`response/CreateOrderResponse.java`：

```java
package com.mintpop.shop.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 下单成功响应体。
 */
@Data
@AllArgsConstructor
public class CreateOrderResponse {

    /** 对外订单号 */
    private String orderNo;
    /** 订单金额，单位分 */
    private Long amountCents;
}
```

`service/OrderService.java`：

```java
package com.mintpop.shop.service;

import com.mintpop.shop.entity.Product;
import com.mintpop.shop.entity.ShopOrder;
import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.enumeration.OrderStatusEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.mapper.ProductMapper;
import com.mintpop.shop.mapper.ShopOrderMapper;
import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 下单服务。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProductMapper productMapper;
    private final ShopOrderMapper shopOrderMapper;

    /**
     * 创建待支付订单：校验商品存在且上架，金额=单价×数量。
     */
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !Boolean.TRUE.equals(product.getOnSale())) {
            throw new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE);
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo(generateOrderNo());
        order.setProductId(product.getId());
        order.setQuantity(request.getQuantity());
        order.setAmountCents(product.getPriceCents() * request.getQuantity());
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT);
        shopOrderMapper.insert(order);

        return new CreateOrderResponse(order.getOrderNo(), order.getAmountCents());
    }

    /** 订单号：MP + 时间戳 + 6 位随机数（骨架阶段单机够用） */
    private String generateOrderNo() {
        return "MP" + ORDER_NO_TS.format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(100000, 1000000);
    }
}
```

`controller/OrderController.java`：

```java
package com.mintpop.shop.controller;

import com.mintpop.shop.request.CreateOrderRequest;
import com.mintpop.shop.response.ApiResponse;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建待支付订单 */
    @PostMapping("/orders")
    public ApiResponse<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }
}
```

- [ ] **Step 4: 运行测试确认 service 测试通过**

Run: `cd apps/api && mvn test`
Expected: `OrderServiceTest` 3 个用例 PASS。

- [ ] **Step 5: 写 controller 测试 `OrderControllerTest.java`**

```java
package com.mintpop.shop.controller;

import com.mintpop.shop.enumeration.BizCodeEnum;
import com.mintpop.shop.exception.BizException;
import com.mintpop.shop.exception.GlobalExceptionHandler;
import com.mintpop.shop.response.CreateOrderResponse;
import com.mintpop.shop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerTest {

    private MockMvc mockMvc;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("下单成功返回 code 0 与订单号")
    void createOrderSuccess() throws Exception {
        when(orderService.createOrder(ArgumentMatchers.any()))
                .thenReturn(new CreateOrderResponse("MP20260713120000123456", 5900L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("MP20260713120000123456"));
    }

    @Test
    @DisplayName("数量为 0 触发参数校验，返回 110002")
    void invalidQuantityReturnsParamError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110002));
    }

    @Test
    @DisplayName("商品不存在返回 210001（HTTP 仍为 200）")
    void productNotOnSaleReturnsBizCode() throws Exception {
        when(orderService.createOrder(ArgumentMatchers.any()))
                .thenThrow(new BizException(BizCodeEnum.PRODUCT_NOT_ON_SALE));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210001))
                .andExpect(jsonPath("$.msg").value("商品不存在或已下架"));
    }
}
```

- [ ] **Step 6: 运行全部测试确认通过**

Run: `cd apps/api && mvn test`
Expected: 全部 PASS。

- [ ] **Step 7: 联调冒烟（真实数据库）**

```bash
mise run run-api
# 另开终端：
curl -s -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"productId":1,"quantity":2}'
curl -s -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"productId":999,"quantity":1}'
```

预期：第一条返回 `code:0`、`orderNo` 以 `MP` 开头、`amountCents:11800`（insert 成功才有返回）；第二条返回 `code:210001`。验证后停掉应用。（有 mysql 客户端可选加验 `shop_order` 表新增一行 `PENDING_PAYMENT`。）

- [ ] **Step 8: 提交**

```bash
git add apps/api/src
git commit -m "feat: 下单接口 POST /api/orders——校验上架商品并落库待支付订单

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: 前端脚手架（Vite + Vue 3 + TS + 设计 token）

**Files:**
- Create: `apps/web/package.json`
- Create: `apps/web/vite.config.ts`
- Create: `apps/web/tsconfig.json`
- Create: `apps/web/index.html`
- Create: `apps/web/src/env.d.ts`
- Create: `apps/web/src/main.ts`
- Create: `apps/web/src/App.vue`（临时壳，Task 7 替换）
- Create: `apps/web/src/styles/base.css`

**Interfaces:**
- Consumes: Task 1 的 `install-web` / `run-web` / `build-web` 任务
- Produces: 可运行的前端工程；CSS 变量 `--color-brand/--color-brand-deep/--color-ink/--color-ink-secondary/--color-bg/--color-bg-cloud/--color-border/--radius-card/--radius-button/--radius-pill`（Task 7 样式使用）；开发期 `/api` 代理到 `http://localhost:8080`

- [ ] **Step 1: 写 `package.json`（无 scripts，命令统一走 mise task）**

```json
{
  "name": "mintpop-shop-web",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "dependencies": {
    "@fontsource/fredoka": "^5.2.10",
    "@fontsource/inter": "^5.2.8",
    "vue": "^3.5.39"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^6.0.7",
    "typescript": "~5.9.3",
    "vite": "^8.1.4",
    "vue-tsc": "^3.3.7"
  }
}
```

- [ ] **Step 2: 写 `vite.config.ts`**

```ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期将 /api 代理到本地后端，避免跨域
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
```

- [ ] **Step 3: 写 `tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "strict": true,
    "noEmit": true,
    "skipLibCheck": true,
    "verbatimModuleSyntax": true,
    "types": ["vite/client"]
  },
  "include": ["src/**/*.ts", "src/**/*.vue"]
}
```

- [ ] **Step 4: 写 `index.html`**

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>MintPop Shop</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 5: 写 `src/env.d.ts`、`src/main.ts`、临时 `src/App.vue`、`src/styles/base.css`**

`src/env.d.ts`：

```ts
/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>
  export default component
}
```

`src/main.ts`：

```ts
import { createApp } from 'vue'
// 字体自托管（Fontsource），禁止外链 Google Fonts
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/fredoka/600.css'
import './styles/base.css'
import App from './App.vue'

createApp(App).mount('#app')
```

临时 `src/App.vue`（Task 7 会整体替换）：

```vue
<script setup lang="ts">
</script>

<template>
  <main class="page">
    <h1 class="wordmark">MintPop</h1>
    <p>商店骨架建设中……</p>
  </main>
</template>

<style scoped>
.page {
  padding: 32px;
}
</style>
```

`src/styles/base.css`：

```css
/* MintPop 设计基线 token */
:root {
  --color-brand: #17d1a7;
  --color-brand-deep: #0fb389;
  --color-ink: #0b0b0c;
  --color-ink-secondary: #6b7280;
  --color-bg: #ffffff;
  --color-bg-cloud: #f4f8f6;
  --color-border: #e5e7eb;
  --radius-card: 8px;
  --radius-button: 6px;
  --radius-pill: 999px;
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI",
    "PingFang SC", "Microsoft YaHei", Roboto, "Helvetica Neue", Arial, sans-serif;
  color: var(--color-ink);
  background: var(--color-bg);
  -webkit-font-smoothing: antialiased;
}

/* 品牌词标专用字体 */
.wordmark {
  font-family: "Fredoka", "Inter", sans-serif;
}
```

- [ ] **Step 6: 安装依赖并验证 dev/build**

```bash
mise run install-web
mise run build-web
```

预期：类型检查通过、`vite build` 产出 `apps/web/dist`。再 `mise run run-web` 打开 http://localhost:5173 能看到「MintPop 商店骨架建设中……」，验证后停掉。

- [ ] **Step 7: 提交**

```bash
git add apps/web
git commit -m "feat: web 前端脚手架——Vite + Vue 3 + TS 与 MintPop 设计 token

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: 前端页面与购买流（分组导航 / 商品网格 / 下单 toast）

**Files:**
- Create: `apps/web/src/api.ts`
- Create: `apps/web/src/components/ProductCard.vue`
- Modify: `apps/web/src/App.vue`（整体替换 Task 6 的临时壳）

**Interfaces:**
- Consumes: Task 4/5 的 HTTP 接口（`GET /api/groups`、`POST /api/orders`，`ApiResponse` 包装，`code===0` 为成功）；Task 6 的 CSS token
- Produces: 完整可用的商店单页

- [ ] **Step 1: 写 `src/api.ts`（类型镜像后端响应体 + 请求封装）**

```ts
/** 后端统一返回结构：code=0 成功，非 0 失败取 msg */
export interface ApiResponse<T> {
  code: number
  data: T | null
  msg: string | null
}

/** 商品（镜像后端 ProductResponse） */
export interface Product {
  id: number
  name: string
  description: string | null
  priceCents: number
  imageUrl: string | null
}

/** 分组含商品（镜像后端 GroupWithProductsResponse） */
export interface GroupWithProducts {
  id: number
  name: string
  products: Product[]
}

/** 下单结果（镜像后端 CreateOrderResponse） */
export interface CreateOrderResult {
  orderNo: string
  amountCents: number
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  const body = (await res.json()) as ApiResponse<T>
  if (body.code !== 0) {
    throw new Error(body.msg ?? '请求失败，请稍后重试')
  }
  return body.data as T
}

/** 拉取全部分组及上架商品 */
export function fetchGroups(): Promise<GroupWithProducts[]> {
  return request<GroupWithProducts[]>('/api/groups')
}

/** 创建待支付订单（骨架阶段数量固定 1） */
export function createOrder(productId: number): Promise<CreateOrderResult> {
  return request<CreateOrderResult>('/api/orders', {
    method: 'POST',
    body: JSON.stringify({ productId, quantity: 1 }),
  })
}

/** 分转元展示 */
export function formatPrice(cents: number): string {
  return `¥${(cents / 100).toFixed(2)}`
}
```

- [ ] **Step 2: 写 `src/components/ProductCard.vue`**

```vue
<script setup lang="ts">
import { formatPrice, type Product } from '../api'

defineProps<{ product: Product; buying: boolean }>()
const emit = defineEmits<{ buy: [product: Product] }>()
</script>

<template>
  <article class="card">
    <div class="thumb">
      <img v-if="product.imageUrl" :src="product.imageUrl" :alt="product.name" />
      <span v-else class="placeholder" aria-hidden="true">{{ product.name.charAt(0) }}</span>
    </div>
    <div class="body">
      <h3 class="name">{{ product.name }}</h3>
      <p class="desc">{{ product.description ?? '' }}</p>
      <div class="footer">
        <span class="price">{{ formatPrice(product.priceCents) }}</span>
        <button
          class="buy-btn"
          type="button"
          :disabled="buying"
          @click="emit('buy', product)"
        >
          {{ buying ? '下单中…' : '购买' }}
        </button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: box-shadow 0.15s ease;
}

.card:hover {
  box-shadow: 0 4px 16px rgba(11, 11, 12, 0.08);
}

.thumb {
  aspect-ratio: 4 / 3;
  background: var(--color-bg-cloud);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  max-width: 100%;
}

/* 无图占位：薄荷色圆底 + 商品首字 */
.placeholder {
  width: 72px;
  height: 72px;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 32px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  flex: 1;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.desc {
  font-size: 13px;
  color: var(--color-ink-secondary);
  flex: 1;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.price {
  font-size: 16px;
  font-weight: 600;
}

.buy-btn {
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-button);
  background: var(--color-brand);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.buy-btn:hover:not(:disabled) {
  background: var(--color-brand-deep);
}

.buy-btn:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.buy-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
```

- [ ] **Step 3: 整体替换 `src/App.vue`**

```vue
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createOrder, fetchGroups, type GroupWithProducts, type Product } from './api'
import ProductCard from './components/ProductCard.vue'

const groups = ref<GroupWithProducts[]>([])
const activeGroupId = ref<number | null>(null)
const loading = ref(true)
const loadError = ref('')
const buyingProductId = ref<number | null>(null)

interface Toast {
  type: 'success' | 'error'
  text: string
}
const toast = ref<Toast | null>(null)
let toastTimer: ReturnType<typeof setTimeout> | undefined

function showToast(type: Toast['type'], text: string) {
  toast.value = { type, text }
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toast.value = null
  }, 3000)
}

const activeGroup = computed(
  () => groups.value.find((g) => g.id === activeGroupId.value) ?? null,
)

onMounted(async () => {
  try {
    groups.value = await fetchGroups()
    activeGroupId.value = groups.value[0]?.id ?? null
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
})

async function buy(product: Product) {
  buyingProductId.value = product.id
  try {
    const result = await createOrder(product.id)
    showToast('success', `下单成功，订单号 ${result.orderNo}`)
  } catch (e) {
    showToast('error', e instanceof Error ? e.message : '下单失败，请稍后重试')
  } finally {
    buyingProductId.value = null
  }
}
</script>

<template>
  <header class="header">
    <h1 class="wordmark">MintPop <span class="wordmark-sub">Shop</span></h1>
  </header>

  <main class="page">
    <p v-if="loading" class="hint">加载中……</p>
    <p v-else-if="loadError" class="hint error">{{ loadError }}</p>

    <template v-else>
      <nav class="group-nav" aria-label="商品分组">
        <button
          v-for="group in groups"
          :key="group.id"
          type="button"
          class="pill"
          :class="{ active: group.id === activeGroupId }"
          @click="activeGroupId = group.id"
        >
          {{ group.name }}
        </button>
      </nav>

      <section v-if="activeGroup" class="grid" aria-live="polite">
        <ProductCard
          v-for="product in activeGroup.products"
          :key="product.id"
          :product="product"
          :buying="buyingProductId === product.id"
          @buy="buy"
        />
      </section>
      <p v-if="activeGroup && activeGroup.products.length === 0" class="hint">
        该分组暂无上架商品
      </p>
    </template>
  </main>

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status">
      {{ toast.text }}
    </div>
  </Transition>
</template>

<style scoped>
.header {
  padding: 16px 32px;
  border-bottom: 1px solid var(--color-border);
}

.wordmark {
  font-size: 24px;
  color: var(--color-brand-deep);
}

.wordmark-sub {
  color: var(--color-ink);
  font-weight: 500;
}

.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 32px 48px;
}

.hint {
  color: var(--color-ink-secondary);
  padding: 24px 0;
}

.hint.error {
  color: #b91c1c;
}

.group-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 24px;
}

.pill {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: var(--color-bg);
  color: var(--color-ink);
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.pill:hover {
  border-color: var(--color-brand);
  color: var(--color-brand-deep);
}

.pill:focus-visible {
  outline: 2px solid var(--color-brand-deep);
  outline-offset: 2px;
}

.pill.active {
  background: var(--color-brand);
  border-color: var(--color-brand);
  color: #ffffff;
  font-weight: 500;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.toast {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: var(--radius-card);
  background: var(--color-ink);
  color: #ffffff;
  font-size: 14px;
  box-shadow: 0 8px 24px rgba(11, 11, 12, 0.16);
  z-index: 10;
}

.toast.success {
  background: var(--color-brand-deep);
}

.toast.error {
  background: #b91c1c;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
}
</style>
```

- [ ] **Step 4: 类型检查与构建**

Run: `mise run build-web`
Expected: `vue-tsc --noEmit` 与 `vite build` 均通过。

- [ ] **Step 5: 提交**

```bash
git add apps/web/src
git commit -m "feat: web 商店页——分组导航、商品网格与购买下单流

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: 端到端验收与 README

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: 前七个 Task 的全部产出
- Produces: 验收通过的骨架 + 中文 README

- [ ] **Step 1: 端到端验收（对照设计文档验收标准）**

```bash
mise run run-api   # 终端 1，等 “Started ShopApplication”
mise run run-web   # 终端 2
```

浏览器打开 http://localhost:5173，逐项确认：

1. 页面显示 MintPop 词标、3 个分组 pill、当前分组商品卡片（种子数据）；
2. 已下架商品「复古街机盲盒」不出现；
3. 点击任意商品「购买」→ 弹出成功 toast 并显示订单号；
4. 再点一次购买，两次订单号不同（每次都真实落库）；有 mysql 客户端可选加验 `shop_order` 表最新一行状态为 `PENDING_PAYMENT`。

任一项不符则回到对应 Task 修复后重验。

- [ ] **Step 2: 写 `README.md`**

```markdown
# MintPop Shop

MintPop 品牌商店：前端分组展示商品，点击购买创建待支付订单。注册与支付由统一文档另行接入（订单表已预留 `user_id` 与状态扩展）。

## 技术栈

- **apps/api**：Spring Boot 4 单体（Java 21 / Maven）+ MyBatis-Plus + Flyway + MySQL
- **apps/web**：Vue 3 + Vite + TypeScript（字体 Fontsource 自托管）
- 工具链与命令统一由根 `mise.toml` 管理

## 快速开始

1. 复制 `mise.local.example.toml` 为 `mise.local.toml`，填入你的 MySQL 连接信息（该文件已被 gitignore，凭据不入库；账号有建库权限时会自动创建 `mintpop_shop` 库）。
2. 依次执行：

​```bash
mise install          # 安装工具链（java/maven/node/pnpm）
mise run install      # 安装项目依赖
mise run run-api      # 终端 1：启动后端（8080，Flyway 自动建表并写入种子数据）
mise run run-web      # 终端 2：启动前端（5173，/api 代理到 8080）
​```

打开 http://localhost:5173 即可看到商店页面。

## 常用命令

| 命令 | 说明 |
|---|---|
| `mise run run-api` / `run-web` | 启动后端 / 前端 |
| `mise run test-api` | 后端单元测试 |
| `mise run build-api` / `build-web` | 构建后端 jar / 前端产物 |

## 接口

统一返回 `ApiResponse<T>`（`code` 0=成功 / `data` / `msg`），HTTP 一律 200。

| 接口 | 说明 |
|---|---|
| `GET /api/groups` | 分组列表（含各组上架商品） |
| `POST /api/orders` | 创建待支付订单，body `{productId, quantity}` |
```

（写入文件时把 ​``` 的零宽字符去掉，用真实的三反引号围栏。）

- [ ] **Step 3: 提交**

```bash
git add README.md
git commit -m "docs: 项目 README——技术栈、快速开始与接口说明

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
