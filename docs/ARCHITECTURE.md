# Agent 系统架构约定

本文档记录 `D:\agent` 的长期设计原则。后续新增业务功能、模型能力和多 Agent 协作流程时，应以此为默认架构约束。

## 1. 总体思想

项目参考 `spring-ai-examples-main` 的设计思想，但不照搬示例项目的简化代码：

- 按能力和职责拆分模块，每个组件只承担一种主要职责。
- AI 模型与业务逻辑解耦，业务代码依赖抽象，不直接绑定具体模型服务商。
- Agent 工作流使用普通 Java 类显式编排，避免把全部流程隐藏在一个巨大 Prompt 中。
- Prompt、工具、记忆、模型配置和工作流彼此分离，可以独立替换和测试。
- 模型输出优先转换为 Java `record` 等强类型对象，不手工截取或拼接 JSON。
- 日志、记忆、工具调用、评估、重试等横切能力通过可插拔组件接入。
- 测试验证流程目标、状态变化和结构约束，不依赖模型逐字返回固定内容。

## 2. 分层结构

系统采用前后端分离部署：`frontend` 只依赖 REST API，不读取 Thymeleaf 模型；Spring Boot 不承载页面模板。

```text
frontend/src
├── views          页面级用例编排
├── components     可复用界面组件
├── services       API、CSRF 和 Cookie 凭证适配
├── stores         Pinia 全局认证状态
├── router         页面路由和登录守卫
└── assets         全局样式
```

```text
com.example.agent
├── config                Spring Bean、Security 与配置属性
├── constant/enums        全局枚举和稳定常量
├── controller            HTTP 协议适配、参数校验、AO/VO 转换
├── exception             业务异常定义
├── model
│   ├── entity            与 MySQL 表映射的 JPA 实体
│   ├── dto               外部请求数据，按 auth/agent 模块拆分
│   ├── ao                Controller 传给 Service 的应用对象
│   ├── bo                Service 对外输出的业务对象
│   └── vo                返回给 Vue 的视图对象
├── repository            Spring Data JPA 数据访问接口
├── security              登录主体、认证查询和密码编码
├── service               业务能力接口与 LangChain4j Agent 接口
│   └── impl              业务实现、事务和持久化协作
├── tools                 Agent 可调用的工具
└── utils                 无状态、无业务依赖的通用辅助方法
```

依赖方向：

```text
Controller -> Service interface -> AO/BO
Controller -> DTO/VO
Service impl -> Repository -> Entity
Service impl -> LangChain4j/SMTP/Music vendor adapters
```

具体约束：

- Controller 不直接调用大模型、Repository 或 SMTP，不注入或返回 Entity。
- Controller 请求必须使用明确的 DTO，禁止使用 `HashMap` 代替请求模型。
- Controller 将 DTO 转为 AO，将 Service 返回的 BO 转为 VO；DTO 和 Entity 不跨层混用。
- `service` 根包只放业务接口或 Agent 能力接口，具体实现统一放在 `service.impl`。
- Service 接口不能依赖 Controller、DTO、VO、Repository 或 Entity。
- Service 实现负责业务规则、事务边界和 Repository 协作；Service 之间通过接口复用。
- Repository 只操作 Entity。当前使用 Spring Data JPA，因此保留 `repository` 命名，不额外创建 MyBatis `mapper`。
- Entity 使用 Lombok `@Getter`、`@NoArgsConstructor(PROTECTED)` 和受控 `@Builder` 消除样板代码；
  业务代码必须通过实体的静态工厂初始化，禁止在 JPA Entity 上使用 `@Data` 或公开 Setter。
- Security 的用户认证查询属于基础设施适配，可以直接读取 Repository，但不能被 Controller 当作业务查询服务使用。
- 不创建没有实际职责的 `annotation`、`aspect`、`filter`、`mapper` 等空包；出现对应能力时再增加。
- Vue 组件不拼接后端地址、CSRF 头或 Cookie 策略，统一通过 `services/api.js` 访问 API。
- 前端路由守卫只改善交互体验，最终权限必须由 Spring Security 和资源归属校验决定。
- Agent Workflow 负责协作顺序，不负责 HTTP 和数据库细节。
- 音乐推荐编排依赖 `MusicQueryPlanner` 和 `MusicCatalogProvider` 接口；规划器输出结构化
  `MusicSearchPlan`，曲库参数和播放协议封装在 QQ Music、Jamendo、Audius、YouTube 适配器中，不能泄漏到通用推荐模型。

当多 Agent 功能增长到需要独立模块时，在现有结构中增加 `agent.workflow`、`agent.role`、
`agent.memory` 和 `agent.protocol`。这些模块仍通过 Service 接口进入业务流程，不能直接依赖 Controller。

## 3. 多 Agent 协作模型

默认采用“编排者负责流程，专业 Agent 负责能力”的模式：

```text
User Request
    -> Router
    -> Orchestrator
    -> Worker Agent(s)
    -> Evaluator
    -> Synthesizer
    -> Final Result
```

角色职责：

- `Router`：识别任务类型，选择工作流或专业 Agent。
- `Orchestrator`：拆解任务、确定依赖关系、分配子任务，不亲自完成所有工作。
- `Worker`：只处理其能力范围内的子任务，返回结构化结果和证据。
- `Evaluator`：依据明确标准检查结果，不直接篡改原结果。
- `Synthesizer`：合并已通过评估的结果，处理冲突并形成最终答复。
- `Reflection Agent`：根据评估反馈迭代改进，受最大迭代次数限制。

对于固定步骤使用 Chain；独立任务使用 Parallel；输入类型明显不同使用 Routing；任务无法预先拆解时使用 Orchestrator-Workers；质量要求高时增加 Evaluator-Optimizer 或 Reflection。

## 4. Agent 通信契约

Agent 之间禁止只传递无法验证的自由文本。任务和结果至少包含以下字段：

```text
AgentTask
- taskId
- parentTaskId
- traceId
- userId
- conversationId
- targetRole
- objective
- input
- constraints
- expectedOutputSchema
- deadline
- attempt

AgentResult
- taskId
- agentRole
- status
- output
- evidence
- warnings
- errorCode
- retryable
- startedAt
- completedAt
```

状态统一使用 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。业务代码根据状态和错误码决策，不通过分析自然语言判断执行是否成功。

## 5. 执行与安全约束

- 每个任务必须携带 `userId` 和 `traceId`，保证用户隔离和全链路追踪。
- 工具权限按 Agent 角色最小化授权；只读 Agent 不获得写入能力。
- 外部写操作必须具备幂等键，重试不能造成重复数据或重复通知。
- 每个模型调用设置超时、最大重试次数和可观察的失败原因。
- 并行 Worker 必须限制并发数，汇总阶段必须处理部分成功和超时。
- Reflection 和 Evaluator 循环必须设置最大迭代次数，防止无限调用。
- Prompt 和工具输入不得包含密码、SMTP 授权码、API Key 或验证码明文。
- 曲库 API Key 只从服务端环境读取，不写入 MySQL、浏览器持久存储或日志。开放曲库不要求用户绑定
  第三方账号；个人 QQ 音乐登录态仅在用户主动导入后使用，由本机随机密钥加密保存并且不返回前端。
  音频由曲库 HTTPS 地址或 YouTube 官方 IFrame 直连，应用不缓存媒体。
- 模型输出进入业务系统前必须完成结构校验、长度限制和权限校验。
- 对话记忆按用户、会话和工作流隔离，不使用全局共享可变记忆。
- 浏览器 HTTP Session 只负责登录状态，不作为 Agent 对话标识；Agent 记忆键必须由 `userId + conversationId` 组成。
- `conversationId` 使用 UUID。新建对话窗口必须生成新 UUID；相同用户的不同 UUID 之间不得读取或复用消息。
- 即使两个用户提交相同 `conversationId`，后端也必须通过 `userId` 前缀保证记忆完全隔离。
- 会话元数据和消息历史持久化到 MySQL；会话列表查询必须包含 `userId` 和逻辑删除条件，消息查询前必须验证会话归属。
- LangChain4j 首次加载某个会话的内存时，从该会话最近的持久化消息恢复上下文，不能从其他会话复制记忆。

## 6. 可测试性

- Workflow 使用接口接收模型和工具能力，单元测试可替换为确定性假实现。
- 测试 Agent 路由选择、任务拆解、状态转换、超时、重试和部分失败。
- 结构化输出必须覆盖缺字段、非法枚举、格式错误和模型拒答场景。
- 集成测试验证 MySQL、Session、模型适配器和工具调用边界。
- 端到端测试验证目标行为，不要求真实模型输出逐字一致。

## 7. 后续开发准则

新增 Agent 前先明确角色、输入、输出、可用工具、权限、超时和失败策略。只有当新 Agent 具备独立职责或独立扩展价值时才创建；简单的固定步骤优先作为现有 Workflow 的普通 Java 方法或 Tool，避免无意义地增加 Agent 数量。
