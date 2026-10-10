# 客服系统 Agent 会话隔离学习记录

## 学习目标

在现有 Spring AI Alibaba Graph 客服系统中实现一套可解释、可验证的会话机制：

1. 同一用户、同一会话可以延续上下文。
2. 同一用户的不同会话互相隔离。
3. 不同用户即使使用相同 `sessionId` 也不会串话。
4. 服务重启后能够从检查点恢复会话。
5. 同一会话并发请求不会相互覆盖。
6. 能够从源码、工程设计和面试三个角度解释实现。

## 当前项目事实盘点

### 已有能力

- `ChatController` 接收 `sessionId`，并将其传给 `RunnableConfig.threadId(...)`。
- 前端会为新会话生成随机 `sessionId`。
- Graph 状态中已经定义 `messages`、`historySummary`、`recentHistory`、`turnIndex` 等上下文字段。
- 项目已经通过 `Flux` 和 `text/event-stream` 提供流式接口。
- `SessionService` 使用 `ConcurrentHashMap` 保存少量会话元数据。

### 当前不足

- 请求模型中没有 `userId`，Graph 目前只按 `sessionId` 寻址。
- 如果不同用户提交相同 `sessionId`，可能读取同一个 Graph 状态。
- `SessionService` 没有接入 `ChatController` 主调用链，且仅保存在JVM内存中。
- Graph 使用 `compile()`，尚未显式配置 checkpoint saver，因此没有建立明确的重启恢复机制。
- 暂未看到针对同一会话并发写入的业务级保护。
- Controller 使用 `Map<String, Object>` 接收请求，缺少编译期类型检查和参数校验。

## 目标调用链

```text
HTTP请求
  -> ChatRequest（userId、sessionId、message...）
  -> SessionKey（userId + sessionId）
  -> 生成内部threadId
  -> RunnableConfig.threadId(threadId)
  -> Graph加载该threadId的checkpoint
  -> 执行路由、RAG、工具和回复节点
  -> 保存新的checkpoint
  -> Flux<NodeOutput>
  -> HTTP JSON或SSE响应
```

## 为什么不直接引入 AgentScope Harness

当前客服系统的主编排框架是 Spring AI Alibaba Graph，它自身已经提供 `RunnableConfig`、Graph state 和 checkpoint saver。如果再让 AgentScope Harness 管理同一份会话状态，会出现两个状态生命周期：

```text
AgentScope AgentState
Spring AI Alibaba Graph Checkpoint
```

这会带来双写、一致性和恢复顺序问题。本阶段学习 Harness 的工程思想，但使用当前框架提供的原生扩展点完成实现。以后若整体迁移到 AgentScope，再统一替换运行时。

## 分阶段实施

### 阶段1：请求身份建模（进行中）

- [x] 给 `ChatRequest` 增加 `userId`。
- [x] 为 `userId`、`sessionId`、`message`增加 `@NotBlank`约束。
- [x] Maven编译验证通过（已补齐 `jakarta.validation.constraints.NotBlank` import）。
- [x] 建立不可变 `SessionKey(userId, sessionId)`并提供统一的 `threadId()`映射。
- [x] 使用类型化DTO替换Controller中的裸 `Map` 入参。
- [x] 对外保留原始身份，对内生成不会冲突的 Graph `threadId`。
- [x] 普通接口和流式接口统一使用复合 `threadId`。
- [x] 在Graph状态策略中注册 `userId`。
- [ ] 前端请求补充 `userId`，避免触发参数校验失败。

相关 Java 知识：

- `record`与不可变对象。
- `equals()`和`hashCode()`。
- Bean Validation。
- 类型安全与泛型Map的缺陷。

相关 Agent 知识：

- 用户身份和会话身份是两个维度。
- `sessionId`不能被当作全局用户身份。
- 会话标识用于状态路由，不应默认写入Prompt发送给模型。

### 阶段2：Graph状态持久化（待进行）

- [x] 使用 Spring AI Alibaba Graph 的 `FileSystemSaver`建立本地检查点骨架。
- [x] 通过 `SaverConfig`和 `CompileConfig`将 saver 注入编译后的Graph。
- [x] 使用 `assistant.memory.checkpoint-directory`外部化存储目录配置。
- [x] 将本地检查点目录加入 `.gitignore`。
- [ ] 检查落盘结构和重启恢复行为（集中测试阶段完成）。

相关 Java 知识：接口与策略模式、序列化、NIO、原子文件操作。

当前版本的 `FileSystemSaver`将检查点序列化到 `thread-<threadId>.saver`文件，适合本地学习和单实例运行。它不是生产级分布式状态存储；多实例部署应替换为Redis、MySQL等共享实现，并补充分布式并发控制。

### 阶段3：同会话并发控制（待进行）

- [x] 增加 `SessionExecutionGate`，对映射到同一分片的会话串行执行。
- [x] 使用固定256个 `Semaphore`分片，使锁结构的内存占用有界。
- [x] 使用 `Flux.usingWhen`在完成、异常和取消时释放许可。
- [x] 在 `boundedElastic`调度器上等待许可，避免直接阻塞响应式处理线程。
- [ ] 集中测试同会话并发执行行为。

相关 Java 知识：`ConcurrentHashMap`、`computeIfAbsent()`、锁粒度、`finally`、竞态条件。

分片锁的取舍：相同 `SessionKey`一定落入同一分片；不同Key发生哈希碰撞时也可能暂时串行，这会牺牲少量并发度，但不会破坏正确性，也避免按会话永久保存锁造成内存增长。该机制仅在当前JVM进程内有效。

### SessionExecutionGate并发八股

- 它防止的是同一会话并发读写，不是禁止同一会话连续多次请求；后续请求会排队执行。
- 典型问题是丢失更新：A和B都读取状态v1，分别保存时后写者可能覆盖先写者。串行后变为A读取v1并保存v2，B再读取v2并保存v3。
- `Semaphore(1)`通过单个许可实现互斥效果，但它没有线程所有权，也不是可重入锁；任意线程都可以 `release`。
- `Semaphore`基于AQS同步机制；许可不足时等待线程入队并被挂起，`release`后再唤醒竞争许可。
- `release`必须与成功的 `acquire`严格配对。重复释放会使许可数大于1，从而破坏互斥；漏释放会让后续请求永久等待。
- 公平模式 `new Semaphore(1, true)`倾向按等待顺序分配许可，降低饥饿风险，但吞吐量通常低于非公平模式。
- 固定分片属于锁粒度与内存的折中：分片少则碰撞多，分片多则占用更多对象。
- `usingWhen`保证完成、异常、取消三个终止路径释放许可；Graph的全部异步工作必须包含在返回的Flux生命周期内，否则流提前结束会导致许可提前释放。
- 该Gate是Spring单例，才能协调当前JVM中的所有请求；多实例部署需要分布式锁、状态版本CAS或按会话分区调度。

### 阶段4：验证和工程完善（待进行）

- 验证同用户同会话连续性。
- 验证跨会话和跨用户隔离。
- 验证服务重启恢复。
- 验证同会话并发请求。
- 增加日志字段，但不记录敏感对话全文。

### 公开事件边界

- [x] 增加 `ChatEvent` DTO。
- [x] 普通接口和SSE接口复用同一个 `executeGraph()`调用链。
- [x] 仅公开 `route`、`confidence`、`decisionReason`、`status`、`answer`等允许字段。
- [x] 不再直接把完整 `event.state().data()`暴露给前端。

这样做的原因是Graph内部状态可能逐步加入历史消息、检索原文、工具参数和内部路由信息。内部状态对象不应直接成为外部API协议。

## 当前会话记忆事实盘点

### 已实际形成并被使用的记忆

- `recentHistory`：`AiReplyNode`写入用户问题和助手回复，保留最近约3轮原文；后续 `AiReplyNode`、`FaqNode`和`KnowledgeNode`会读取。
- `historySummary`：超过3轮时，较早一轮移入摘要并由 `HistoryManager`调用模型压缩；后续节点会读取。
- `turnIndex`：`AiReplyNode`每完成一轮加1，用于触发历史压缩。
- `lastRoute`：`SemanticRouteNode`写入上一轮路由，后续路由判断会读取。

### 已持久化但当前未形成有效对话记忆的字段

- `messages`：Controller只追加用户消息；当前没有节点读取该字段，也没有把助手回复追加进去，因此它目前是冗余且不完整的消息记录。
- `answer`、`route`、`confidence`、`status`等会进入Graph状态，但主要表示当前/最近一次执行结果，不等同于完整对话历史。

### 仅预留、尚未真正写入的字段

- `lastNeedClarify`和`pendingQuestion`已注册策略且会被路由读取，但当前节点没有写入它们，所以通常只能得到默认值。

### 当前缺口

- `clarify`和`human_handoff`分支直接到 `END`，不会经过 `AiReplyNode`，因此这些轮次目前不会更新 `recentHistory`、`historySummary`和 `turnIndex`。
- `messages`使用 `AppendStrategy(false)`会按 `equals`去重，相同文本的两次真实用户输入可能只保留一次；聊天流水通常应允许重复。

## AgentScope Harness记忆设计对照

### 1. 会话工作记忆

- `AgentState.context`是权威消息序列，类型为 `List<Msg>`。
- 用户消息、助手推理消息、工具调用和工具结果都会按顺序追加。
- system消息在模型调用前临时前置，不污染会话消息序列。
- `AgentStateStore`按 `(userId, sessionId)`加载和保存完整 `AgentState`。

### 2. 上下文压缩

- Harness按消息数或估算Token数触发，而不是固定轮数。
- 压缩前可裁剪过大的工具参数、清理冗长工具结果，并保证不拆开 assistant工具调用和tool结果。
- 较早消息由LLM生成结构化摘要，模型上下文重建为“摘要消息＋保留的最近消息”。

### 3. 完整会话档案

- LLM可见上下文允许压缩，但原始会话可写入append-only JSONL档案。
- Harness采用双文件思路：一个是压缩后的模型上下文，一个是从不压缩的完整日志，兼顾Token成本与审计/恢复。

### 4. 跨会话长期记忆

- Harness可从会话窗口提取稳定事实到每日memory ledger，再周期性汇总进有大小限制的 `MEMORY.md`。
- 这与单个session的聊天历史不同，适合用户偏好、长期任务事实等跨会话信息。
- AgentScope core 2.0已将旧 `LongTermMemory`接口标记为弃用，建议跨会话记忆在应用层集成；Harness仍提供自己的文件化memory pipeline。

### 5. 非对话运行状态

- 同一个 `AgentState`还保存权限、工具、任务、计划模式、迭代和中断恢复状态，使Agent可以从工具等待或人工确认点继续。

### 对客服项目的启示

- 用一个结构化消息列表作为最近对话的权威来源，避免 `messages`与`recentHistory`双写。
- 同时保存user、assistant以及未来的tool消息，所有终止分支统一写入历史。
- 保留摘要，但后续改为按Token/消息阈值触发并保留最近尾部。
- 将Graph运行字段与对话消息分开；需要审计时另建append-only完整日志。
- 跨会话用户画像属于后续长期记忆层，不应混入当前session checkpoint。

## 记忆与状态的边界

- 记忆回答“模型为了理解上下文需要知道什么”：最近结构化消息、早期摘要、检索到的长期用户事实。
- 状态回答“系统当前确定地执行到哪里、接下来必须做什么”：当前/下一节点、等待中的工具调用、人工确认、补问字段、任务进度、版本号等。
- 记忆主要进入模型上下文；状态主要由程序判断，不应让LLM根据聊天文本猜测。
- 简单的一问一答FAQ可以只使用消息历史；具备工具、审批、中断恢复和多步流程的Agent必须保存状态。
- 状态应最小化。Semaphore、线程、网络连接和缓存属于运行时资源，不应持久化；路由分数、trace等诊断信息更适合日志/观测系统。
- 当前Graph状态需要后续拆分为：请求上下文、会话记忆、流程状态和诊断数据，避免所有字段长期混在Checkpoint中。

## 第一阶段核心问题

### 为什么Key必须包含userId和sessionId

只使用 `sessionId` 时：

```text
user-A/session-1 -> threadId=session-1
user-B/session-1 -> threadId=session-1
```

两次请求会指向相同状态。

使用复合身份后：

```text
user-A/session-1 -> 独立状态A1
user-B/session-1 -> 独立状态B1
```

`userId`回答“属于谁”，`sessionId`回答“这个用户的哪段对话”。

### 为什么使用不可变SessionKey

状态Key一旦放入哈希结构，其 `equals()`和`hashCode()`所依赖的字段就不应变化。否则对象仍在原来的哈希桶中，但使用新字段计算后会去另一个桶查找，导致状态无法取回。Java `record`天然适合表达这种只承载数据、按值比较、创建后不修改的复合Key。

## 面试表达（持续更新）

> 客服Agent以 `userId + sessionId`作为会话复合身份。请求进入后先映射为框架内部的状态线程标识，再由Graph checkpoint加载对应上下文。用户身份、会话身份与模型Prompt分离，避免仅使用客户端提供的sessionId造成跨用户状态冲突。后续对同一复合Key实施串行控制，并使用可替换的checkpoint saver实现重启恢复。

## Graph事件与响应式流八股

### 当前项目中的事件

- Graph每完成一个节点会产生一个 `NodeOutput`，它表示节点名称和执行后的Graph状态。
- `customerSupportGraph.stream(...)`返回 `Flux<NodeOutput>`。
- `/chat/ask`通过 `collectList().block()`收集全部节点事件后一次返回JSON。
- `/chat/ask/stream`直接返回 `Flux`，由Spring通过SSE逐个推送节点事件。
- 当前是节点级事件流，不等同于大模型Token级流式输出。

### 三种信号

Reactive Streams中一次流包含：`onNext`数据、`onError`错误、`onComplete`完成。错误信号之后不会再正常继续发送数据。

### 常见概念区分

- 观察者模式：一个主题发生变化时通知多个观察者，通常在同一进程中。
- 发布订阅：发布者与订阅者通过事件通道解耦，双方通常不直接持有彼此。
- 消息队列：跨进程事件基础设施，可进一步提供持久化、重试、确认和消费组。
- SSE：HTTP网络输出方式，适合服务端单向持续推送；它不是事件存储系统。

### Reactor重点

- `Flux<T>`：0到多个元素；`Mono<T>`：0或1个元素。
- `map`：同步一对一转换；`flatMap`：展开异步结果，可能改变顺序；需要保持顺序时关注 `concatMap`。
- `collectList`：`Flux<T>`转换为`Mono<List<T>>`。
- `block`：阻塞当前线程等待结果，不应在完整响应式链中滥用。
- 背压：消费者处理速度低于生产速度时的流量协调机制。
- 取消：SSE客户端断开后应向下游传播取消，避免模型和工具继续消耗资源。

### Mono与Flux原理

- `Mono<T>`描述未来的0或1个结果；当前 `Mono<Semaphore>`表示未来成功获得一个执行许可，或者以错误/空结果结束。
- `Flux<T>`描述未来随时间产生的0到多个结果；当前 `Flux<ChatEvent>`表示Graph运行过程中连续产生多个节点事件。
- 它们不是已经装好数据的集合，而是包含订阅、数据、错误、完成和取消语义的异步执行描述。
- Reactor流通常是惰性的：构建操作链不会立即执行，订阅后才沿调用链请求数据。重复订阅冷流可能重复调用模型或工具。
- 信号包括 `onNext`、`onError`和`onComplete`；错误和完成都是终止信号。
- `List<T>`保存当前已有的多个值；`Flux<T>`可以让值在未来分批到达。Java `Stream<T>`主要用于同步集合计算，`Flux<T>`还处理异步、取消、错误和背压。
- `CompletableFuture<T>`更接近一个异步单结果；`Mono<T>`同样表达单结果，但与Reactive Streams的组合、取消和流式操作更统一。
- `subscribe`触发并消费响应式流；`block`把响应式结果桥接回同步世界，会阻塞当前线程，应谨慎使用。

### 生产注意事项

- 事件需要明确类型、会话标识、时间、序号和可公开的payload。
- 不应直接把完整Graph状态暴露给前端，避免泄露Prompt、历史消息、工具参数和内部路由分数。
- 跨进程事件需要考虑至少一次投递导致的重复消费，因此消费者应具备幂等性。
- 并行处理可能破坏事件顺序；需要按会话维度排序时，应使用序号或串行处理。

## 学习进度

- [x] 分析AgentScope会话隔离原理。
- [x] 盘点客服项目当前会话实现。
- [x] 确定使用Spring AI Alibaba Graph原生checkpoint机制。
- [x] 完成 `ChatRequest` 的 `userId`建模和字段校验声明。
- [x] 完成请求身份建模。
- [x] 完成本地检查点持久化骨架。
- [x] 完成同会话并发控制骨架。
- [ ] 完成隔离、恢复和并发测试。

## Tool阶段：非法JSON与参数校验

### 先区分两种JSON

- 普通文本JSON：提示模型在回答正文中输出JSON，约束较弱，容易出现代码围栏、漏引号或额外说明文字。
- 原生Tool Calling：把工具名和参数JSON Schema传给模型服务，由模型返回结构化的工具调用；Agent工程应优先采用这一方式。

### AgentScope的防线

1. `ToolSchema`声明工具名、用途、参数结构和可选的`strict`模式，帮助兼容的模型服务按Schema生成参数。
2. 模型适配器把服务商返回的工具调用转换成`ToolUseBlock`，同时保留原始参数字符串`content`。
3. `ToolExecutor`在真正执行工具之前调用`ToolValidator.validateInput(...)`。
4. `ToolValidator`先解析JSON，再使用JSON Schema校验必填字段、类型、枚举、数值范围、字符串长度、正则、嵌套对象和数组。
5. JSON无法解析或不符合Schema时，不调用工具，而是返回`ToolResultBlock.error(...)`。
6. 错误结果加入Agent上下文，ReAct进入下一轮推理，模型能够依据错误信息修正参数。
7. `maxIters`限制最大推理—执行轮数，避免模型持续输出错误参数造成无限重试。

### 不能误解的“保证”

`strict=true`只表示兼容的模型Provider会加强Schema约束，并不应被视为绝对安全保证。因此服务端执行前校验不能省略。JSON Schema只验证数据结构；订单是否存在、金额是否合法、用户是否有权限等业务规则，仍必须由Java业务层检查。

### 面试表达

> 工具调用不能依赖Prompt保证参数正确。我会优先使用模型原生Tool Calling和JSON Schema，并在服务端执行前再次完成反序列化、Schema校验和业务校验。参数非法时禁止执行工具，将结构化错误作为Tool Result写回上下文，让模型在有界ReAct循环中纠正；高风险写操作还需要权限校验、幂等和人工确认。

### 对应Java八股

- Jackson反序列化：JSON字符串转Java对象或`Map<String,Object>`，语法非法时抛异常。
- Bean Validation与JSON Schema：前者常校验Java DTO，后者用于描述并校验跨语言的工具协议。
- 防御式编程：不信任模型输出，所有外部输入都在边界处验证。
- 异常降级：把可预期的参数错误转成领域错误结果，不让异常直接击穿整个Agent流程。
- 幂等性：重试工具调用时要防止订单、支付、发消息等副作用重复发生。

- [x] 进入Tool阶段并理解非法JSON的多层兜底。

## 大模型调用工具的完整链路

大模型本身不会直接调用Java方法。它只负责产生一条结构化的“调用建议”，真正的查找、校验和执行由Agent Harness完成。

```text
Java工具注册
→ 生成工具名、描述和参数Schema
→ 消息历史与工具Schema一起发送给模型
→ 模型选择直接回答或返回tool_call(id/name/arguments)
→ Provider适配器解析为框架内部ToolCall对象
→ Harness路由到工具执行节点
→ 工具名解析、权限、参数和业务校验
→ Java ToolCallback/AgentTool执行
→ 结果封装为带相同callId的Tool Result消息
→ 写入上下文并再次调用模型
→ 模型基于工具结果继续调用工具或形成最终答案
```

`callId`用于把一次Tool Call与对应Tool Result配对；`name`决定调用哪个注册工具；`arguments`是模型生成的JSON参数。模型与Java工具之间不存在直接方法调用，Harness是控制平面。

## AgentScope与Spring AI Alibaba兜底对照

### AgentScope

- `ToolSchema`把工具定义交给模型，兼容Provider可以使用`strict`加强生成约束。
- Provider适配器生成`ToolUseBlock`并保留原始参数JSON。
- `ToolExecutor`执行前用`ToolValidator`进行JSON语法和完整JSON Schema校验。
- 工具不存在、未激活、参数非法、工具异常或空结果都转成`ToolResultBlock.error`，不会直接执行非法调用。
- 错误结果写入上下文，下一轮ReAct让模型纠正；`maxIters`限制最大循环次数。
- 权限规则和HITL可以在有副作用的工具执行前暂停或拒绝调用。

### Spring AI Alibaba Agent Framework（本机源码版本）

- Spring AI的`ToolDefinition.inputSchema`将Java方法参数Schema交给模型。
- 模型响应被表示为`AssistantMessage.ToolCall`；Graph根据`hasToolCalls()`路由到`AgentToolNode`。
- Agent Framework关闭Spring AI内部自动执行，由Graph的工具节点统一控制调用和状态更新。
- `MethodToolCallback`用Jackson体系把arguments JSON解析成Map，再转换成Java方法参数；JSON错误或类型转换错误会包装成`ToolExecutionException`。
- 默认异常处理器可将`ToolExecutionException`转换为工具结果；其他同步、异步、超时、取消和空结果问题会转成`ToolCallResponse.error`。
- 所有结果组合成`ToolResponseMessage`，写入messages后重新路由到模型。
- 与AgentScope不同，当前`AgentToolNode`没有显示出一层独立的完整JSON Schema服务端校验；应在ToolInterceptor或工具业务入口补充DTO/Schema、权限和业务校验。
- 当前源码的默认`recursionLimit`是`Integer.MAX_VALUE`，生产项目应显式配置有限循环上限，避免错误调用反复自修复形成高成本循环。

### 两者共同不能替代的防线

- Schema只解决结构问题，不能验证订单归属、库存、余额等业务语义。
- 对写操作增加鉴权、授权、参数白名单和HITL确认。
- 对下单、退款、发消息等操作使用幂等键，防止模型或网络重试产生重复副作用。
- 设置工具超时、并发上限、Agent循环上限和可观察日志。

## Tool源码学习第一课：Java方法如何成为模型工具

Spring AI的核心转换链路：

```text
带@Tool的方法
→ ToolCallbacks.from(工具对象)反射扫描
→ MethodToolCallback
→ ToolDefinition(name/description/inputSchema)
→ 注册到Agent
→ ToolDefinition发送给模型
```

- `@Tool`只声明工具名称、用途、是否直接返回等元数据，本身不会执行方法。
- `@ToolParam`声明参数用途和是否必填，并参与输入JSON Schema生成。
- `ToolCallbacks.from(object)`需要具体对象，是因为非静态工具方法最终要在该对象实例上反射调用。
- `ToolCallback`统一不同工具的执行接口，核心形式是`call(String arguments, ToolContext context)`。
- `ToolDefinition`是模型能看到的契约，只包含`name`、`description`和`inputSchema`，模型看不到Java源码和Service实现。
- `@Component`只让对象进入Spring容器，不等于自动暴露给模型；仍需把生成的`ToolCallback`注册进Agent。

工程原则：工具参数应尽量使用明确DTO、枚举和范围约束；身份、权限和租户信息从可信`ToolContext`注入，不让模型生成；避免把宽泛的`Map<String,Object>`或万能执行方法直接暴露为工具。
