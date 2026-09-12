# TimeImprintTask

稳定内核 + 可插拔能力的通用任务平台。首期（P01）交付本地固定身份下的可运行后端：S01 通用提醒、S02 周期待办、五种公历规则与站内信。

## 技术基线

| 项 | 版本 |
| --- | --- |
| Java | 21 LTS（`release=21`） |
| Spring Boot | 4.0.8 |
| MyBatis Starter | 4.0.1 |
| Spring AI BOM | 2.0.1（仅父 POM 锁定，首期无 AI 运行依赖） |
| MySQL 验收镜像 | `mysql:9.7.2`（digest 见 `docs/phases/P01/T01-ENV-EVIDENCE.txt`） |
| Maven Wrapper | 3.3.2 / Apache Maven 3.9.9 |

## 模块

13 个平级 Maven 模块，前缀 `timeimprint-task-*`。组合根为 `timeimprint-task-boot-loader`。

## 本地启动

1. 准备 MySQL 9.7.x（推荐官方镜像 `mysql:9.7.2`），库名写入 `DB_JDBC_URL`，**不要**让应用自动建库。
2. 复制 `.env.example` 为本地环境变量（勿提交真实密码）。
3. 构建：

```bash
export JAVA_HOME=... # JDK 21
./mvnw -q test
./mvnw -q package
```

4. 启动（任选其一）：

```bash
# A. 推荐：一条命令（-am 编译依赖；仅 boot-loader 真正 run）
./mvnw -pl timeimprint-task-boot-loader -am -DskipTests spring-boot:run

# B. jar
export SERVER_ADDRESS=127.0.0.1
export SERVER_PORT=18080
export DB_JDBC_URL='jdbc:mysql://127.0.0.1:13306/timeimprint_task_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC'
export DB_USERNAME=tit
export DB_PASSWORD=tit_local
export INSTANCE_ID=local-1
export LOCAL_TENANT_ID=local-tenant
export LOCAL_ACTOR_ID=local-actor
./mvnw -pl timeimprint-task-boot-loader -am -DskipTests package
java -jar timeimprint-task-boot-loader/target/timeimprint-task-boot-loader-*.jar
```

只执行 `./mvnw -pl timeimprint-task-boot-loader spring-boot:run` 且未先 package/install 时，会因本地仓库缺少兄弟模块而失败。

健康检查仅回环开放：`/actuator/health/liveness`、`/actuator/health/readiness`。

本地 curl 手工联调见 [docs/phases/P01/MANUAL-HTTP.md](docs/phases/P01/MANUAL-HTTP.md)（已实现端点清单与 S01/S02 示例；字段语义以 [docs/04-API.md](docs/04-API.md) 为准）。

## 测试分层

| 命令 | 含义 |
| --- | --- |
| `./mvnw test` | 无库单元 / 架构测试 |
| `./mvnw -Pmysql-it verify` | 真 MySQL 集成测试 |
| `./mvnw -Pmysql-it,dual-process-it verify` | 双进程恢复与竞争 |

禁止使用 H2 代替锁与迁移证据。

## 文档

从 [docs/00-READING-ORDER.md](docs/00-READING-ORDER.md) 进入。当前阶段为 [P01](docs/phases/P01/README.md)。
