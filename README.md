# AI 智能客服 Agent

基于 Java 17、Spring Boot 与 Spring AI Alibaba 构建的智能客服 Agent。它以状态图编排请求，覆盖受认证保护的会话问答、混合 RAG 检索、订单状态查询、会话上下文持久化和同步/SSE 两种响应方式。

## 已实现能力

- JWT Resource Server 认证；用户身份从 Spring Security 上下文获取。
- 会话隔离：`userId + sessionId` 生成稳定 Graph threadId，并用 Redis 分布式锁防止同会话并发执行。
- 请求追踪：响应返回 `X-Trace-Id`、`X-Span-Id`，日志写入 traceId。
- Graph 路由：规则与 LLM 两路融合，将请求分发至 FAQ、知识库、订单工具、补问、人工转接或外部搜索分支。
- RAG：查询清洗、查询改写、向量检索与 BM25 双路召回、RRF 融合、可选 DashScope Rerank 和证据充分性门控。
- 工具：订单号格式校验、从认证上下文取得用户身份，并以 `userId + orderNo` 查询本地 MySQL 订单数据。
- 会话与任务数据：MySQL 持久化会话、消息、摘要、人工转接工单和异步任务状态；Redis 缓存热上下文和摘要版本。
- 数据库迁移：Flyway 管理版本化表结构，应用启动时自动执行迁移。
- 知识隔离：检索链路根据 `tenantId`、`productCode`、`locale` 过滤带范围 metadata 的知识块。
- 可观测性：Actuator 健康探针、Prometheus 指标、Trace/Span 响应头，以及聊天成功/失败/耗时指标。
- 持续验证：GitHub Actions 在推送和 Pull Request 时执行 Maven `verify`。
- 运行保护：单会话锁、全局并发闸门、输入/节点/工具/模型调用预算、30 秒 Graph 超时与基础熔断组件。
- 单元与集成测试：覆盖订单权限、Redis Context、摘要 CAS、消息持久化、会话锁及主要 guard。

## 架构

```text
HTTP / SSE
  -> Spring Security (JWT) + TraceId
  -> ChatController
  -> Redis 同会话锁 + 全局并发闸门
  -> StateGraph
       semantic_route
       ├─ clarify / human_handoff -> END
       ├─ faq / knowledge_base -> hybrid RAG -> ai_reply
       ├─ business_tool -> order query -> ai_reply
       └─ external_search -> ai_reply
  -> MySQL: conversation, chat_message, summary, agent_task, handoff_ticket, order
  -> Redis: hot context, summary CAS, session lock
```

## 运行要求

- JDK 17+（开发环境使用 JDK 21，以 `--release 17` 构建）
- Docker Compose（可选，用于 MySQL 与 Redis）
- DashScope API Key（调用 LLM/Rerank 时需要）

## 本地启动

1. 启动基础设施：

   ```bash
   docker compose -f compose.infrastructure.yml up -d
   ```

2. 设置模型密钥（不要提交 `.env` 或任何密钥）：

   ```bash
   export AI_DASHSCOPE_API_KEY='your-key'
   export AI_JWT_SECRET='at-least-32-characters-development-secret'
   ```

3. 启动服务：

   ```bash
   ./mvnw spring-boot:run
   ```

开发默认端口为 `8081`。Flyway 会自动执行数据库迁移。健康检查：`GET /actuator/health`，Prometheus 指标：`GET /actuator/prometheus`。

## 调用接口

所有 `/chat/**` 接口都需要 Bearer JWT。JWT subject 是用户 ID；若 JWT 带 `tenant_id` claim，它会作为可信租户标识写入会话记录。请求中的 `tenantId` 不被信任，也不参与权限判断。

### 同步问答

```bash
curl -X POST http://localhost:8081/chat/ask \
  -H 'Authorization: Bearer <jwt>' \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"session-001","clientMessageId":"msg-001","message":"G6 的质保政策是什么？","locale":"zh-CN"}'
```

### SSE 流式问答

```bash
curl -N -X POST http://localhost:8081/chat/ask/stream \
  -H 'Authorization: Bearer <jwt>' \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":"session-001","clientMessageId":"msg-002","message":"订单 ORD123456 到哪里了？"}'
```

`sessionId` 最大 64 个字符，`clientMessageId` 最大 128 个字符，消息最大 16,000 字符。同一会话在执行期间会拒绝并发请求；重复的 `clientMessageId` 会被拒绝，客户端应复用原请求结果。

### 异步任务

`POST /chat/tasks` 使用与同步问答相同的请求体，立即返回 `taskId`；随后可通过 `GET /chat/tasks/{taskId}` 查询 `QUEUED`、`RUNNING`、`COMPLETED` 或 `FAILED` 状态及完成后的事件结果。任务状态、结果与失败码会写入 MySQL。

RAG 完成事件包含 `citations`，每项提供 `source`、`chunkId` 和截断后的 `snippet`，便于前端展示引用来源。

## 测试

```bash
./mvnw test
```

测试使用 H2 或 Mock，不需要 DashScope、MySQL、Redis 服务。评测脚本与历史结果位于项目根目录；GitHub Actions 会自动执行 `./mvnw --batch-mode verify`。

## 容器化与生产配置

先构建 Jar，再构建镜像：

```bash
./mvnw package
docker build -t aiagent:local .
```

生产环境使用 `prod` profile，并显式提供下列环境变量：

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
AI_DASHSCOPE_API_KEY
AI_JWT_SECRET
AI_SEARCH_ENDPOINT
```

`application-prod.yml` 不提供数据库、模型密钥或 JWT 密钥默认值，避免将开发配置误用于生产。生产数据库应通过受版本控制的迁移工具管理。

## 安全说明

- 不要将 DashScope Key、JWT 密钥、数据库密码、日志或 `data/checkpoints/` 提交到仓库。
- 工具查询使用认证用户 ID，而不是客户端传入的用户 ID。
- 知识库内容与工具输出应视为不可信上下文；高风险动作应继续保持在服务端明确授权的工具白名单内。
- 当前 JWT 示例采用对称 HMAC 解码器；生产环境还应配置 issuer/audience 校验、密钥轮换、网关限流、日志脱敏和审计策略。

## 项目结构

```text
src/main/java/com/aiagent/
  config/        Graph、模型和 RAG 配置
  controller/    同步与 SSE 接口
  guard/         预算、熔断、回答与工具保护
  memory/        Redis 上下文和摘要
  persistence/   MySQL 会话/消息持久化
  task/          异步任务提交、状态与结果持久化
  handoff/       人工转接工单服务
  search/        外部搜索网关适配器
  observability/ Trace 与 Prometheus 指标
  security/      JWT 与当前用户
  session/       SessionKey、Redis 锁和全局闸门
  tool/          订单查询工具
src/test/        自动化测试
knowledge_base/  示例知识库与切分结果
evaluation/      离线评测、测试数据与压测脚本
```

Python 评测与压测材料位于 [`evaluation/`](evaluation/README.md)，Java 自动化测试位于 `src/test/`。
