# P03 T01 · Jackson 2 基线清单

baselineGitRef: `e26e27bf8745dfa2c14c73d6b3722a72af25013f`（等同 `v20260917-P02` 冻结提交；授权时 HEAD）

recordedAtUtc: 2026-09-17T14:25:00Z

## 1. 模块 POM（Jackson 直接依赖）

| 模块 | 坐标 | 备注 |
| --- | --- | --- |
| domain | `com.fasterxml.jackson.core:jackson-annotations` + `jackson-databind` | annotations 官方例外保留 |
| application | `jackson-databind` | |
| runtime | `jackson-databind` + `jackson-datatype-jsr310` | jsr310 迁移后删除 |
| storage-mysql | `jackson-databind` | |
| scenario-basic | `jackson-databind` | |
| capability-notification | `jackson-databind` | 生产无 databind import，可删除 |
| web | `jackson-databind` | |
| Boot BOM | Jackson 2.21.5 + tools.jackson 3.1.5 并存 | P02 用 Jackson2WebConfig 强制 2 |

## 2. Mapper / 转换器

| 位置 | 角色 |
| --- | --- |
| `RuntimeBeans#objectMapper` | `@Primary ObjectMapper`；FAIL_ON_UNKNOWN；STRICT_DUPLICATE_DETECTION |
| `Jackson2WebConfig` | 强制 `MappingJackson2HttpMessageConverter`，剥离 Boot Jackson 3 转换器 |
| boot-loader | 无 JsonMapper 配置 |

## 3. 非 annotation 引用规模

约 81 个 Java 文件使用 `com.fasterxml.jackson.core` / `databind`（含 boot IT）。domain 另有仅 annotation 的 DTO。

## 4. 迁移前黄金契约

```text
./mvnw -q -pl timeimprint-task-service-runtime -Dtest=JsonGoldenContractTest test
```

退出码 0（2026-09-17）。

## 5. Annotations 例外

禁止机械替换 `com.fasterxml.jackson.annotation.*`；`jackson-annotations` 坐标保持 `com.fasterxml.jackson.core`。
