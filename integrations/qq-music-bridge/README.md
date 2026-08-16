# Sonora QQ Music Bridge

本机 Bridge 参考 [Rain120/qq-music-api](https://github.com/Rain120/qq-music-api) 的
`getSearchByKey` 与 `getMusicPlay` 实现，只保留 Sonora 需要的能力。

- 仅监听 `127.0.0.1`，不启用 CORS。
- 不保存、不打印 QQ 音乐 Cookie；Cookie 由 Spring Boot 在单次请求中通过内部 Header 传入。
- 使用 Node.js 18+ 内置能力，不安装第三方 npm 包。
- 私有上游接口可能变更；Jamendo、Audius 与 YouTube 仍作为应用兜底。

启动：

```powershell
npm start
```

环境变量：

- `QQ_MUSIC_BRIDGE_PORT`：监听端口，默认 `3200`。
- `QQ_MUSIC_VKEY_SIGN`：上游签名覆盖值；默认沿用参考项目当前签名。
