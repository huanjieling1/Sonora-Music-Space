# Sonora 全局界面融合记录

更新日期：2026-08-16

## 选取的 GitHub 参考

| 项目 | 取其经验 | 在 Sonora 中的落点 |
| --- | --- | --- |
| [Open WebUI](https://github.com/open-webui/open-webui) | 深色统一外壳、窄侧栏、居中输入区、弱化边界 | 全局改为炭黑画布；会话侧栏收窄；输入框固定为居中胶囊形 |
| [LobeChat](https://github.com/lobehub/lobe-chat) | 大面积呼吸空间、轻层级、工具入口克制 | 新会话使用留白欢迎区和三枚任务建议；减少大白卡与重复标题 |
| [Chatbox](https://github.com/chatboxai/chatbox) | 桌面 AI 工作区的清晰导航和紧凑操作 | 统一 72px 顶栏、会话图标、用户区与面板开关 |
| [YesPlayMusic](https://github.com/qier222/YesPlayMusic) | 专辑封面驱动、低装饰曲目列表、固定播放上下文 | 音乐推荐改为封面行；底部播放器保持可见；专辑图生成环境光 |
| [Feishin](https://github.com/jeffvli/feishin) | 高信息密度下仍保持稳定列和明确播放层级 | 音乐面板使用固定宽度；曲名、艺人、时长和操作保持稳定列 |

这些项目只用于提炼布局、视觉层级和交互原则；没有直接复制其界面代码或品牌资产。

## 从原界面识别的问题

- 白色 Agent 区与黑色音乐区像两个产品拼接，色彩和圆角体系不一致。
- 左侧与音乐侧栏都偏宽，中间内容却没有明确焦点，超宽屏空白失去节奏。
- 欢迎消息使用大型边框卡片，输入区和主要动作缺少视觉中心。
- 音乐错误直接显示英文 SDK 原文，打断整体语气。
- 顶栏、侧栏、音乐面板的标题高度和信息密度不一致。

## 已融合的设计系统

- 颜色：`#0b0d11` 画布、炭黑分层表面、紫色主动作、薄荷色连接状态。
- 间距：以 8px 为基础节奏；主顶栏统一为 72px；内容宽度控制在约 820px。
- 形态：面板只用细边界分层；卡片圆角 18–28px；小操作使用 10–13px 圆角。
- 层级：页面只保留一个主要动作；次级操作降为图标或文字按钮。
- 响应式：1200px 收窄列宽，980px 音乐面板变为覆盖层，720px 会话侧栏折叠为顶部条。
- 错误语气：隐藏无意义的 Spotify `no list was loaded` 原文，其余错误转为可关闭的中文提示。

## 代码落点

- `frontend/src/assets/main.css`：全局令牌、认证页、工作区、聊天、音乐与响应式系统。
- `frontend/src/views/AgentView.vue`：工作区标题、空状态、建议入口、音乐面板开关与新输入区。
- `frontend/src/components/ConversationSidebar.vue`：品牌、紧凑会话导航和用户信息。
- `frontend/src/components/ChatMessage.vue`：轻量消息层级。
- `frontend/src/components/MusicPanel.vue`：情绪快捷项、封面列表、播放器与友好错误处理。
- `frontend/src/layouts/AuthLayout.vue`：统一的 Sonora 登录/注册视觉入口。

## 后续优化准则

1. 新增组件前先复用现有颜色、圆角和间距令牌。
2. 每个区域只保留一个高对比主操作。
3. 桌面、980px 和 390px 三个视口都完成视觉检查后再交付。
4. 新增音乐能力不能破坏固定播放器和曲目列稳定性。
5. 用户可见错误必须是可理解、可恢复的中文说明。
