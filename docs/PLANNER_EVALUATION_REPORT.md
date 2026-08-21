# 通用多意图规划器离线评测报告

评测日期：2026-08-21

语料版本：`planner-evaluation-corpus.json`（18 条）
运行环境：Java 17 / JUnit 5 / Spring Boot 3.5.16

## 结果

| 指标 | 结果 | 分子 / 分母 | 口径 |
| --- | ---: | ---: | --- |
| Goal 分解准确率 | 100.00% | 18 / 18 | Goal 顺序、operation/target 及关系类型多重集完全匹配 |
| 计划可编译率 | 100.00% | 18 / 18 | 真实经过 Capability Registry、PlanValidator 和 PlanCompiler |
| 目标完成率 | 100.00% | 4 / 4 | 真实满足的受控执行目标均被报告完成 |
| 错误成功率 | 0.00% | 0 / 5 | 工具错误、超时、部分失败、拒绝确认和无状态变化均未被报告完成 |

以上是离线受控基线，不代表线上自然流量准确率。阶段十四应在 shadow mode 继续采集真实请求分布，并按同一
口径建立时间窗口、置信区间和回归阈值。

## 基准集

- 单意图 6 条：歌曲搜索、歌手资料、场景推荐、歌单搜索、画像分析和 Unicode 实体。
- 双意图 6 条：串行、并行、条件、副作用、分别执行和画像实体解析。
- 三到六意图 6 条：包含 3、4、5、6 Goal 的混合依赖图。
- 结构关系完整覆盖 `SEQUENCE / PARALLEL / CONDITIONAL / DEPENDS_ON`。

## 可靠性与安全覆盖

| 风险 | 验证位置 | 预期结果 |
| --- | --- | --- |
| 空画像、并列候选 | `FavoriteArtistResolverTest` | 不猜测，返回澄清 |
| 实体别名 | `TaskEvaluatorTest` | 已声明 alias 可与规范实体证据匹配 |
| 同名歌手 | `TaskEvaluatorTest` | provider/entityId 不同则 `REPLAN` |
| 错误工具结果 | `TaskEvaluatorTest` | 畸形 Schema 返回 `REVISE` |
| 超时、部分失败 | `GenericDagExecutorTest` | 超时失败，下游跳过，独立成功分支不回滚 |
| 用户拒绝确认 | `GenericDagExecutorTest` | 副作用不执行，分支标为跳过 |
| 暂停与恢复 | `GenericDagExecutorTest` | 快照恢复后沿用身份、输入和幂等键 |
| 局部重规划 | `GenericDagExecutorTest`、`BoundedReplannerTest` | 只替换失败子图，保留已验收结果 |
| 计划属性 | `PlanSchemaPropertyTest` | 128 个随机合法 DAG 全部有界且拓扑正确 |
| 循环依赖攻击 | `PlanSchemaPropertyTest` | 80 个生成式循环图全部拒绝 |
| 越权与伪造能力 | `PlanValidatorCompilerTest` | 跨用户画像、未授权副作用、伪造能力全部拒绝 |
| 原始请求透传 | `PlanSchemaPropertyTest`、`PlanValidatorCompilerTest` | 64 个随机标记及显式污染计划全部拒绝 |

## 复现

```powershell
$env:NEO4J_PASSWORD=$null
.\mvnw.cmd test
```

本次全量结果：326 tests，0 failures，0 errors，4 skipped。跳过项是 2 项当前未启用外部服务的 Neo4j 集成
测试，以及需要显式开启真实模型调用的 `GlmModelSmokeTest`、`MusicAgentSmokeTest`；不影响本阶段离线评测
与安全回归。
