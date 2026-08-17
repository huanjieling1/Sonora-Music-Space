# Sonora QQ Music Bridge

本机 Bridge 参考 [Rain120/qq-music-api](https://github.com/Rain120/qq-music-api) 的
`getSearchByKey` 与 `getMusicPlay` 实现，只保留 Sonora 需要的能力。

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
- `QQ_MUSIC_VKEY_SIGN`：上游签名覆盖值；默认沿用参考项目当前签名。
- `QQ_MUSIC_EDGE_PATH`：可选，自定义 Microsoft Edge 可执行文件路径；默认使用系统安装的 `msedge`。
- `QQ_MUSIC_BROWSER_PROFILE_DIR`：可选，专用 Edge 配置目录；默认位于已忽略的 `runtime-data/qq-music-edge-profile`。

登录任务仅保存在内存中，默认 5 分钟过期。Bridge 重启、用户取消或登录成功后都会关闭独立 Edge 窗口；专用配置目录只用于后续自动恢复 QQ 音乐登录。
