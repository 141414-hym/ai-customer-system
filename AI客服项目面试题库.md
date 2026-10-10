# AI智能客服项目面试题库

> 生成日期：2026-08-22
> 依据：当前源码、配置、评测脚本及`AI客服项目面试拆解.md`。
> 使用方法：模拟面试时一次回答一题；先给结论，再讲链路、取舍、证据和不足。未实现能力必须使用“计划/方案/演进”，不能使用“已经完成”。

## 一、项目开场

### 1. 用一分钟介绍项目

答题要点：

- Java 17、Spring Boot 3.5、Spring AI Alibaba Graph、DashScope。
- 六类意图路由，规则与LLM初始融合。
- 查询清洗、上下文改写、向量+BM25、RRF、统一回答。
- recentHistory+historySummary短期记忆。
- SessionKey、threadId、FileSystemSaver、Semaphore分片。
- 同步接口与SSE节点事件。
- 主动限定：工具/MCP仍是占位，当前是Agentic Workflow。

#### 60秒参考答案

> 我做的是一个基于Java和Spring AI Alibaba Graph的汽车产品智能客服系统，主要解决客服问题类型不同、用户表达不规范以及知识回答缺少证据的问题。请求进入后，系统先通过规则和大模型融合完成六类意图路由；知识问答分支会进行查询清洗，并结合近期对话和历史摘要完成指代补全与多意图拆解，随后同时执行DashScope Embedding向量检索和基于Lucene CJKAnalyzer的BM25检索，再通过RRF融合结果，最后让qwen-max根据检索证据生成回答。工程上我使用userId和sessionId生成Graph threadId，通过FileSystemSaver保存Checkpoint，并使用256个Semaphore分片降低同会话并发覆盖风险；接口同时支持同步响应和SSE节点事件。当前完成度最高的是Graph、混合RAG和会话状态，业务工具、MCP、生产级共享存储以及完整可靠性策略还没有落地，所以我把它定位为可运行的Agentic Workflow，而不是生产级自主Agent。

#### 评分标准（10分）

- 2分：说清楚业务问题，而不是只报技术栈。
- 2分：说清规则+LLM路由及Graph分支。
- 2分：说清清洗/改写/向量+BM25/RRF/回答链路。
- 2分：说清threadId、Checkpoint和同会话并发控制。
- 1分：准确说明SSE是节点事件。
- 1分：主动说明工具/MCP和生产化能力尚未完成。

#### 容易扣分的说法

- 一上来堆满框架名，没有业务问题和调用链。
- 把`node_async`说成每个节点都会启动新线程。
- 把SSE说成模型Token流。
- 声称已打通订单、物流和MCP。
- 使用92.5%、91.8%等当前无法复现的数字。
- 讲了两分钟仍没有说出自己做了什么。

### 2. 项目解决了什么业务问题？

答题要点：

- 不同客服请求不能全部当成知识问答。
- 需要先区分知识、澄清、人工和未来实时业务查询。
- 用户表达存在口语、错别字、指代和多意图。
- 回答需要尽可能基于知识证据，而不是模型自由生成。
- 当前是学习型汽车产品知识场景，不能声称真实企业日均请求量或业务收益。

### 3. 这是Agent还是Workflow？

标准结论：

> 它是Graph-based Agentic Workflow。模型参与路由、查询改写和回答，但流程由预定义Node与Edge控制；没有Planner—Tool—Observation—Replan循环，也没有真实工具执行，因此不是高度自主Agent。

### 4. 为什么选择Java而不是Python？

答题要点：

- 目标是理解AI通用原理并在Java/Spring体系工程化落地。
- Java适合企业既有认证、权限、事务、监控和微服务体系整合。
- Spring AI统一模型、工具、记忆等抽象，Alibaba Graph提供Java状态图。
- Python生态更适合原型、数据处理和评测；不是互斥，而是双轨分工。

### 5. 你在项目中负责什么？

只能依据当前源码回答：

- Graph与节点划分；
- 规则+LLM路由；
- 查询清洗/改写与混合RAG；
- 近期历史+摘要；
- DTO、会话复合Key、Checkpoint、同会话并发控制；
- Python评测骨架和问题复盘。

不要加入真实企业接入、销售效果、生产QPS等无证据内容。

## 二、Graph与Spring AI

### 6. 为什么不用一个Service加if-else？

- 简单无状态问答用Service更合适。
- 当前有六类分支、共享State、Checkpoint和节点事件。
- Node处理业务，Edge处理流程，KeyStrategy处理状态合并。
- 承认Graph有学习成本、字符串State和过度设计风险。

### 7. ChatClient、ChatModel、DashScopeChatModel分别是什么？

- ChatClient：应用侧高级调用API，组织Prompt/Advisor/Tool。
- ChatModel：统一模型接口。
- DashScopeChatModel：对接阿里DashScope的Provider实现。
- DashScope是平台，qwen-max是具体模型。

### 8. StateGraph和CompiledGraph有什么区别？

- StateGraph：节点、边和State策略的定义蓝图。
- compile阶段做结构校验并绑定Saver等配置。
- CompiledGraph：运行时可执行对象。

### 9. Node、Edge和State如何配合？

```text
Node读取State并返回局部Map
→ KeyStrategy合并
→ Edge读取最新State选择next node
→ Checkpoint/NodeOutput
→ 下一Node
```

Edge不传输业务数据，Node不直接调用下一Node。

### 10. KeyStrategy有什么作用？

- Replace用于route、answer和当前状态。
- Append用于列表消息。
- Merge用于Map累计。
- 错误策略会造成旧路由残留、消息丢失或重复。

### 11. `node_async`是否真的异步？

标准结论：

> 它把同步NodeAction适配为Future接口，不自动创建新线程，也不会把内部`ChatClient.call()`变成非阻塞调用。

### 12. Future、Mono、Flux分别用于什么？

- Future：未来一个结果，不等于线程。
- Mono：Reactor中0或1个信号。
- Flux：0到多个信号。
- 当前单Node用Future交付一次结果，Graph内部可用Mono处理，整个执行用Flux发布多个NodeOutput。

### 13. SSE是不是模型Token流？

不是。当前SSE发送Graph节点完成事件；节点内部使用`call()`取得完整模型结果。实现Token流还需要在回答节点内部使用stream并重新设计State、事件和错误语义。

### 14. 当前route字段有什么设计问题？

它同时表示业务意图和流程下一站。KnowledgeNode会把`knowledge_base`覆盖成`ai_reply`。应拆为`intentRoute`和`nextAction`，或业务节点通过固定边进入回答节点。

## 三、路由

### 15. 融合路由怎么实现？

- 六类规则打分。
- LLM独立输出route/confidence/reason。
- 当前使用Code 0.4、LLM 0.6融合。
- 选择最高分并写入State。

### 16. 为什么不完全交给LLM？

- 明确订单号、转人工、投诉等规则更确定、低成本、可解释。
- LLM负责口语、长句和上下文语义。
- 程序必须保留白名单与业务边界。

### 17. 0.4/0.6权重怎么来的？

诚实回答：

> 当前是初始固定权重，没有可信消融实验能证明最优。下一步应冻结标签本体，分别评测规则Only、LLM Only及多个权重组合，并进行置信度校准。

### 18. 当前置信度有什么Bug？

Prompt要求百分制，但代码可能把confidence再次乘100；模型返回90时会变成9000并压倒规则，最终被截断为1.0。应统一0~1 Schema并严格校验。

### 19. LLM返回非法JSON怎么办？

当前使用正则解析并回退规则，仍不够稳健。生产方案：结构化DTO/JSON Schema、类型与白名单校验、有限修正重试；Provider异常与格式错误分别处理。

### 20. 低置信度和多意图怎么处理？

当前总是argmax。改进应增加Top1阈值、Top1/Top2 margin；低把握进入clarify，多意图进入拆解或保留多个候选路由。

## 四、RAG

### 21. 完整RAG链路是什么？

```text
规则清洗
→ 历史感知的LLM改写/多问题拆解
→ 向量Top5 + BM25 Top5
→ RRF融合
→ 最多Top10
→ qwen-max生成回答
```

### 22. 为什么向量检索与BM25同时使用？

- 向量：同义、口语、语义近似。
- BM25：型号、数字、专有名词和精确词。
- 二者互补。

### 23. 为什么用RRF而不是分数相加？

向量相似度与BM25分数不在同一量纲。RRF只依赖排名，通过`1/(K+rank)`融合，避免直接归一化不同分数。

### 24. 当前有没有Rerank？

没有。当前只有RRF。不能描述为Cross-encoder精排；可作为后续候选集精排方案。

### 25. 多Query融合有什么问题？

所有子查询向同一RRF Map累加，通用文档可能因多次命中而挤掉只回答某个子问题的证据。应先保证每个子问题的证据配额，再全局去重和控制Token。

### 26. 检索不到答案怎么办？

当前缺少证据门控，模型仍可能回答。正确方案是检测空召回/低相关/必要事实缺失，重新检索、澄清或拒答，不允许脱离证据自由补全。

### 27. 向量缓存如何管理？

当前首次构建后保存本地JSON，后续直接加载；知识更新依赖人工删除缓存。生产应绑定文档Hash、切块版本、Embedding模型和维度，离线构建并原子发布索引版本。

### 28. tenantId为什么重要？

当前它只进入State，没有参与检索过滤。生产多租户必须从认证上下文取得可信tenantId，并在文档入库、向量、BM25、Checkpoint和工具全链路强制隔离。

### 29. 一次RAG请求调用几次模型？

通常路由1次、改写1次、回答1次；超过摘要阈值时再增加1次。需要跳过无必要改写、模型分层、缓存和Token预算优化成本与延迟。

## 五、记忆、Checkpoint与并发

### 30. 当前记忆怎么实现？

- recentHistory保存最近约3轮原文。
- historySummary保存较早对话摘要。
- AiReplyNode更新，路由/改写/回答读取。
- 字段随Checkpoint按threadId持久化。

### 31. ChatMemory、State、Checkpoint和聊天档案有什么区别？

- Memory：给模型看的上下文。
- State：程序运行数据。
- Checkpoint：State与执行位置快照。
- 档案：完整、append-only、可审计原始消息。

### 32. 为什么固定3轮不够好？

轮数不代表Token量；摘要有损且可能漂移。应使用结构化Message、Token预算、关键任务字段独立保存，并保留完整原始档案。

### 33. 为什么threadId不能只用sessionId？

不同用户可提交相同sessionId并指向同一Checkpoint，造成历史串话和状态覆盖。当前使用userId+sessionId复合Key；生产还需从认证上下文取得可信身份。

### 34. Semaphore解决什么问题？

避免同会话A、B同时读取Checkpoint v1并互相覆盖更新。相同SessionKey映射到同一许可，按顺序执行。

### 35. 为什么用256个分片？

使锁对象数量有界；碰撞会让无关会话暂时串行但不破坏正确性。256是经验值，需要压测，并非理论最优。

### 36. 为什么Semaphore而不是ReentrantLock？

响应式清理可能跨线程。Semaphore无线程所有权，可以由完成、异常或取消清理路径释放；必须严防重复或遗漏release。

### 37. 多实例如何保证同会话一致性？

本地Semaphore无效。采用共享Checkpoint version CAS、按threadId分区调度、必要的分布式锁；sticky session只能优化，不能替代一致性。

## 六、工具、可靠性与安全

### 38. 当前真的实现工具调用了吗？

没有。BusinessToolNode和ExternalSearchNode是占位。只能说预留Graph分支。

### 39. 如何补齐一个真实工具闭环？

```text
选择工具
→ 白名单
→ Schema
→ 认证/权限
→ 确认/幂等
→ 执行
→ ToolOutcome
→ answer/clarify/retry/fallback/human
```

### 40. 本地ToolCallback和MCP如何选择？

- 同一Java应用、简单低延迟：先用本地ToolCallback。
- 独立服务、跨客户端共享和统一发现：考虑MCP。
- 当前先做只读订单查询闭环，再抽象MCP更合理。

### 41. 哪些错误可以重试？

- 超时、429、502、503等瞬时错误有限重试。
- 参数、权限、配置和缺失用户信息不能原样重试。
- 写工具必须幂等后才能重试。

### 42. 重试、降级、熔断、隔离舱有什么区别？

- 重试：当前请求再尝试。
- 降级：改用较弱方案。
- 熔断：依赖持续故障时暂停调用。
- 隔离舱：限制某依赖占用的资源。

### 43. 当前最合理的局部降级是什么？

```text
路由LLM→规则
改写LLM→原问题
向量→BM25-only
BM25→vector-only
两路失败→拒答/人工
摘要失败→主回答继续成功
```

### 44. 当前主要Prompt Injection风险是什么？

系统指令、用户输入、历史和证据全部拼入UserMessage；知识文档也可能包含恶意指令。应分System/User/Context，外部内容视为不可信，同时由程序控制工具权限和输出边界。

### 45. Base64 threadId安全吗？

只保证编码和路径友好，不是加密、匿名化或认证。必须校验用户与session归属，Checkpoint还需要加密和生命周期治理。

## 七、评测、观测与部署

### 46. 现在可以说路由准确率92.5%吗？

不可以。当前没有对应当前代码的可信报告。旧52条报告严格准确率32.7%、宽松59.6%，且标签本体冲突；最新API变化后旧脚本也缺少userId。

### 47. 73.2%的RAG召回代表什么？

它是最终回答中价格/库存/参数/政策类别关键词覆盖，不是基于gold chunk的Recall@K。

### 48. 如何建立正确RAG评测？

- 每个query标gold chunk IDs和必要事实。
- 检索层算Recall@K、MRR、NDCG。
- 生成层算Faithfulness、完整性和引用准确率。
- Vector/BM25/Hybrid/Rerank消融。
- 绑定commit、模型、Prompt、KB和Embedding版本。

### 49. Agent可观测性要看什么？

- HTTP、Graph、Node、模型、检索、Checkpoint、工具Span。
- 延迟、错误率、Token、成本、空召回、降级、会话等待。
- 质量信号：用户反馈、正确拒答、Groundedness、转人工。
- userId/sessionId不能作为Prometheus高基数label。

### 50. 当前如何部署，为什么不能多实例？

当前本地向量JSON、内存BM25、FileSystemSaver和JVM Semaphore都不共享。生产需应用无状态化、共享Checkpoint+CAS、检索服务化、认证限流、Secret、可观测性及CI/CD。

## 八、二面场景题

### 51. 模型服务延迟突然升高怎么办？

- 分解路由/改写/回答模型Span定位。
- 超时、并发隔离和费用预算。
- 路由回退规则、改写回退原问题、回答模板降级。
- 熔断后控制恢复探测。

### 52. 用户连续点击发送导致回答串乱怎么办？

- 同threadId串行、请求序号和幂等。
- 明确排队、取消上一请求或拒绝重复请求的产品语义。
- 多实例用CAS/分区调度，不能只依赖JVM Semaphore。

### 53. 知识库更新后回答变差怎么办？

- 对比kbVersion、chunk版本和Embedding版本。
- 离线Recall/Faithfulness门禁。
- Canary索引、Alias切换、保留上一版本回滚。
- 分析新增Chunk是否造成召回竞争或Prompt Injection。

### 54. 支付工具超时，不知道是否扣款怎么办？

- 不直接重复支付。
- 使用idempotencyKey查询业务状态。
- 若状态不确定则暂停、人工或补偿流程。
- Checkpoint记录调用ID与观察结果，但不代替支付系统幂等。

### 55. 如何降低成本？

- 规则跳过不必要模型调用。
- 路由/改写使用更小模型。
- 缓存稳定结果、减少上下文和最终候选文档。
- 摘要按Token阈值触发。
- 监控单请求Token与tenant预算，先评测再优化。

### 56. 如果面试官指出当前指标很差怎么办？

推荐回答：

> 我同意现有报告不能作为当前版本成果。复盘发现接口、标签本体和指标口径都发生过变化，因此我选择撤掉宣传数字，先固定版本和gold数据，分层重建评测。这比继续调Prompt追求一个不可复现的高分更重要。

## 九、当前最值得准备的三条项目故事

### 故事A：从单一问答拆成Graph

- 问题：客服请求类型不同且需要跨轮状态。
- 方案：Node/Edge/State/Checkpoint解耦业务与调度。
- 取舍：复杂度增加，工具分支仍占位。
- 反思：route职责混杂，应拆intent与next action。

### 故事B：混合RAG

- 问题：语义问法与型号/数字检索各有短板。
- 方案：向量+BM25+RRF，加入历史改写和多意图拆解。
- 取舍：当前无Rerank、最终最多Top10，多query覆盖不均。
- 反思：需要gold chunk、证据门控、引用和版本化索引。

### 故事C：会话隔离与并发

- 问题：sessionId碰撞与同会话并发丢失更新。
- 方案：userId+sessionId复合threadId、FileSystemSaver、256分片Semaphore、usingWhen释放。
- 取舍：碰撞牺牲并发，且只支持单JVM。
- 反思：生产需可信身份、共享Checkpoint、CAS/分区调度和隐私治理。

## 十、禁止使用的表述

- “已实现MCP业务工具、订单/物流/库存实时查询。”
- “使用Neo4j知识图谱。”
- “使用Cross-encoder Rerank。”
- “实现Token级SSE流式输出。”
- “路由准确率92.5%、RAG召回91.8%、编造率0.3%。”
- “220+测试用例、18+维度均达到90%。”
- “生产级高并发、多实例、熔断降级已经落地。”
- “完整自主规划Agent。”

## 十一、模拟面试顺序

第一轮建议依次训练：

```text
1分钟项目介绍
→ 为什么Graph
→ 完整请求链路
→ 路由融合
→ 混合RAG
→ 记忆与Checkpoint
→ 会话并发
→ 工具真实边界
→ 指标可信度
→ 最大不足与下一步
```

每题控制在60~120秒。面试官追问时再进入源码和公式，不在开场堆满所有细节。
