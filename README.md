# GitHub 音乐应用设计研究

本目录保存对 GitHub 开源音乐应用的持续设计研究，重点不是收集项目数量，而是提炼“视觉好看、简约、能落地”的设计方法。

## 先看什么

- [music-app-design-study.md](music-app-design-study.md)：核心结论、项目评分、设计规范与反模式。
- [ITERATION_LOG.md](ITERATION_LOG.md)：每轮研究如何筛选、复盘和优化。
- `research_assets/`：从各项目官方仓库取得的界面截图，仅用于视觉研究；版权归原项目及素材权利人。

## 当前结论

最值得组合借鉴的不是某一个完整产品，而是：

1. YesPlayMusic 的留白、内容层级和双栏歌词页。
2. SPlayer 的专辑色氛围与沉浸式播放页。
3. Retro Music Player 的移动端大触控区与 Material You 动态色。
4. Feishin 的高密度媒体库能力和稳定桌面框架。
5. Swing Music 的大标题、低装饰和主操作聚焦。

研究快照日期：2026-08-16（Asia/Shanghai）。GitHub 星标和维护状态会变化，以仓库实时数据为准。

## Sonora 多曲库配置

音乐推荐现已改为应用级多曲库，不再要求用户绑定 Spotify 或其他音乐账号。

```dotenv
JAMENDO_CLIENT_ID=
AUDIUS_API_KEY=
YOUTUBE_API_KEY=
QQ_MUSIC_ENABLED=true
QQ_MUSIC_BRIDGE_URL=http://127.0.0.1:3200
QQ_MUSIC_SESSION_DIRECTORY=runtime-data
QQ_MUSIC_DEFAULT_QUALITY=flac
```

- Jamendo 与 Audius 至少配置一个，负责可直接播放的完整音频。
- YouTube 为可选补充，只在直接音频不足时通过官方可见 IFrame 补位。
- 密钥仅由 Spring Boot 后端读取，浏览器只接收标准化后的曲目数据。
- `GET /api/music/status` 返回曲库配置与可用状态；`POST /api/music/recommend` 支持最多 20 页、
  每页最多 10 首的真实曲库分页，同时继续兼容旧的 `limit` 字段。推荐请求必须绑定当前 Agent 会话；
  后端只从登录主体取得 `userId`，不会接受客户端代传账号。

```json
{
  "conversationId": "当前会话 UUID",
  "description": "适合专注工作的电子音乐",
  "page": 1,
  "pageSize": 10
}
```

响应会返回 `searchId`（同时是服务端曝光 ID）、`policyVersion`、`personalizationStatus`、
`page`、`pageSize`、`hasNext` 和 `maxPages`。歌曲还包含 `reasonCodes`、`reasonText` 和
`exploration`；继续加载时保持描述和 `conversationId` 不变并递增页码。

首次克隆后请从脱敏模板创建本机配置，再填写你自己的数据库、邮件和 API 凭据：

```powershell
Copy-Item .env.example .env
Copy-Item src/main/resources/application.example.yml src/main/resources/application.yml
```

`.env` 与 `src/main/resources/application.yml` 都已加入 `.gitignore`，以后修改本机配置时不会被提交。
配置完成后重启后端即可生效。

## 个性化推荐闭环

当前实现保持 QQ、Jamendo、Audius、YouTube 在线曲库不变，在 Spring Boot 内加入纯 Java 推荐工作流：

1. 将模型输出校验为结构化执行计划，提取歌曲、艺人、专辑、风格、情绪、场景和避开项。
2. 按登录用户与当前会话加载 L1 显式偏好、L2 行为推断、L3 会话场景和近期负反馈。
3. 并行融合在线曲库、Neo4j 标签图召回和 512 维 `embedding-3` 向量召回。
4. 使用 weighted RRF（`k=60`）融合；内容分固定由语义 45%、结构化匹配 30%、RRF 25% 构成。
5. 当前请求硬约束和显式不喜欢先过滤；画像、新鲜度、冷门和过曝的总调整限制在 `±0.08`。
6. 粗排后用 Thompson Sampling 保留约 15% 探索位，再以 MMR（`λ=0.7`）控制艺人和标签重复。
7. 返回前同步写入 MySQL 曝光快照；播放、跳过、播完、喜欢、收藏等事件只能引用本人真实曝光。
8. MySQL Outbox 将歌曲、行为和偏好可靠投影到 Neo4j；图或向量不可用时自动退回曲库排序。

MySQL 是唯一事实源。原始行为保留为 L0；用户编辑项为永久 L1；L2 至少需要 3 条事件、2 个曝光、
置信度达到 0.70，有效期 30 天；L3 仅在当前会话生效 24 小时。无操作的曝光不作为负样本，
`NOT_RELEVANT` 也只代表当前会话场景不匹配。

### 行为与画像 API

- `POST /api/music/events`：提交 `eventId`、`searchId`、`trackId`、`eventType` 和可选 `playbackMs`。
- `POST /api/music/feedback`：实体纠错和会话范围的 `NOT_RELEVANT`。
- `GET /api/music/profile`：查看显式与推断偏好、证据、置信度和过期时间。
- `POST /api/music/profile/preferences`、`DELETE /api/music/profile/preferences/{id}`：编辑 L1 偏好。
- `DELETE /api/music/profile/learned`：清除 L2/L3 学习画像。
- `GET /api/music/policy/status`：查看激活策略、样本量和 Neo4j/向量状态。

每个行为事件使用客户端 UUID 幂等；位置、特征和策略版本一律从服务端曝光快照反查。播放不足 2 秒
不会记为跳过，播放达到 90% 才能记为播完。

## 音乐工作台与歌单

登录后访问 `http://127.0.0.1:5173/music`，可以使用独立音乐首页、场景推荐卡片、歌单宝藏库和
跨歌单底部播放器。推荐卡会调用现有个性化工作流生成真实歌曲并保存成歌单；进入 `/music/playlists/{id}`
可查看、播放、切歌和删除可编辑歌单中的歌曲。Agent 右侧音乐面板也提供“保存歌单”和“音乐库”入口。

- `GET /api/music/playlists`：列出本人歌单，并同步“我喜欢的音乐”和“最近播放”。
- `POST /api/music/playlists`：创建空白自建歌单。
- `POST /api/music/playlists/recommended`：按场景执行个性化推荐并保存歌单。
- `POST /api/music/playlists/from-exposure`：把一次真实推荐整体保存为歌单。
- `POST /api/music/playlists/{id}/open`：打开歌单并生成新的可信曝光，返回可播放歌曲。
- `PATCH/DELETE /api/music/playlists/{id}`：编辑或删除本人可编辑歌单。
- `POST /api/music/playlists/{id}/tracks`、`DELETE /api/music/playlists/{id}/tracks/{itemId}`：增删歌曲。

歌单保存曲源标识和歌曲元数据快照，不缓存完整音频或易过期的播放 URL。QQ 播放地址仍在点击时刷新，
Jamendo/Audius 使用浏览器 Audio，YouTube 使用官方可见 IFrame。每次打开歌单都会生成新的服务端曝光，
因此旧歌单中的播放、跳过、播完和喜欢事件仍可安全参与画像学习。

## 本机 Neo4j 5.26 环境

本工作区使用 Neo4j Community `5.26.29` ZIP，不需要 Docker、Python 或 GPU：

- 程序：`D:\agent\runtime-tools\neo4j`
- 数据：`D:\agent\runtime-data\neo4j\data`
- 日志：`D:\agent\runtime-data\neo4j\logs`
- Browser/Bolt：仅监听 `127.0.0.1:7474` 和 `127.0.0.1:7687`

Neo4j 密码保存在当前 Windows 用户环境变量 `NEO4J_PASSWORD`，不会写入仓库或日志；其余配置见
`.env.example`。`run-dev.ps1` 会依次检查 MySQL、幂等启动 Neo4j、QQ Bridge、Spring Boot 和 Vue，
已监听的组件不会重复启动。Neo4j/向量索引未就绪时，页面会显示“个性化降级”，搜索和播放仍可用。

离线策略学习只使用明确标签，至少需要 100 个标签和 20 个曝光。候选策略通过时间切分 NDCG@10
和用户分段守卫后只会标记为 `PASSED`，不会自动上线；将已验证版本写入
`MUSIC_RANK_POLICY_ACTIVE_VERSION` 并重启即可切换，改回 `baseline-v1` 即回滚。

### 个人 QQ 音乐接入

`run-dev.ps1` 会在 `127.0.0.1:3200` 自动启动零第三方依赖的 QQ 音乐 Bridge。
登录 Sonora 后打开左下角“设置 → QQ 音乐”，点击“打开 QQ 登录窗口”，然后在独立的 Microsoft Edge
窗口中使用手机 QQ 扫码或完成账号登录。Sonora 会从 QQ 官方网页登录上下文中自动提取必要的 QQ 音乐
会话字段，不需要抓包或手动复制 Cookie。登录凭据经
AES-GCM 加密后保存在 `runtime-data/`，不会返回浏览器、写入日志或提交到 Git。

- QQ 音乐优先参与歌曲、歌手、专辑和中文主流内容检索。
- 播放地址仅在点击歌曲时获取，并按无损、320K、128K、M4A 自动降级。
- 当前账号无播放权益、登录态过期或私有接口失效时，现有开放曲库和 YouTube 仍可继续使用。
- 本功能只绑定回环地址，用于本机个人环境；登录态不要截图或发送给任何人。

## 智能音乐检索

推荐请求不再直接转换成一条模糊关键词。后端先生成结构化 `MusicSearchPlan`，识别精确歌曲、
歌手、专辑、场景推荐、相似音乐和模糊实体，再生成最多 5 条搜索任务并行查询曲库。

- 明确的歌名、歌手和专辑会进入不同检索路线；Jamendo 使用对应的 `namesearch`、
  `artist_name`、`album_name` 或 `xartist` 参数。
- 中文风格、情绪和使用场景会映射为曲库更容易理解的英文音乐词汇。
- 候选结果按歌曲名、歌手、专辑、关键词覆盖、可播放类型和版本词确定性排序，并在排序后去重。
- 模型不可用时自动使用本地规则规划器；单个曲库失败或超时不影响其他曲库结果。
- 裸实体无法可靠判断类型时标记为 `AMBIGUOUS`，不会擅自虚构歌手或歌曲信息。
