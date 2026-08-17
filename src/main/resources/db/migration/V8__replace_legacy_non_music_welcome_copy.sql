-- 将早期通用 Agent 欢迎语替换为纯音乐定位；仅处理已知模板，不改写用户自定义对话内容。
UPDATE agent_chat_message
SET content = '你好，我是 Sonora 音乐助手。告诉我你想听的歌曲、歌手、曲风、情绪或场景，我会为你搜索、推荐并播放真实音乐。'
WHERE role = 'ASSISTANT'
  AND (
    content LIKE '你好，我是 Sonora Agent。%开发和调试 Agent%'
    OR content LIKE '你好，我可以帮助你完成一些任务，%翻译语言%创意内容%'
    OR content LIKE '你好，我是 Sonora Agent。%回答问题%提供建议%写不同类型%'
  );
