# AI智能客服项目补强路线

> 日期：2026-08-22
> 原则：先修复会影响项目真实性和可解释性的基础问题，再增加新功能。每项能力只有在代码、自动化测试和可复现报告同时存在后，才允许写入简历成果。

## 一、优先级总览

```text
P0 可信基线
→ P1 路由与State正确性
→ P2 RAG证据闭环
→ P3 真实只读工具闭环
→ P4 记忆与Checkpoint一致性
→ P5 可靠性、安全与可观测性
→ P6 多实例和部署
```

## 二、P0：建立可信基线

### 目标

让“当前代码能做什么、效果是多少”可以复现。

### 任务

- 为Python评测请求补充`userId`，适配当前ChatEvent结构。
- 固定Git commit、模型、Prompt、知识库、Embedding及配置版本。
- 重写route ontology：每类定义、正例、反例、优先级和多意图规则。
- 清理FAQ与Knowledge Base标签冲突。
- 给关键Node、SessionKey、SessionExecutionGate、Retriever添加自动化测试。
- 增加真实连续多轮、服务重启恢复、跨用户隔离和同会话并发集成测试。
- 删除或明确标记历史虚假/不可复现指标，生成新的baseline报告。

### 完成证据

```text
固定commit
+ 一条命令运行的测试/评测
+ 原始逐条结果
+ 失败用例列表
+ 指标定义文档
```

### 面试收益

能够可信回答“如何评测、指标是多少、BadCase是什么、怎么复现”。

## 三、P1：修复路由与Graph State

### 任务

- 将`route`拆为`intentRoute`与`nextAction`。
- 统一LLM confidence为0~1并用Schema校验。
- 全部规则0分时显式进入unknown/clarify。
- 增加Top1阈值和Top1/Top2 margin。
- 使用结构化输出DTO替代正则JSON解析。
- 模型调用异常时回退规则决策。
- 将`assistant.graph.max-iterations`真正接入CompileConfig。
- 通过固定边让FAQ/Knowledge/Tool/Search进入AiReply，减少流程控制字段污染。

### 完成证据

- 路由单元测试覆盖合法、非法JSON、超时、低置信度和并列分数。
- Rule-only、LLM-only和Fusion消融报告。
- 不同权重与阈值的Macro-F1、各类Recall和拒判率。

### 面试收益

能够解释结构化输出、置信度校准、不确定性路由和Graph状态建模。

## 四、P2：补齐RAG证据闭环

### 任务

- 建立query→gold chunk IDs与必要事实标注。
- 明确最终TopK，避免配置Top5但实际发送Top10。
- 多子问题分别召回并保证证据配额，再全局去重。
- 增加证据充分性判断和无答案拒答。
- 接入引用数据结构并返回source/chunkId/snippet。
- tenantId/productCode/locale加入可信metadata过滤。
- 给向量缓存增加文档Hash、chunk版本、Embedding模型和维度校验。
- 在基线后评估是否增加Rerank；没有收益就不引入。

### 完成证据

- Vector-only、BM25-only、Hybrid、Hybrid+Rerank消融。
- Recall@K、MRR、NDCG、Faithfulness、Completeness和Citation Accuracy。
- 无答案、错别字、指代、多意图、长文本及Prompt Injection回归集。

### 面试收益

从“会调用向量库”升级为能够解释检索质量、证据约束和实验取舍。

## 五、P3：落地一个真实只读工具闭环

### 第一工具建议

实现“订单状态查询”只读工具；可以先连接本地测试数据或明确的开发接口，但不能把Mock描述为真实生产业务系统。

### 链路

```text
选择query_order工具
→ ToolCallback白名单
→ orderId Schema校验
→ 从服务端认证上下文取得userId
→ 订单归属权限校验
→ 超时与有限重试
→ ToolOutcome
→ answer / clarify / fallback / human
```

### 任务

- 定义结构化ToolRequest/ToolOutcome和错误码。
- 覆盖缺订单号、格式错误、无权限、未找到、超时和服务故障。
- State增加toolCallId、toolAttempt、toolOutcome和lastErrorCode。
- 增加最大attempt与Graph退出边。
- 形成完整Observation后再决定是否需要Replan。
- 本地闭环稳定后，将同一工具抽象为MCP Server并比较差异。

### 完成证据

- Tool选择、参数、权限、错误和Graph分支测试。
- 轨迹日志与工具调用成功率。
- MCP版本需额外验证`isError/structuredContent/outputSchema`。

### 面试收益

项目真正具备受控Agent工具闭环，而不只是工具节点占位。

## 六、P4：统一记忆与Checkpoint

### 任务

- 建立结构化ConversationMessage作为权威近期消息源。
- 移除`messages`与`recentHistory`双轨，或明确各自职责。
- 所有终止分支统一追加user和assistant消息。
- 按Token预算而非固定3轮触发摘要。
- 关键订单号、待补字段和审批状态独立结构化保存，不依赖摘要。
- 增加append-only完整会话档案。
- 修复字符串切分和重复`用户:`前缀问题。
- Checkpoint增加TTL、删除、大小上限和敏感字段治理。

### 完成证据

- 多轮指代、重复输入、超长消息、摘要失败和重启恢复测试。
- 摘要前后关键事实保持率抽样。

### 面试收益

能够系统解释短期记忆、摘要、档案、任务状态和长期记忆边界。

## 七、P5：可靠性、安全与可观测性

### 可靠性

- 客户端超时、瞬时错误有限重试、指数退避和熔断。
- 路由→规则、改写→原query、向量→BM25等局部降级。
- HistoryManager失败不影响主回答。
- 同步统一错误响应和SSE error事件。

### 安全

- Spring Security/JWT，userId和tenantId来自认证上下文。
- System/User/Context分层Prompt。
- 文档和工具结果视为不可信数据。
- Prompt Injection、越权、PII和成本滥用测试。
- 日志脱敏，Checkpoint加密与生命周期治理。

### 可观测性

- HTTP→Graph→Node→LLM/RAG/Checkpoint/Tool Trace。
- 延迟、错误、Token、成本、空召回、降级、摘要与锁等待指标。
- 结构化日志只记录ID哈希、枚举和诊断字段。
- Dashboard、SLO和告警。

### 完成证据

- 可人为注入模型超时、检索失败和工具错误并观察降级。
- Prometheus/Trace界面能定位单请求链路。
- 安全回归集通过且无敏感原文默认日志。

## 八、P6：多实例与部署

### 任务

- Docker化和dev/test/prod配置分离。
- 共享Checkpoint存储与version CAS。
- 检索索引服务化和kbVersion原子发布。
- 多实例同会话一致性测试。
- Gateway认证、限流及SSE代理配置。
- Secret Manager、健康检查、优雅关闭。
- CI/CD绑定代码、Prompt、模型和知识库版本。
- Canary质量/延迟/错误/成本门禁与自动回滚。

### 完成证据

- 两个应用实例下完成会话连续性和并发测试。
- 滚动重启后Checkpoint可恢复。
- 新旧知识库版本可切换和回滚。

## 九、推荐实际执行顺序

第一轮只做四件事：

1. 修复评测兼容和route/confidence问题。
2. 建立可信路由与RAG baseline。
3. 补证据门控与引用。
4. 实现一个只读订单ToolCallback闭环。

完成这四项后，项目的面试说服力会明显高于继续堆Neo4j、Kubernetes或更多Agent框架名词。

## 十、简历更新门槛

只有满足以下条件才允许更新简历：

```text
能力有真实代码
→ 自动化测试通过
→ 当前commit可运行
→ 指标定义清晰并可复现
→ 面试能解释设计取舍与失败边界
```

“计划实现”“理解原理”“写了占位节点”不能转换为“已落地成果”。
