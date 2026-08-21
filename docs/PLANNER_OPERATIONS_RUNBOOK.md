# 通用规划器观测与上线运行手册

## 当前状态

默认 `PLANNER_ROLLOUT_MODE=SHADOW`。主聊天入口已接入唯一的 `PlannerCutoverCoordinator`：SHADOW 下旧
`MusicAgentRoute` 继续真实执行，动态规划器只生成 Goal Graph、编译 Plan、输出脱敏事件并进行 shadow
对比；READ_ONLY 下纯查询计划由通用 DAG 独占执行，含副作用的计划仍由旧链独占执行。

## 配置

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `PLANNER_ROLLOUT_MODE` | `SHADOW` | `SHADOW / READ_ONLY / FULL` 三级发布 |
| `PLANNER_FALLBACK_TO_LEGACY` | `true` | 未获准动态执行时返回旧路由回退决策 |
| `PLANNER_KILL_SWITCH` | `false` | 紧急停止所有新的动态执行 |
| `PLANNER_TASK_COUNT_ALERT_THRESHOLD` | `20` | Compiled Plan 任务数量告警阈值，最大 24 |
| `PLANNER_EVENT_HISTORY_LIMIT` | `500` | 单进程最近事件/告警有界窗口 |

配置变更后按项目标准方式重启后端。紧急回退时设置：

```properties
PLANNER_KILL_SWITCH=true
PLANNER_FALLBACK_TO_LEGACY=true
```

回退不需要恢复旧版本：旧主链始终保留，动态入口收到 `LEGACY_FALLBACK` 后不得执行工具。只有执行前的
分解、合成或编译失败可以回退；一旦动态 DAG 开始执行，运行期失败必须原样失败，禁止再调用旧链，以免
播放、入队或收藏等动作重复。

## 主聊天切流语义

1. 每个请求先按 `userId + conversationId` 查找 `WAITING_USER` 工作流；存在时直接恢复，不再重新做意图分类。
2. 无等待工作流时完成旧意图理解，再由通用分解器生成任意多 Goal Graph 并执行无副作用的 prepare。
3. `SHADOW_ONLY / LEGACY_FALLBACK` 返回旧链；`EXECUTE` 只进入通用 DAG；`BLOCKED` 明确阻断。
4. 通用任务产生的旧 UI 卡片按 task ID 暂存，只有任务结果通过 evaluator 验收后才回传，并在单次响应后清空。
5. `WAITING_USER` 的原始回合、Goal Graph 和跟进计划持久化到 MySQL；用户画像不入库，进程重启后恢复时重新读取。

## 发布顺序

1. `SHADOW`：观察分解/编译率、shadow 差异和四类告警；不执行动态工具。
2. `READ_ONLY`：只让所有 capability 都是 `READ_ONLY` 的计划进入通用 DAG；含播放、队列、收藏等计划继续走旧路由。
3. `FULL`：只在前两阶段指标稳定后开放；副作用仍必须经过确认、权限和幂等闸门。
4. 任一阶段出现错误成功、重复副作用或循环告警，先启用 kill switch，再定位对应 workflow/plan/task ID。

## 结构化事件

- `GOAL_GRAPH`：graphId、Goal 结构、关系数量、请求哈希和长度。
- `COMPILED_PLAN`：planId、任务/阶段数量、能力、依赖和脱敏输入来源。
- `TASK_STARTED / TASK_FINISHED`：workflow/task、尝试次数、sideEffect、状态、错误码和 `durationMillis`。
- `TASK_EVALUATION`：`PASS / REVISE / REPLAN / ASK_USER / FAIL`、finding codes 和原因哈希。
- `REPLAN`：失败任务、错误码、替换范围、保留数量、结果和原因哈希。
- `ROLLOUT_DECISION`：`SHADOW_ONLY / EXECUTE / LEGACY_FALLBACK / BLOCKED`。

禁止在这些事件中增加原始请求、用户输入值、画像值、Cookie、密钥或 provider 原始响应。

## 告警

| 告警 | 含义 | 首要动作 |
| --- | --- | --- |
| `ABNORMAL_TASK_COUNT` | 计划接近任务硬上限 | 检查 Goal 重复分解和异常展开 |
| `RAW_REQUEST_FORWARDING` | 原始请求被绑定到能力参数或验证器检出污染 | 阻断计划，检查输入绑定规则 |
| `DUPLICATE_SIDE_EFFECT` | 已完成的幂等键再次启动 | 立即 kill switch，核查恢复/重规划路径 |
| `WORKFLOW_CYCLE` | 编译循环或运行时无 READY 任务 | 阻断计划，检查关系和局部替换边界 |

所有告警只包含标识、错误码、计数和哈希，不包含敏感原值。

## 验证

```powershell
$env:NEO4J_PASSWORD=$null
.\mvnw.cmd test
```

主聊天接入基线：340 tests，0 failures，0 errors，2 skipped。
