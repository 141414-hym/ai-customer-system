# Spring AI与Spring AI Alibaba面试学习记录

## 学习规则

- 一次只学习一个问题。
- 每题按照：定义、产生原因、核心链路、面试表达、易错点整理。
- 先学习Spring AI基础，再学习Spring AI Alibaba的Graph与Agent Framework。
- 每题讲解后设置3～5个递进检查题，覆盖概念辨析、代码链路和面试场景；完成批改后记录掌握情况，再进入下一题。

## 问题1：Spring AI解决了什么问题？

### 定义

Spring AI是面向Java/Spring应用的AI应用开发抽象层。它不负责训练大模型，而是把不同模型厂商的调用协议和AI应用中的通用能力封装成较统一的Java接口。

### 为什么需要

如果直接对接不同模型厂商，应用需要分别处理不同的HTTP地址、鉴权方式、请求/响应JSON、流式协议、工具调用格式和异常。业务代码会与具体厂商强耦合，切换模型时需要大量修改。

Spring AI在业务代码和模型Provider之间增加抽象层：

```text
业务代码
→ Spring AI统一抽象
→ DashScope/OpenAI/DeepSeek等Provider适配器
→ 模型服务
```

### 主要统一能力

- `ChatModel`：统一底层聊天模型调用。
- `ChatClient`：面向应用的高级调用入口。
- `Prompt`、`Message`、`ChatResponse`：统一消息和响应模型。
- `ToolCallback`：统一工具定义和执行接口。
- `ChatMemory`：统一对话记忆抽象。
- `EmbeddingModel`、`VectorStore`：统一Embedding和向量检索。
- `Advisor`：以责任链方式组合Memory、RAG、Tool Calling、日志等能力。
- Observability：接入Spring/Micrometer指标和链路追踪。

### Spring AI不负责什么

- 不训练基础大模型。
- 不保证模型回答正确。
- 不自动完成企业业务权限和业务规则校验。
- 不等于完整Agent系统；是否形成Agent取决于是否建立模型决策、工具执行、状态和循环控制。

### 面试表达

> Spring AI解决的是Java AI应用与不同模型Provider之间的适配和通用能力复用问题。它通过ChatModel、ChatClient、ToolCallback、ChatMemory、VectorStore和Advisor等抽象，统一模型调用、工具、记忆、RAG与可观测性，使业务代码减少对特定厂商协议的依赖。但它不是大模型本身，也不能替代业务层的权限、校验和Agent执行边界设计。

### 易错点

- “统一接口”不代表所有Provider能力完全一致，厂商特有功能仍可能需要专用Options或适配器。
- 更换模型通常能减少业务代码修改，但不保证零修改；Prompt效果、工具协议、上下文窗口和流式行为可能不同。
- 调用Spring AI SDK不等于已经实现Agent。

### 状态

- [x] 已讲解并记录

## 补充问题：DashScope是什么？

### 定义

DashScope（阿里云百炼模型服务）是阿里云提供的大模型云服务平台和API入口。它负责把通义千问等模型以网络服务的形式提供给应用，而不是大模型算法本身。

### 四个概念的区别

```text
Spring AI Alibaba      Java开发框架/适配层
DashScopeChatModel     框架中访问DashScope的Java实现类
DashScope              远程模型服务平台和API入口
qwen-max               平台上被调用的具体大模型
```

类比普通后端开发：

```text
业务代码                → AiReplyNode
HTTP/SDK客户端          → ChatClient + DashScopeChatModel
云服务平台              → DashScope
平台中的具体服务资源     → qwen-max
```

### 当前项目中的作用

项目配置：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY:}
      chat:
        options:
          model: qwen-max
```

- `api-key`：证明应用有权访问DashScope。
- `model: qwen-max`：告诉DashScope本次请求使用哪个模型。
- `DashScopeChatModel`：把Spring AI的Prompt转换为DashScope协议，并把返回结果转换回Spring AI对象。

### 请求链路

```text
Java业务代码
→ Spring AI ChatClient
→ DashScopeChatModel
→ HTTPS请求到DashScope
→ DashScope鉴权、路由到qwen-max并进行计量
→ qwen-max推理
→ DashScope返回响应
→ Java对象
```

DashScope通常还承担模型选择、API鉴权、请求路由、限流、用量统计和错误码等云服务职责。因此应用不是把qwen-max模型下载到本机运行，而是通过网络调用平台托管的模型。

### 面试表达

> DashScope是阿里云的大模型服务平台和API入口，通义千问是平台提供的模型。项目中的DashScopeChatModel是Spring AI Alibaba的Provider适配器，负责把统一的ChatModel调用转换为DashScope协议。业务代码通过API Key访问DashScope，并通过model参数选择qwen-max完成推理。

### 易错点

- DashScope不是Spring AI Alibaba，两者分别是云平台和Java框架。
- DashScope不是qwen-max，前者是服务平台，后者是具体模型。
- `DashScopeChatModel`不是大模型，它只是Java适配器。
- API Key应通过环境变量或密钥服务提供，不能提交到Git仓库。

### 状态

- [x] 已讲解并记录

## 问题2：ChatClient和ChatModel在代码中如何连接？

### 先纠正职责

- `AiReplyNode`等Controller、Service、Graph Node属于业务/应用层。
- `ChatClient`不是业务层本身，而是业务层使用的AI调用客户端，负责方便地组装Prompt、Advisor、Tool及调用方式。
- `ChatModel`是底层聊天模型的统一Java接口。
- `DashScopeChatModel`是`ChatModel`针对DashScope的具体实现，负责实际访问通义千问服务。

### 当前项目中的代码链路

项目通过依赖引入DashScope Starter：

```xml
<artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
```

并在`application.yml`中配置模型：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY:}
      chat:
        options:
          model: qwen-max
          temperature: 0.1
```

Spring Boot启动时读取这些配置，由自动配置创建一个`DashScopeChatModel` Bean。业务代码不直接依赖具体实现，而是通过接口进行构造器注入：

```java
private final ChatModel chatModel;

public AiReplyNode(ChatModel chatModel, HistoryManager historyManager) {
    this.chatModel = chatModel;
    this.historyManager = historyManager;
}
```

字段的编译期类型是`ChatModel`，但运行期对象是`DashScopeChatModel`。这就是Java的接口多态和Spring依赖注入。

节点中通过以下代码把高级客户端与模型实现连接起来：

```java
ChatClient client = ChatClient.builder(chatModel).build();
String reply = client.prompt()
        .user(prompt)
        .call()
        .content();
```

各步骤含义：

1. `ChatClient.builder(chatModel)`：把已注入的模型能力交给ChatClient。
2. `prompt()`：开始构造一次模型请求。
3. `user(prompt)`：把字符串包装为用户消息。
4. `call()`：触发同步、非流式调用，最终进入`ChatModel`。
5. Java动态分派到运行期对象`DashScopeChatModel`的方法。
6. Provider实现将Spring AI的Prompt转换为DashScope请求，携带API Key访问`qwen-max`。
7. Provider将厂商响应转换成Spring AI统一响应。
8. `content()`提取助手回复文本并返回给业务代码。

完整链路为：

```text
AiReplyNode（业务节点）
→ ChatClient（请求构造与应用编排）
→ ChatModel（统一接口）
→ DashScopeChatModel（具体Provider实现）
→ DashScope网络接口
→ qwen-max
→ 统一响应
→ content文本
```

### 为什么业务代码只写ChatModel

这是依赖倒置：上层依赖抽象，而不是依赖DashScope的具体类。未来切换其他模型Provider时，只要容器中注入另一种`ChatModel`实现，主要业务调用代码通常不需要改动。不过模型名称、参数、Prompt效果和厂商特有能力仍需重新验证。

### 当前代码可改进之处

当前代码每次执行节点都会调用`ChatClient.builder(chatModel).build()`。它能够工作，但工程上更适合在配置类中集中创建可复用的`ChatClient` Bean或Builder，再注入节点。这样默认System Prompt、Advisor、工具、日志和模型参数可以统一配置，避免散落在业务代码中。

### 面试表达

> ChatClient不是业务层本身，而是业务层面向AI能力的高级客户端；ChatModel是统一模型接口。项目引入DashScope Starter后，Spring Boot根据配置自动创建DashScopeChatModel Bean，并以ChatModel接口注入AiReplyNode。ChatClient.builder(chatModel)把两层连接起来，call触发模型调用时，通过Java多态最终执行DashScopeChatModel的Provider逻辑，访问qwen-max，再把厂商响应转换成统一结果返回。

### 易错点

- 不是`ChatModel`这个接口自己发送网络请求，而是其运行期具体实现发送。
- `ChatClient`不负责真正推理，真正推理发生在远程大模型服务中。
- `model: qwen-max`只是选择模型；`DashScopeChatModel`才是访问该Provider的Java适配器。
- 自动配置、依赖注入和接口多态共同完成了“代码只写抽象，运行时选择实现”。

### 状态

- [x] 已讲解并记录

## 问题3：Spring AI整体架构如何分层，一次请求如何执行？

### 总体分层

```text
业务应用层
→ ChatClient应用编排层
→ Advisor责任链
→ ChatModel模型抽象层
→ Provider模型适配层
→ 模型服务
```

- 业务应用层：Controller、Service及业务规则，发起AI请求并消费结果。
- ChatClient：构造Prompt，组合默认配置、工具和Advisor，提供`call()`与`stream()`入口。
- Advisor链：在调用前后插入Memory、RAG、Tool Calling、日志等横切能力；Advisor可修改请求、调用下游并处理响应。
- ChatModel：统一的底层模型调用抽象，接收Prompt并返回ChatResponse或流式响应。
- Provider适配层：如DashScopeChatModel，负责把Spring AI对象转换为厂商请求协议，再把厂商响应转换回统一消息对象。
- 模型服务：Qwen等真正执行推理的远程服务。

### 非工具请求链路

```text
Controller
→ ChatClient.prompt()
→ 生成ChatClientRequest/Prompt
→ Advisor链依次增强
→ ChatModel.call()/stream()
→ Provider序列化并请求模型
→ Provider解析模型响应
→ ChatResponse/Flux<ChatResponse>
→ Advisor逆序处理响应
→ 返回业务层
```

### 带工具请求链路

```text
ChatClient
→ ToolCallingAdvisor
→ ChatModel（工具Schema随Prompt发送）
→ AssistantMessage.ToolCall
→ ToolCallingManager执行ToolCallback
→ ToolResponseMessage加入消息历史
→ 再次调用ChatModel
→ 无Tool Call时返回最终结果
```

### 控制方向

请求向下经过各层，响应向上返回。Advisor采用责任链：请求阶段按顺序进入，响应阶段通常沿调用栈逆序返回。ChatClient负责应用编排，ChatModel只负责模型调用，不应混淆二者职责。

### 面试表达

> Spring AI采用分层与适配器架构。业务层通过ChatClient构造请求；ChatClient利用Advisor责任链组合Memory、RAG、Tool Calling等能力；链路最终调用ChatModel统一接口；具体Provider实现负责厂商协议转换和远程模型访问；响应再转换为统一ChatResponse并沿Advisor链返回。这样业务编排、通用增强能力和厂商通信相互解耦。

### 易错点

- ChatClient不是模型，它是应用层客户端和能力编排入口。
- ChatModel通常只抽象一次模型调用；完整工具循环可能由ToolCallingAdvisor等上层组件管理。
- Advisor不是简单的“调用前回调”，它可以包裹下游调用，因此能够同时处理请求和响应，递归Advisor还可再次调用模型。
- Provider统一的是公共抽象，不同厂商的特有参数仍可能通过专用Options暴露。

### 状态

- [x] 已讲解并记录

## 学习检测记录

### ChatClient、ChatModel与DashScope职责辨析

- 当前评价：基本掌握。
- 已掌握：能够说清`ChatClient → ChatModel → DashScopeChatModel → 大模型平台`的主调用方向；知道`DashScopeChatModel`是`ChatModel`的具体实现。
- 待校准：`ChatModel`是统一模型调用接口，真正连接阿里平台的是`DashScopeChatModel`；ChatClient只有配置了Advisor、Memory或Tool后才会编排对应能力，并非天然自动包含全部能力。
- DashScope与qwen-max检测：已掌握。能够区分阿里大模型服务平台与具体千问模型，并理解请求先访问平台、再由模型名称路由到具体模型。
- 下一步检测：解释`ChatClient.builder(chatModel).build()`和调用链。
- `ChatClient.builder(chatModel)`首次检测：需要复习。将模型名称字符串与`ChatModel`接口对象混淆，尚未说清接口多态、Spring依赖注入和依赖倒置。
- `ChatClient.builder(chatModel)`第二次检测：部分掌握。已理解该变量可以承载不同实现，但仍需区分“本地模型调用适配器对象”和“远程大模型本体”。
- `ChatModel`接口与实现检测结果：基本掌握。已能区分统一调用接口和阿里平台调用实现；需保持术语准确：`DashScopeChatModel`是实现类，`qwen-max`是远程具体模型。
- `prompt/user/call/content`调用链检测结果：基本掌握。已理解用户消息、模型调用与文本提取；需修正：`prompt()`是开始构建请求，不等同于设置System Prompt，系统消息应通过`system(...)`提供。

## 问题4：Prompt、Message和ChatResponse分别是什么？

### 核心关系

```text
Prompt = 本次发给模型的完整请求
Message = Prompt中的一条角色消息
ChatResponse = 模型返回的结构化响应
```

`Prompt`通常包含一个有顺序的消息列表和本次调用参数。消息顺序构成模型本次能够看到的上下文。

常见消息类型：

- `SystemMessage`：身份、全局规则和行为边界。
- `UserMessage`：用户输入及本轮业务上下文。
- `AssistantMessage`：模型此前回复；也可能携带工具调用请求。
- `ToolResponseMessage`：工具执行结果，供模型继续推理。

`ChatResponse`不只有回答字符串，还可以包含Generation、结束原因、Token用量、模型信息等元数据。`ChatClient.call().content()`是一个便捷入口，只提取最终文本。

### 一次请求示例

```text
Prompt
├── SystemMessage：你是汽车客服，只根据知识库回答
├── UserMessage：电池质保多久？
├── AssistantMessage：我需要查询质保工具（工具调用请求）
├── ToolResponseMessage：电池质保期为……
└── ChatOptions：model、temperature等参数

模型返回
└── ChatResponse
    ├── AssistantMessage：最终自然语言回答
    └── Metadata：Token、结束原因、模型信息等
```

### 为什么它对Agent重要

多轮记忆的核心是保存并重新组装Message；工具调用的核心是在AssistantMessage和ToolResponseMessage之间往返；RAG的核心是把检索结果加入本次Prompt。因此它们表面上是不同能力，最终都会影响送给模型的上下文。

### 面试表达

> Prompt表示一次完整模型请求，包含有序Message列表和调用参数；Message通过System、User、Assistant和Tool等角色表达上下文；ChatResponse是包含模型输出和元数据的结构化结果。ChatClient的链式API是在帮助业务代码构造和消费这些底层对象。

### 易错点

- Prompt不等于System Prompt，它是整个请求对象。
- Message列表有顺序，不能随意重排。
- ChatResponse不等于一个String，`content()`只是提取文本的便捷方法。
- 历史消息并不会天然进入新请求，需要Memory或业务代码重新组装。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够正确识别System、历史User、历史Assistant和当前User四条消息，并保持消息顺序。

## 问题5：Advisor是什么，为什么需要它？

### 定义

Advisor是ChatClient调用链中的增强组件，作用类似Spring MVC拦截器或Servlet Filter。它可以在模型调用前读取或修改请求，也可以在模型返回后读取或修改响应。

```text
业务调用
→ Advisor A（请求前处理）
→ Advisor B（请求前处理）
→ ChatModel
→ Advisor B（响应后处理）
→ Advisor A（响应后处理）
→ 业务代码
```

### 适合承载的能力

- Memory：根据会话ID读取历史消息并加入Prompt，响应后保存新消息。
- RAG：查询向量库并将检索结果补充到请求上下文。
- Tool Calling：识别工具调用、执行工具、追加工具结果并再次请求模型。
- 日志与可观测性：记录耗时、Token和异常。
- 安全处理：输入检查、输出审查等。

Advisor用于复用跨业务的AI调用增强逻辑，不应承载订单退款规则等核心领域业务。

### 当前项目对照

当前项目在`AiReplyNode`中手动拼接`historySummary`、`recentHistory`、用户输入和查询结果，这能工作，但这些通用逻辑容易散落。Advisor可以将适合复用的记忆、RAG或日志逻辑从节点调用代码中抽离；具体业务流程仍应保留在Graph Node或Service中。

### Advisor接在哪里、如何接入

Advisor是实现Spring AI `Advisor`接口的Java对象，接在ChatClient与ChatModel之间。可以在创建ChatClient时通过`defaultAdvisors(...)`全局接入，也可以在单次请求中通过`advisors(...)`临时接入。

```java
ChatClient client = ChatClient.builder(chatModel)
        .defaultAdvisors(memoryAdvisor)
        .build();

String reply = client.prompt()
        .user(question)
        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
        .call()
        .content();
```

ChatClient执行请求时主动遍历Advisor链，Advisor并不是自行监听请求。以Memory Advisor为例：调用前根据`sessionId`读取消息并加入请求，调用后把本轮User和Assistant消息保存到ChatMemory，再把结果返回业务代码。

### 易错点

- Advisor不是大模型，也不负责基础模型推理。
- 配置ChatClient不代表自动拥有Memory/RAG，必须添加相应Advisor或自行实现。
- Advisor有执行顺序，多个Advisor的先后可能影响最终Prompt和响应。

### 状态

- [x] 已讲解并通过检测
- 首次检测：需要继续巩固。尚未区分“固定提示词”和“历史消息”，也未明确调用后需要将本轮User与Assistant消息保存回ChatMemory。
- 第二次检测：基本掌握。已理解Memory Advisor调用前读取历史、调用后保存历史；完整表达应包含“将历史加入本次Prompt”以及“保存本轮User与Assistant消息”。
- 最终检测：已掌握。能够说明Advisor位于ChatClient与ChatModel之间，并由ChatClient组织调用。

## 问题6：多个Advisor为什么构成责任链，执行顺序是什么？

### 核心机制

ChatClient将多个Advisor按`order`组织成链。每个Advisor都可以执行请求前逻辑，再决定是否调用`chain.next...`把请求交给下一个Advisor；模型响应返回时，再执行自己的响应后逻辑。

```text
请求方向：ChatClient → A → B → ChatModel
响应方向：ChatModel → B → A → ChatClient
```

近似代码：

```java
Response advise(Request request, Chain chain) {
    before(request);
    Response response = chain.nextCall(request);
    after(response);
    return response;
}
```

如果某个Advisor不调用下一个节点，它可以提前返回结果或中断链路。执行顺序会影响上下文：例如输入安全检查通常应在把内容送入模型前完成，日志Advisor的位置则决定它看到的是原始请求还是已经被Memory/RAG增强的请求。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够区分“JSON格式合法”和“业务语义合法”，知道需要白名单/枚举与业务规则校验，并在失败时有限重试和安全降级。

## 问题9：大模型如何调用工具？

### 核心原则

大模型不能直接执行Java方法。应用先把工具名称、描述和参数JSON Schema随请求发送给模型；模型只返回“建议调用哪个工具及参数”；框架或业务代码校验后找到对应`ToolCallback`执行。

```text
Java工具定义
→ 生成工具Schema
→ ChatModel把消息和Schema发给模型
→ 模型返回Tool Call（工具名+参数）
→ 框架匹配ToolCallback并校验/转换参数
→ Java方法执行
→ ToolResponseMessage加入消息列表
→ 再次调用模型
→ 模型生成最终自然语言回答
```

模型负责选择和生成参数，应用负责工具注册、权限、参数校验、超时、幂等、执行和异常处理。工具调用成功也不代表高风险业务动作可以绕过鉴权或人工确认。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够完整说明注解生成工具定义、注册可用工具、模型生成Tool Call、框架匹配ToolCallback、Java执行和结果回传链路。
- 术语修正：模型不直接读取Java注解；发送的核心是工具名、描述和输入参数JSON Schema；工具执行结果随后通过ToolResponseMessage回传。工具注册不替代服务端权限与业务校验。

## 问题10：模型请求未知工具时如何兜底？

### 安全边界

Tool Call中的工具名只是模型生成的数据，不能被当成执行权限。框架必须只在本次已注册的`ToolCallback`集合中按精确名称查找；找不到就拒绝，禁止通过反射扫描并执行任意同名Java方法。

### 推荐处理链路

```text
收到Tool Call
→ 在注册表/白名单中查找
→ 未找到：记录错误，不执行任何业务方法
→ 可选：把受控错误作为工具结果反馈给模型并有限重试
→ 仍失败：安全降级、返回明确错误或转人工
```

Spring AI或不同Agent框架的默认行为可能是抛出异常，而不是自动要求模型重新生成。因此“是否重试、最大次数、错误如何反馈、最终如何降级”应由应用显式设计。禁止无限重试。

### 状态

- [x] 已讲解并通过检测
- 首次检测：部分掌握。想到让模型重新生成，但尚未说明先拒绝未知工具、默认行为不一定自动重试，以及禁止反射执行未注册方法。
- 最终检测：已掌握。能够给出受控错误反馈、有限重试、失败降级、明确报错和人工介入等处理方式。
- 工程边界：重试次数应配置化；所有重试前必须保证未知工具未被执行；只有非关键能力可以安全跳过，高风险业务不能静默忽略失败。

## 问题11：工具存在，但模型生成的参数非法时如何处理？

### 三层校验

```text
1. 语法/反序列化校验：是不是合法JSON，能否转换为Java参数
2. Schema/字段校验：必填字段、类型、长度、格式、枚举是否合法
3. 业务校验：用户权限、资源归属、订单状态、金额范围、幂等条件是否合法
```

JSON解析成功只代表语法正确，不代表业务操作可以执行。校验失败时必须在调用业务方法之前停止；可以将不包含敏感内部信息的错误反馈给模型进行有限修复重试，仍失败则返回安全错误或转人工。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够准确说明Advisor负责读写时机、ChatMemory负责窗口策略、Repository负责存储，并知道InMemory实现随JVM重启丢失，需要持久化实现。

## 问题18：ChatMemory、Graph State、Checkpoint、聊天存档和长期记忆有什么区别？

### 职责区别

```text
ChatMemory     给模型提供近期对话上下文，通常按conversationId组织Message
Graph State    一次Agent流程中节点之间传递的工作变量
Checkpoint     持久化某个threadId的Graph State快照，用于恢复和继续执行
聊天存档       完整、可查询的原始对话记录，用于展示、审计和分析
长期记忆       从历史中提取的稳定事实或偏好，跨会话按userId检索
```

Graph State可以保存`route`、`toolAttempt`、`lastToolError`、工具结果和当前执行状态，这些不是都应该作为自然语言消息放入ChatMemory。Checkpoint保存状态快照，不等于完整审计日志。

完整存档应独立持久化，不受上下文窗口淘汰；发送给模型时再从近期消息、摘要或检索到的长期记忆中选择必要内容，避免把全部历史无限塞入Prompt。

### ChatMemory为什么不等于聊天存档

两者可能保存相似的Message，甚至使用同一数据库，但逻辑职责不同。ChatMemory是面向模型上下文的“工作集”，允许窗口淘汰、摘要和过滤；聊天存档是面向用户查询、审计和分析的完整事实记录，应保留时间、原始文本、状态和必要元数据，不能随上下文窗口被删除。

例如一段会话已有100条消息，`MessageWindowChatMemory(maxMessages=20)`只需为模型保留近期20条，而聊天存档仍应保存100条。生产设计通常先追加完整消息到档案表，再由Memory层从档案或独立存储中选取近期消息/摘要构建上下文。

### 标识符

```text
userId          身份与跨会话长期记忆
conversationId  一次对话的消息隔离
threadId        一条Graph执行线程及Checkpoint恢复
```

三者可以有关联，但语义不能混为一谈。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够给出文档切块、Embedding、向量与BM25双路召回、RRF融合、Rerank和Top5输出的完整Hybrid RAG链路。
- 工程修正：500～600 Token仅是候选起点，应按文档结构和评测调优；Embedding模型需基于中文领域数据、成本和延迟评测；RRF属于基于排名的融合，Reranker才是对query-document相关性进行更精细的重排。

## 问题20：RRF融合与Rerank有什么区别，为什么经常同时使用？

### RRF

RRF（Reciprocal Rank Fusion）只使用文档在各召回列表中的名次，不要求向量相似度和BM25分数处于同一尺度：

```text
RRF(d) = Σ 1 / (k + rank_i(d))
```

它成本低、稳定，适合合并向量检索、BM25、多查询等异构排名，但不深入理解query与document的语义关系。

### Rerank

Reranker对`query + candidate document`进行成对相关性判断，通常比初始召回更精确，但计算成本更高，因此只处理融合后的较小候选集。

```text
向量Top20 + BM25 Top20
→ 去重
→ RRF融合并截取候选TopN
→ Reranker精排
→ 最终Top5
```

RRF解决“多个排序如何合并”，Rerank解决“合并后的候选谁与问题真正更相关”。

### 状态

- [x] 已讲解并通过检测
- 首次检测：基本掌握。已理解向量分数与BM25分数量纲不同、Rerank用于精排；需纠正Cross-Encoder原理：它联合编码query和document并直接输出相关性分数，不是分别生成向量后计算夹角。
- 最终检测：已掌握。能够区分Bi-Encoder的独立向量编码与相似度计算、Cross-Encoder的联合编码与相关性打分，并理解Cross-Encoder只用于候选集是因为成本和延迟较高。

## 问题21：RAG检索不到可靠证据时，如何避免模型编造？

### 分层控制

```text
检索前：查询改写、metadata权限过滤
检索中：TopK、阈值、混合召回、Rerank
生成前：证据门控，低置信度时不进入正常回答
生成时：要求只依据证据回答并输出来源
生成后：引用校验、事实一致性检测、敏感操作规则校验
兜底：澄清问题、明确无法确认、转人工
```

相似度阈值不是“答案正确率”，不同模型、向量库和距离算法的分数不可直接照搬，需要通过标注数据校准。即使检索分数高，文档也可能过期或不具备回答问题的充分证据。

系统Prompt只能降低幻觉概率，不能形成强保证。高风险场景应通过结构化引用、证据ID、后置校验和确定性业务规则控制，不能只相信模型声称“依据知识库”。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够识别多子问题证据覆盖不足，提出证据检测、有限重新检索和禁止无依据生成。
- 工程修正：证据判断不能只依赖另一个LLM；应结合子问题覆盖、引用ID和规则校验。重新检索必须改变Query或检索策略；最终仅回答有证据部分并明确未覆盖内容。

## 问题22：RAG检索失败后如何进行有效的检索重试？

### 不能原样重复

同一索引、同一Query和同一参数通常产生相同结果。有效重试应根据失败原因改变查询或检索策略：

```text
指代不清 → 结合会话历史补全实体
复合问题 → 拆成多个子查询分别召回
表达口语化 → 改写为领域术语及同义词
关键词精确 → 提高BM25权重或执行精确过滤
语义表达多样 → 生成有限数量Multi-Query
过滤过严 → 在权限不变前提下放宽非安全metadata过滤
知识库无数据 → 停止检索，改调业务Tool或转人工
```

每次重试应保存原Query、改写Query、触发原因和结果差异，并设置最大次数。Query改写要防止语义漂移，不能改变用户原始意图，也不能移除租户和权限过滤。

### 当前项目

当前`KnowledgeNode`已经执行`QueryCleaner → QueryRewriter → Retriever.retrieveMultiQuery`，具备清洗、结合历史改写和多查询召回的基础。若增加检索重试，应在Graph State记录`retrievalAttempt`和`lastRetrievalReason`，由条件边决定重新改写、转Tool或进入无答案出口。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。能够通过历史进行指代消解，保留原Query并将不明确代词替换为具体实体；还提出使用假设回答增强向量检索。
- 工程修正：历史只用于补足有依据的实体，不得改变否定、时间、金额、产品和用户意图；假设回答属于HyDE，只能作为检索载体，不能作为事实或最终答案。

## 问题23：HyDE是什么，与普通Query Rewrite有什么区别？

### 普通改写

普通Query Rewrite仍生成“查询语句”，主要完成指代消解、术语规范、同义词扩展和子问题拆分：

```text
它坏了还能免费换吗
→ 小鹏G6动力电池自然衰减是否属于免费更换范围
```

### HyDE

HyDE（Hypothetical Document Embeddings）让模型生成一段“假设的相关文档/回答”，对该假设文本生成Embedding，再用它召回真实文档：

```text
原Query
→ LLM生成假设答案文本
→ 对假设文本Embedding
→ VectorStore召回真实Chunk
→ 真实证据进入后续RAG
```

它利用“答案风格文本”与知识库文档表达更接近的特点改善语义召回，但假设文本可能含有错误事实，因此不能显示给用户、不能作为证据，也不能直接驱动高风险业务。

工程上宜保留原Query召回，并将HyDE作为额外召回通道，再通过去重、RRF/Rerank和证据门控融合，且设置调用成本、延迟和触发条件。

### 状态

- [x] 已讲解并通过检测
- 检测结果：已掌握。理解HyDE通过生成接近知识库表达的假设文档改善向量召回，同时知道假设文本没有真实证据，不能作为最终答案。
- 工程修正：HyDE的作用不只是把疑问句改为肯定句；保留原Query召回可降低假设事实错误和语义漂移风险。

## 问题24：如何评估RAG系统，证明检索和回答真的变好？

### 分层评估

不能只看最终回答，应分别评估检索层和生成层：

```text
检索评估：相关Chunk是否被召回、排名是否靠前
生成评估：回答是否基于证据、相关、完整、引用准确
系统评估：成功率、拒答准确性、延迟、Token和成本
```

### 常见检索指标

- Hit@K：TopK中是否至少命中一个标注相关Chunk。
- Recall@K：所有相关Chunk中有多少进入TopK。
- MRR：第一个相关Chunk排名越靠前越好。
- NDCG@K：考虑多级相关性和整个排名质量。
- Precision@K：TopK中相关Chunk的比例。

### 生成与端到端指标

- Faithfulness/Groundedness：结论是否能由检索证据支持。
- Answer Relevance：是否回答用户问题。
- Completeness：多子问题是否覆盖完整。
- Citation Accuracy：引用是否真实对应证据。
- Abstention/Refusal Accuracy：无答案时能否正确拒答，避免有证据时误拒答。
- P95延迟、Token和单次成本。

评测集应包含可回答、不可回答、指代、多子问题、精确关键词、口语表达、权限和对抗问题。每次只改变一个变量进行基线对比，例如向量Only、BM25 Only、Hybrid、Hybrid+Rerank。

### 状态

- [x] 已讲解并通过检测
- 综合检测：已掌握，约90%。能够说明Node返回局部更新、State保存共享信息、条件边基于State路由、Graph通过KeyStrategy合并新旧值。
- 边界修正：固定边不需要读取State；只有节点显式返回的key才会更新State；State是运行期容器，Checkpoint/Repository才负责跨时间持久化。
- 首次检测：基本掌握。已能从检索命中、首个相关结果排名、回答完整性、引用和幻觉等维度设计评测，并补充错字、多语言、多问题和长文本等测试集。
- 指标修正：单个问题的Hit@5是0或1；全数据集Hit@5是这些值的平均。若Top5中4条相关则Precision@5=80%。题设3个相关Chunk召回2个且首个在第2名时，Hit@5=1、Recall@5=2/3、Reciprocal Rank=1/2。
- 最终检测：已掌握。能够正确计算评测集Hit@5与单问题Precision@5。

## 问题25：为什么有了ChatClient还需要StateGraph和CompiledGraph？

### 职责区别

`ChatClient`适合组织一次模型请求及Advisor链；Graph用于组织跨多个节点、有状态、可分支、可循环和可恢复的Agent工作流。

```text
ChatClient：Prompt → Advisor → ChatModel → Response
Graph：Node → Edge/条件路由 → Node → State更新 → Checkpoint/END
```

### Graph核心组件

- `StateGraph`：图的定义/蓝图，注册节点、边、条件边和State合并策略。
- `Node`：一个可测试的处理步骤，如语义路由、知识检索、工具执行或最终回答。
- `Edge`：固定转移关系。
- `Conditional Edge`：读取State后决定下一个节点。
- `OverAllState`：节点之间共享的数据容器。
- `KeyStrategy`：同一个State key接收新值时采用Replace、Append等合并方式。
- `CompiledGraph`：StateGraph经过校验和编译后的可执行对象。
- `RunnableConfig.threadId`：标识Graph执行线程，与Checkpoint恢复关联。

### 当前项目链路

```text
START → semantic_route
→ clarify / faq / knowledge_base / business_tool / external_search / human_handoff
→ ai_reply或END
```

Controller把请求转换为input Map，并用`SessionKey.threadId()`构造RunnableConfig，再调用`customerSupportGraph.stream(input, config)`获取各节点的`NodeOutput`事件。

Graph不是越复杂越好。单轮分类或简单RAG可直接使用ChatClient；当存在多分支、工具循环、重试、人工审批、状态恢复和可观测节点事件时，Graph的价值更明显。

### 与Python LangGraph的关系

Spring AI Alibaba官方说明其Graph设计受LangGraph启发，二者都采用State、Node、Edge、条件路由、compile、checkpoint和threadId等状态图范式。但Spring AI Alibaba Graph是独立的Java实现，不调用Python LangGraph运行时；它使用Spring Bean、Java类型、Reactor Flux、OverAllState、KeyStrategy、RunnableConfig和SaverConfig等Java/Spring机制。概念可以迁移，API和运行时行为不能按一比一等同。

### 状态

- [x] 已讲解并掌握基础：Future不是线程；单节点以Future交付一次结果，Graph内部可用Mono处理，完整执行过程可用Flux发布多次事件；node_async不等于真正非阻塞

## 问题32：节点发生异常后，Graph如何传播和兜底？

未处理异常会使Future异常完成，Graph不会执行正常的State合并与普通边跳转，异常继续向上游传播。若希望进入兜底流程，应在适当层捕获可处理异常，将其转换为例如`route=fallback`的局部状态更新，再由条件边进入降级或反馈节点。业务缺参通常应进入澄清分支，而不是作为系统故障直接终止。

### 状态

- [x] 已讲解并掌握：异常默认中断Graph；捕获并转换为状态后，才能通过条件边进入兜底节点

## 问题33：Agent节点的重试应该如何设计？

错误应先结构化分类：临时网络、限流和服务端故障可进行有限重试与指数退避；参数格式错误可反馈给模型修正，缺少真实业务信息应转澄清；权限问题按重新认证、拒绝、审批或人工介入处理；有副作用的工具必须保证幂等后才能重试。重试耗尽后进入降级。熔断不同于重试，它根据一段时间内持续的高失败率暂时停止调用故障依赖，避免拖垮整个Agent链路。MCP工具可通过`isError`表示工具失败，通过`structuredContent`承载结构化结果；第三方MCP格式不统一时，由客户端适配层转换成内部统一错误模型。

### 状态

- [x] 已讲解并通过检测：能够区分重试、降级和熔断

## 问题34：Graph Checkpoint保存什么，为什么需要它？

Checkpoint是Graph执行现场的快照，核心包括OverAllState、当前或下一执行位置、threadId/运行配置及相关元数据。它用于中断恢复、人工审批后续跑和故障恢复；不同于ChatMemory，后者主要让模型获得对话上下文。只有进入State或配置的信息才可能被保存，Checkpoint也不会自动撤销或确认外部系统已经产生的副作用。

### 状态

- [x] 已讲解并通过检测：能够说明Checkpoint保存执行状态与位置，并用于意外后的恢复执行

## 问题35：threadId如何让Checkpoint恢复到正确会话？

threadId不是Java线程ID，而是Graph执行链的稳定标识。业务层可由userId与sessionId生成SessionKey，再得到threadId并写入RunnableConfig；Checkpoint保存器据此存取对应执行快照。继续原会话复用原threadId，新会话使用新threadId。同一threadId并发修改同一状态时需要串行化或并发控制。

### 状态

- [x] 已讲解并通过检测：新sessionId应生成新threadId，以隔离不同会话的State和Checkpoint

## 问题36：Graph如何暂停、等待确认并恢复执行？

暂停Graph不是让Java线程长期等待，而是在中断点保存OverAllState与执行位置，结束当前运行并释放请求线程。用户确认后发起新请求，系统使用相同threadId读取Checkpoint、合并确认信息、重新校验身份与业务状态，再从中断位置继续。高风险副作用通常在节点执行前中断，生成结果需要人工审核时可在节点执行后中断。

### 状态

- [x] 已讲解并通过检测：能够说明持久化Checkpoint、释放线程并用相同threadId恢复Graph

## 问题37：Checkpoint保存器应该如何选择？

### 状态

- [ ] 学习中

### 简化检测记录

- 当前评价：基本掌握Node核心模型。
- 已理解：每个Node注册一个Action，只有Graph调度到对应nodeId时才执行；Action读取State并返回局部Map。
- 术语修正：节点成功返回后Graph通常会执行合并，不是临时判断是否合并；KeyStrategy决定覆盖、追加或Merge。异常完成时不走正常合并链路。

## 问题26：当前客服系统是否属于正常的状态图Agent架构？

### 定位

当前系统是合理的`Graph-based Agentic Workflow`：LLM与规则共同完成语义路由，StateGraph通过条件边进入FAQ、RAG、业务工具、外部搜索或人工分支，再由回答节点结束。它具备Agentic特征，但当前主图是固定单向DAG，不是具备动态规划、工具选择—执行—观察—再决策循环的高度自主Agent。

### 已具备的标准工程组件

- Controller与Graph执行解耦。
- `SessionKey → threadId → RunnableConfig`实现会话/执行标识。
- `SessionExecutionGate`串行化同会话执行，防止单进程并发状态冲突。
- OverAllState与Replace/Append策略管理节点共享状态。
- 条件边进行显式、可审计路由。
- FileSystemSaver保存Checkpoint。
- Flux输出节点事件。
- 自定义RAG、近期原文+早期摘要记忆。

### 当前尚未形成的Agent闭环

- `BusinessToolNode`目前只返回占位文本，没有真实ToolCallback和业务系统调用。
- 主图没有工具失败纠错、证据不足重检、审批后恢复等循环边和attempt上限。
- 路由节点手工解析模型JSON，结构化输出和校验仍可增强。
- FileSystemSaver与本地Semaphore适合单机学习/开发；多实例生产环境需要共享Checkpoint和分布式并发/版本控制。

客服场景不必追求最大自主性。显式分支、权限边界和确定性降级通常比完全由模型自由规划更安全。简历宜称“基于Spring AI Alibaba Graph的智能客服工作流/Agentic Workflow”；补齐真实工具闭环和循环控制后，再强调完整Agent执行闭环。

### 状态

- [x] 已讲解并记录

## 问题27：当前架构能否扩展为自主规划Agent？

### 结论

可以。现有StateGraph、OverAllState、条件边、Checkpoint、threadId和Flux执行已经是运行底座；需要增加Planner、Tool Executor、Observer/Evaluator和Replanner循环，以及次数、Token、时间、权限和人工审批边界。

推荐采用混合架构：外层Graph保留确定性语义路由、FAQ、RAG和人工出口；只有复杂、需要多步工具协作的任务进入受限Agent子图。

```text
外层Router
├─ 简单FAQ/RAG → 固定工作流
├─ 高风险操作 → 审批/确定性流程
└─ 复杂任务 → Agent子图
                 plan
                  ↓
              select_tool
                  ↓
           validate/authorize
                  ↓
               execute
                  ↓
               observe
          ┌───────┴────────┐
       未完成且未超限       完成/超限
          ↓                  ↓
       replan             answer/降级
          └────→ select_tool
```

State至少需要`goal`、`plan`、`currentStep`、`observations`、`toolAttempt`、`iteration`、`maxIterations`、`deadline`、`lastError`和`approvalStatus`。模型只负责提出计划和工具调用建议；注册表、参数/权限校验、执行、幂等、预算和降级由应用控制。

也可以使用Spring AI Alibaba Agent Framework提供的ReactAgent作为受限子Agent或Graph节点，但仍要由外层Graph约束工具集合、最大调用次数、高风险审批和退出条件。

当前`application.yml`虽然配置`assistant.graph.max-iterations: 4`，但`GraphAgentConfiguration`构造`CompileConfig`时尚未调用`recursionLimit(appProperties.getGraph().getMaxIterations())`，所以该业务配置目前没有真正接入Graph运行上限。增加循环前必须修正，并同时保留State中的业务attempt计数；全图递归上限和单类错误重试上限是两种不同保护。

### 状态

- [x] 已讲解并记录

## 问题28：Graph中的边本质是什么，固定边和条件边如何运行？

### 边的本质

边不是Java方法调用，也不传输业务数据；它是Graph中从source node选择next node的路由元数据。业务数据通过OverAllState传递。

源码内部固定边近似保存为：

```text
Edge(sourceId, EdgeValue(targetId))
```

条件边近似保存为：

```text
Edge(sourceId, EdgeValue(EdgeCondition(action, mappings)))
```

### 运行过程

```text
NodeExecutor执行当前节点
→ 节点返回State update
→ 按KeyStrategy合并到OverAllState
→ GraphRunnerContext读取当前节点对应Edge
→ 固定边直接得到targetId
→ 条件边执行condition(state)，得到逻辑route key
→ mappings把route key映射为真实nodeId
→ 保存Checkpoint/nextNode信息并调度下一节点
```

条件函数返回的`knowledge_base`首先是逻辑路由值，只有在`Map.of("knowledge_base", "knowledge_base", ...)`中查到映射后才能进入真实节点；未找到映射会抛出Graph运行错误。

### 编译期校验

`StateGraph.compile()`先执行`validateGraph()`，检查START入口、source节点、target节点和条件映射目标是否存在。它能发现结构错误，但无法证明条件函数运行时一定返回合法key，也无法证明所有业务分支都可达。

### 为什么Node不直接调用下一个Node

节点只负责`State → State Update`，Graph统一负责调度，这样才能集中实现条件路由、Checkpoint、流式事件、取消、迭代上限、并行执行和可视化，并让节点可独立测试。

### 状态

- [x] 已讲解，等待检测

## 问题29：循环边如何工作，怎样避免Agent无限循环？

### 原理

循环边本质上仍是条件边，只是某个映射目标指向之前的节点：

```text
tool_executor → observer
observer --retry--> tool_executor
observer --done--> END
observer --failed--> human_handoff
```

节点执行并更新State后，条件函数读取`status`、`attempt`等字段选择回边或退出边。Graph不要求必须是DAG，因此可以重复调度同一节点。

### 两层上限

- `CompileConfig.recursionLimit`：整张图执行的全局保险，防止错误路由导致无界循环。
- State中的`toolAttempt/retrievalAttempt`：具体业务错误的重试预算，用于决定何时降级。

二者不能互相替代。全图上限耗尽通常代表最后一道防线；业务流程应在触发全局上限前主动进入失败或人工节点。

### 关键保护

- 条件映射必须包含成功、可重试、不可重试和超限出口。
- 每次循环必须更新能够推动流程前进的State，不能原样重复。
- 有副作用的工具必须使用幂等键/调用ID，避免Checkpoint恢复或超时重试造成重复扣款、取消等操作。
- 保存每次attempt、errorCode和observation，便于可观测与复盘。

### 当前项目提醒

`application.yml`中的`assistant.graph.max-iterations: 4`尚未传给`CompileConfig.recursionLimit(...)`，目前不会按该值限制Graph。真正增加循环前需要接入此配置。

### 状态

- [x] 已讲解，等待检测

## Node、Edge、State综合检测

- 当前评价：基本掌握，约70%。
- 已掌握：Node是处理步骤；普通边无条件转移；条件边根据条件选择目标；State保存路由和节点结果等共享信息。
- 核心修正：Edge不携带或传递State。State属于GraphRunnerContext中的共享执行上下文；Node返回State Update，Graph按KeyStrategy合并后，Edge读取最新State并只决定next node，后续Node再读取同一份最新State。
- 待巩固：条件边不是“条件满足才允许通过，否则停住”，而是条件函数产生route key，并通过mappings选择一个明确分支；若没有合法映射会报错。

## 问题30：KeyStrategy是什么，为什么每个State key需要合并策略？

### 定义

`KeyStrategy`是`BiFunction<Object, Object, Object>`：输入旧值与节点返回的新值，输出最终写入OverAllState的值。

```text
finalValue = strategy.apply(oldValue, newValue)
```

节点只返回partial update，Graph的`OverAllState.updateState(...)`逐个key查找策略；未注册策略时默认使用Replace。

### 内置策略

- `ReplaceStrategy`：直接返回newValue，适合route、status、answer、计数器和当前结果。
- `AppendStrategy`：把新元素/集合追加到旧List，适合messages、trace和observations；`new AppendStrategy(false)`表示追加时去重。
- `MergeStrategy`：当新旧值都是Map时执行Map合并，同名子key由新值覆盖；适合累计结构化metadata或多分支结果。

### 当前项目

```text
route、answer、historySummary、recentHistory → Replace
messages → AppendStrategy(false)
```

例如旧route为`faq`，节点返回`route=knowledge_base`，最终覆盖为`knowledge_base`；旧messages有两条，节点返回一条新消息，Append后得到三条。

KeyStrategy决定State更新语义，配置错误会造成旧路由残留、消息历史丢失、重复事件或并行分支互相覆盖。

### 状态

- [x] 已讲解并通过综合检测

## 问题31：Node内部如何被注册、调用并更新State？

### Action类型

`NodeAction`是同步函数：

```text
OverAllState → Map<String,Object> partialUpdate
```

`AsyncNodeAction`是异步函数：

```text
OverAllState → CompletableFuture<Map<String,Object>>
```

`AsyncNodeActionWithConfig`额外接收RunnableConfig。Graph内部最终统一按`AsyncNodeActionWithConfig`执行。

### node_async的真实含义

`node_async(syncAction)`只是把同步NodeAction结果包装成CompletableFuture，并把异常放进`completeExceptionally`。它没有自动切换线程，也不会把内部`ChatClient.call()`变成真正非阻塞调用。当前项目多个节点在该包装器内执行同步模型请求，因此Graph的`stream()`主要流式输出“节点完成事件”，并非模型Token流。

### NodeExecutor链路

```text
取得nextNodeId并设为currentNodeId
→ 根据nodeId找到AsyncNodeActionWithConfig
→ interrupt-before检查与NODE_BEFORE监听器
→ action.apply(overallState, runnableConfig)
→ Mono.fromFuture等待节点结果
→ 节点成功返回partial update
→ interrupt-after检查
→ KeyStrategy合并State
→ 根据Edge计算nextNodeId
→ 创建Checkpoint和NodeOutput
→ 继续调度下一节点
```

如果Future异常完成，NodeExecutor走错误事件/异常传播，不会把一个成功partial update正常合并。节点应返回局部更新而不是自行修改共享State，以保持合并、Checkpoint和事件的一致性。

### 状态

- [x] 已讲解，等待检测

# 当前进度与剩余学习路线

## 当前已掌握/已覆盖

- Spring AI定位、ChatClient与ChatModel/Provider调用链。
- Prompt、Message、ChatResponse、call与stream。
- Advisor责任链、Memory Advisor基础。
- Tool Schema、ToolCallback、参数校验、错误分类和有限重试思想。
- ChatMemory、Repository、摘要记忆、Graph State与Checkpoint区别。
- RAG切块、Embedding、混合召回、RRF、Rerank、HyDE和评估指标。
- Spring AI Alibaba StateGraph总体结构，开始学习Edge运行原理。

## 阶段一：Graph Core运行原理（当前阶段）

1. 固定边、条件边及运行时映射。
2. 循环边、recursionLimit与业务attempt上限。
3. 并行边、State合并与并发冲突。
4. KeyStrategy：Replace、Append与自定义Reducer。
5. Node执行、State Update与NodeOutput事件。
6. Checkpoint保存、threadId恢复、interrupt before/after。
7. 子图、状态隔离与父子图数据映射。
8. Flux流、取消、异常传播和资源释放。

## 阶段二：Spring AI核心能力深化

1. ChatClient Bean配置、默认项与请求级覆盖。
2. Advisor顺序、Call/Stream Advisor和自定义Advisor。
3. 结构化输出、转换失败与校验重试。
4. ToolCallingManager、异常处理器、returnDirect和工具上下文。
5. Memory持久化实现、并发一致性和摘要策略。
6. Provider重试、超时、限流、模型切换与Options差异。
7. Micrometer Observability、Trace、Token与成本监控。
8. MCP基础、客户端/服务端、工具发现与安全边界。

## 阶段三：Spring AI Alibaba Agent Framework

1. Agent Framework与Graph Core的关系。
2. ReactAgent内部ReAct循环与退出条件。
3. Sequential、Parallel、Routing、Loop Agent模式。
4. Planner/Executor、动态工具选择和Context Engineering。
5. Tool调用限制、纠错重试、上下文压缩与编辑。
6. Human-in-the-loop、审批、暂停与恢复。
7. Subagent、Supervisor、Handoff和多Agent状态边界。
8. A2A属于进阶选学，不作为当前求职主线必修。

## 阶段四：生产级Agent Harness

1. 身份、租户、权限、Prompt注入与数据泄露防护。
2. 幂等、事务、超时、熔断、退避重试与降级。
3. 多实例会话并发、共享Checkpoint和分布式锁/乐观锁。
4. Prompt、Tool Schema和模型版本管理。
5. RAG/Agent离线评测、线上指标、回归测试和故障回放。
6. 延迟、Token、成本预算和缓存。
7. Docker/部署基础、健康检查和日志告警。
8. 基于当前客服项目完成一次真实工具闭环和集中测试。

## 掌握层级目标

- 面试核心：完成Graph Core、Spring AI深化中的高频项，并能结合项目回答。
- 独立项目：再完成Agent Framework和生产治理的核心项。
- 源码级掌握：持续阅读关键实现、编写自定义扩展并验证边界；这属于长期目标，不能用一次课程宣称“完全掌握”。

## 补充问题：Spring AI能否压缩上下文后再交给ChatModel？

### 结论

可以实现，但当前项目使用的Spring AI 1.1.2没有一个开箱即用、自动完成“旧消息LLM摘要”的标准ChatMemory实现。框架提供ChatMemory、Repository和Advisor等扩展点；`MessageWindowChatMemory`主要执行消息窗口淘汰，而非模型摘要。

可采用两种方式：

1. 自定义ChatMemory或Advisor：超过Token/消息阈值时调用摘要模型，保存summary，并用`summary + recent messages`构造下一次Prompt。
2. 在Graph节点中显式管理：把`historySummary`和`recentHistory`放在State中，节点达到阈值时调用ChatClient生成新摘要。

当前客服项目已经采用第二种方式：`AiReplyNode`保留最近3轮，将更早内容交给`HistoryManager.compressHistory(...)`；该方法通过ChatClient调用ChatModel生成摘要，后续把`historySummary + recentHistory`加入Prompt。

`HistoryManager`位于项目自己的`com.aiagent.config.pipeline`包并标注为`@Component`，不是Spring AI或模型Provider提供的类。大模型只负责根据传入文本生成摘要；压缩触发条件、待压缩范围、摘要Prompt、近期消息保留数量和摘要保存位置均由项目代码控制。

当前项目没有在ChatClient上注册`MessageChatMemoryAdvisor`或`PromptChatMemoryAdvisor`。`historySummary`和`recentHistory`存放在Graph State中，由`AiReplyNode`读取后手动拼入user prompt；ChatClient仅通过`ChatClient.builder(chatModel).build()`创建。因此当前实现是“Graph State手动记忆”，不是“ChatClient挂载Spring AI ChatMemory”。

摘要是有损压缩，不能替代完整聊天存档。工程上应保留原始记录，并考虑摘要幻觉、关键事实丢失、并发更新、Token阈值和失败回退。

### 状态

- [x] 已讲解并记录

## 问题19：RAG的离线建库与在线问答链路是什么？

### 离线建库

```text
原始文档
→ 解析与清洗
→ 分块Chunk
→ EmbeddingModel生成向量
→ 文本、向量和metadata写入VectorStore
```

### 在线问答

```text
用户问题
→ 清洗/改写/拆分查询
→ 向量检索和/或BM25关键词检索
→ TopK、阈值、融合或重排
→ 检索结果作为上下文加入Prompt
→ ChatModel基于问题和证据生成回答
```

Spring AI提供`Document`、`EmbeddingModel`、`VectorStore`、`DocumentRetriever`和`RetrievalAugmentationAdvisor`等通用抽象，但文档质量、切块策略、混合检索、过滤、评估和兜底仍需应用设计。

### 当前项目对应

当前项目使用预切分JSONL；`KnowledgeBaseConfig`加载Document，通过DashScope `text-embedding-v3`生成512维向量并保存到`SimpleVectorStore`本地文件，同时建立Lucene CJKAnalyzer驱动的内存BM25索引。在线阶段由`KnowledgeNode`完成QueryCleaner、QueryRewriter和多查询，`Retriever`执行向量检索与BM25双路检索，再通过RRF融合，结果写入Graph State的`answer`，最后由`AiReplyNode`手动加入Prompt。

因此当前RAG不是直接挂载`RetrievalAugmentationAdvisor`，而是Graph节点显式编排的自定义RAG流水线。

### 状态

- [x] 已讲解，等待检测
- 首次检测：基本掌握。已理解模型返回arguments后需要进行JSON、参数类型和工具约束校验；需补充错误分类，只有可修复的生成错误适合有限重试，权限和不可修复业务错误不能交给模型反复尝试。
- 最终检测：已掌握。能够区分可修复生成错误、缺少真实信息和权限/业务错误，并选择有限重试、询问用户或拒绝/降级。
- 边界修正：无权限必须拒绝，人工处理不能绕过授权；二次确认用于用户有权限但操作风险较高的场景。

## 问题15：哪些Tool参数由模型生成，哪些必须通过ToolContext传入？

### 参数来源分离

模型生成的arguments属于不可信输入，适合表达用户意图中的普通业务参数，例如订单号、查询条件和用户填写的原因。安全身份与执行上下文必须由服务端从可信来源注入，例如：

```text
currentUserId、tenantId、角色权限
sessionId、traceId、requestId
幂等键、内部鉴权信息
```

Spring AI可通过`toolContext(...)`把这些数据放入本地ToolContext，它们不作为普通工具Schema参数交给模型选择：

```java
chatClient.prompt()
        .user(question)
        .tools(orderTools)
        .toolContext(Map.of(
                "currentUserId", authenticatedUserId,
                "tenantId", authenticatedTenantId))
        .call()
        .content();
```

工具方法可读取上下文：

```java
@Tool(description = "取消订单")
public ToolResult<Void> cancelOrder(
        CancelOrderRequest request,
        ToolContext context) {
    String currentUserId = (String) context.getContext().get("currentUserId");
    return orderService.cancel(currentUserId, request.orderId());
}
```

模型只生成`CancelOrderRequest`，不能指定自己以哪个用户或租户身份执行。ToolContext也不能替代Service层的最终权限校验。

### 框架与业务代码的职责边界

Spring AI提供工具Schema生成、arguments解析与Java类型转换、ToolCallback匹配执行、ToolContext传递、ToolResponseMessage组织和正常工具调用循环。Spring生态还可复用Bean Validation、Spring Security、事务和Retry等基础设施。

应用必须定义可信身份从哪里获得、字段与业务规则、权限和资源归属、错误是否可重试、最大次数、降级出口、幂等和审计。框架无法自动知道企业业务规则，因此不是全部从零实现，也不是配置框架后自动获得完整安全策略。

### 状态

- [x] 已讲解，等待检测

## 问题16：工具执行结果为什么通常还要再次发给模型，returnDirect是什么？

### 默认工具链路

工具方法返回的是业务数据，框架通常将其转换为`ToolResponseMessage`并再次调用模型，由模型结合用户问题和工具结果生成最终自然语言回答：

```text
用户问题
→ 模型返回Tool Call
→ Java工具执行
→ ToolResponseMessage
→ 再次调用模型
→ 最终AssistantMessage
```

### returnDirect

`@Tool(returnDirect = true)`表示工具结果直接成为本次调用结果，跳过工具执行后的第二次模型总结。

```java
@Tool(description = "获取结构化订单详情", returnDirect = true)
public OrderResult queryOrder(String orderId) {
    return orderService.query(orderId);
}
```

```text
普通模式：工具结果 → 模型总结 → 用户
returnDirect：工具结果 → 用户/上层程序
```

直接返回可减少一次模型调用、降低延迟和Token消耗，并保持结构化结果确定性；但上层必须能够消费该结果，且需要自行处理展示格式、脱敏与错误，不应把内部对象或敏感字段原样暴露。

### 状态

- [x] 已讲解，等待检测
- 检测状态：用户选择先进入下一题，暂未检测。

## 问题17：ChatMemory、ChatMemoryRepository和Memory Advisor有什么区别？

### 三层职责

```text
MessageChatMemoryAdvisor  接入ChatClient调用链，决定何时读写
ChatMemory                记忆策略，决定保留和淘汰哪些消息
ChatMemoryRepository      存储接口，决定消息保存在哪里
```

`MessageChatMemoryAdvisor`在调用模型前根据conversationId从ChatMemory获取历史并加入Prompt，调用后将本轮消息写回ChatMemory。

`MessageWindowChatMemory`是ChatMemory的一种实现，通过`maxMessages`维持消息窗口并淘汰较旧消息。它解决上下文选择策略，不等于持久化数据库。

`ChatMemoryRepository`负责按conversationId保存、读取和删除消息。`InMemoryChatMemoryRepository`只保存在进程内存中，服务重启后丢失；生产环境可以提供JDBC等持久化Repository实现。

```text
conversationId
→ Advisor请求ChatMemory
→ ChatMemory按窗口策略处理
→ Repository读取/保存Message列表
```

ChatMemory主要服务于模型上下文，不等同于完整聊天档案或跨会话用户画像。完整审计记录和长期记忆通常需要独立存储与提取策略。

### 状态

- [x] 已讲解，等待检测
- 首次检测：基本掌握。知道类型绑定错误发生在Java工具方法调用前；需具体指出`productId`期望String却收到number、`quantity`期望int却收到中文字符串，并注意部分转换器可能进行宽松类型转换，仍需显式校验。
- 第二次检测：基本掌握。能够识别类型转换成功但参数值不可执行的场景；术语需修正为“字段约束或业务语义非法”，而不是格式不匹配。问题13通过，后续进入运行时校验。

## 问题14：Tool参数的运行时校验应该怎样实现？

### 校验分层

```text
DTO字段校验：空值、长度、数值范围、格式
身份与权限校验：userId从服务端上下文获取，不信任模型生成
业务状态校验：库存、订单状态、资源归属
副作用保护：幂等键、事务、并发控制
```

复杂参数建议使用DTO并配合Jakarta Validation；但不能假设ToolCallback参数转换后一定自动触发Bean Validation。可靠做法是在Tool/Service边界显式调用Validator，或调用经过Spring代理并启用方法校验的Service。

```java
public record CreateOrderRequest(
        @NotBlank String productId,
        @Min(1) @Max(99) int quantity) {}
```

工具中先验证，再执行业务方法：

```java
Set<ConstraintViolation<CreateOrderRequest>> violations =
        validator.validate(request);
if (!violations.isEmpty()) {
    return ToolResult.failure("INVALID_ARGUMENT", safeMessage, true);
}
return orderService.create(currentUserId, request);
```

`currentUserId`等安全上下文应由Controller、登录态或`ToolContext`传入，不能使用模型在arguments中自行声明的userId作为授权依据。

### 状态

- [x] 已讲解，等待检测

## 补充问题：Spring AI是否自带工具纠错重试？

### 结论

需要区分三类机制：

1. 模型API瞬时故障重试：Spring AI提供Retry配置，用于部分网络错误、限流或服务端错误。
2. 正常Tool Calling循环：`ToolCallAdvisor`能够在模型返回合法Tool Call后执行工具、追加ToolResponseMessage并再次调用模型，直到模型不再请求工具或工具`returnDirect`。
3. Tool Call纠错重试：未知工具、非法参数、越权和业务失败没有一套默认万能的“自动重试2～3次再转人工”策略，需要应用层显式设计。

在当前Spring AI 1.1.2实现中，未知工具可能由`DefaultToolCallingManager`抛出`IllegalStateException`；已注册工具执行异常可由`ToolExecutionExceptionProcessor`选择转成工具错误结果或继续抛出。即使错误被返回给模型，模型是否重新调用也不确定，仍需控制最大循环次数和最终降级。

```text
网络/API重试 ≠ Tool Calling循环 ≠ 业务纠错重试
```

### 工程建议

- 网络重试仅处理适合重试的瞬时错误，并采用退避策略。
- 未知工具直接拒绝，不通过反射寻找方法。
- 参数格式错误可以给模型有限修复机会。
- 权限、资源归属和不可重试的业务错误不应交给模型反复尝试。
- 在应用或Graph层设置最大迭代次数、超时、错误分类和降级出口。

### 状态

- [x] 已讲解并记录

## 问题12：工具纠错与重试逻辑应该怎样自行实现？

### 错误来源

错误不一定由大模型以“错误响应”返回：

- 模型输出错误：未知工具名、非法或缺失参数。
- 框架错误：ToolCallback匹配、JSON反序列化失败。
- 工具错误：Java方法超时、依赖服务异常。
- 业务错误：无权限、资源不属于用户、订单状态不允许。
- Provider错误：限流、网络超时、服务端5xx。

### 统一处理策略

```text
捕获错误
→ 分类为RETRYABLE / NON_RETRYABLE / TRANSIENT
→ 生成脱敏、结构化错误信息
→ RETRYABLE：将错误作为ToolResponseMessage或纠错上下文反馈模型
→ 增加attempt并重新进入模型决策节点
→ 达到maxAttempts：降级或转人工
→ NON_RETRYABLE：不重试，立即拒绝或转人工
→ TRANSIENT：由网络重试策略处理，不能重复执行已成功的有副作用工具
```

### 推荐Graph状态

```text
toolAttempt      当前纠错次数
maxToolAttempts  最大纠错次数
lastToolError    脱敏错误码与可修复提示
executedCallId   已执行调用标识，用于幂等防重
```

Graph路由：

```text
模型决策 → 工具校验/执行
              ├─ 成功 → 模型总结
              ├─ 可修复且未超限 → 纠错节点 → 模型决策
              └─ 不可修复或超限 → 安全失败/人工节点
```

### 提示方式

优先将已发起Tool Call对应的失败包装成`ToolResponseMessage`，保持工具协议完整；如果错误发生在Tool Call形成之前，可在下一次请求加入受控纠错上下文。只提供模型修复所需的信息，例如错误码、出错字段和允许值，不暴露堆栈、SQL、内部类名或密钥。

### 状态

- [x] 已讲解并记录

## 问题13：Tool参数如何从Java方法变成JSON Schema，再还原为Java对象？

### 参数链路

```text
Java方法与注解
→ Spring AI反射读取方法签名
→ 创建MethodToolCallback
→ 生成ToolDefinition(name、description、inputSchema)
→ Schema随Prompt发送给模型
→ 模型返回AssistantMessage.ToolCall(name、arguments JSON)
→ ToolCallingManager匹配ToolCallback
→ MethodToolCallback解析JSON并构造强类型参数
→ 反射调用Java方法
→ 返回值转换为字符串并包装成ToolResponseMessage
```

示例：

```java
@Tool(description = "根据订单号查询订单")
public OrderResult queryOrder(
        @ToolParam(description = "业务订单号", required = true)
        String orderId) {
    return orderService.query(orderId);
}
```

生成的核心Schema近似为：

```json
{
  "type": "object",
  "properties": {
    "orderId": {
      "type": "string",
      "description": "业务订单号"
    }
  },
  "required": ["orderId"]
}
```

模型返回的`arguments`本质上仍是JSON字符串。`MethodToolCallback`解析为Map并按Java参数类型转换后，才调用真实方法。

### Schema不是安全校验

`required`、类型和description主要用于约束、提示模型正确生成参数，但模型仍可能违反Schema。运行时必须再次验证空值、格式、枚举、长度及业务权限。复杂参数建议封装为DTO，并在Service边界执行明确校验。

### 状态

- [x] 已讲解，等待检测
- 首次检测：部分完成。已正确判断客服长回答应使用`stream()`以逐步展示内容；待回答短文本意图分类应采用何种方式。
- 最终检测：已掌握。能够根据场景选择调用方式：短小且需要完整结果的意图分类使用`call()`，面向用户的长回答使用`stream()`。

## 问题8：什么是结构化输出，为什么不能直接相信模型返回的JSON？

### 作用

业务代码通常需要确定的数据结构而不是自由文本。例如意图路由需要`intent`和`confidence`，Spring AI可将模型响应转换为Java DTO：

```java
record IntentResult(String intent, double confidence) {}

IntentResult result = chatClient.prompt()
        .system("判断用户意图，并按照指定结构返回")
        .user(question)
        .call()
        .entity(IntentResult.class);
```

底层思路是向Prompt补充格式要求，再将模型文本反序列化为Java对象。它提高了工程可用性，但模型仍是概率生成系统，可能返回非法JSON、错误枚举、缺失字段或业务上不合理的数据。

### 工程兜底

```text
模型结构化输出
→ JSON/DTO反序列化
→ 字段与枚举校验
→ 业务规则校验
→ 失败时有限重试、降级默认路由或人工处理
```

重试应限制次数并记录原始响应，不能无限重试。高风险动作不能因为JSON格式正确就直接执行，还需要权限、参数、幂等和业务状态校验。

### 状态

- [x] 已讲解，等待检测
- 首次检测：部分掌握。能识别链路底部是ChatModel调用，但需要理解响应逆序来自嵌套方法调用和调用栈回退。
- 当前结果：讲解后基本理解。响应逆序来源于Advisor嵌套调用与Java调用栈的后进先出，后续结合代码继续复习。

## 问题7：ChatClient的call()与stream()有什么区别？

### call同步调用

```java
String answer = chatClient.prompt()
        .user(question)
        .call()
        .content();
```

当前线程等待模型完整响应，`content()`最终得到一个`String`。适合结构化任务、后台处理或必须拿到完整结果后才能继续的业务逻辑。

### stream流式调用

```java
Flux<String> answer = chatClient.prompt()
        .user(question)
        .stream()
        .content();
```

模型生成内容时分片返回，Spring AI用Reactor `Flux<String>`表示0到多个异步片段。Web层通常通过SSE把片段持续推送给浏览器。

```text
模型片段 → Provider流 → Flux → SSE → 浏览器逐步显示
```

流式输出通常改善首字等待时间和用户体验，但不代表模型总推理时间一定缩短。流已经开始后发生错误，HTTP响应头可能已经发送，通常需要通过流内错误事件或连接结束表达，不能总是改成普通HTTP错误状态。

### 状态

- [x] 已讲解，等待检测
