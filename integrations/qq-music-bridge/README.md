# Sonora QQ Music Bridge

本机 Bridge 为 Sonora 提供 QQ 音乐登录、检索、元数据和播放地址解析能力。

- 仅监听 `127.0.0.1`，不启用 CORS。
- 使用独立 Microsoft Edge 配置打开 QQ 官方登录页面；登录结果只在 Bridge 与 Spring Boot 的回环连接中传递。
- 只提取 QQ 域下必要的 QQ 音乐会话字段，不打印 Cookie；Spring Boot 接收后立即加密保存，并销毁 Bridge 中的一次性登录任务。
- 使用 Node.js 20+ 与 `playwright-core` 控制本机已经安装的 Edge，不下载额外浏览器。
- 私有上游接口可能变更；Jamendo、Audius 与 YouTube 仍作为应用兜底。

启动：

```powershell
npm start
```

环境变量：

- `QQ_MUSIC_BRIDGE_PORT`：监听端口，默认 `3200`。
- `QQ_MUSIC_VKEY_SIGN`：可选的播放签名覆盖值。
- `QQ_MUSIC_EDGE_PATH`：可选，自定义 Microsoft Edge 可执行文件路径；默认使用系统安装的 `msedge`。
- `QQ_MUSIC_BROWSER_PROFILE_DIR`：可选，专用 Edge 配置目录；默认位于已忽略的 `runtime-data/qq-music-edge-profile`。

登录任务仅保存在内存中，默认 5 分钟过期。Bridge 重启、用户取消或登录成功后都会关闭独立 Edge 窗口；专用配置目录只用于后续自动恢复 QQ 音乐登录。

## QQ 官方榜单接口

Bridge 还把 QQ 音乐榜单能力标准化为两个只读接口：

- `GET /charts`：读取 QQ 音乐当前公开的榜单目录、分组、期次与更新时间。
- `GET /chart?id=26&period=2026-08-18&offset=0&limit=20`：读取指定榜单期次的真实曲目、官方名次、名次变化、歌手与可播放元数据。

榜单数据的 `sourceType` 固定为 `QQ_OFFICIAL`。Bridge 不会把关键词搜索结果伪装成热度榜，也不会自行生成 QQ 官方热度分。Spring Boot 会保存按期榜单快照，并在其上计算最近、近一周、近一月和已积累区间的歌手/歌曲趋势；这类聚合结果会明确标记为 `SONORA_DERIVED_FROM_QQ_CHARTS`，并返回实际数据覆盖时间。

第三方代码与许可证信息统一记录在仓库根目录的 `THIRD_PARTY_NOTICES.md`。
