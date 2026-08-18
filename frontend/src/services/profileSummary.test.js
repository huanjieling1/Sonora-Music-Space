import assert from 'node:assert/strict'
import test from 'node:test'
import { conciseProfileSummary } from './profileSummary.js'

test('removes historical markdown report chrome and keeps the first useful summary', () => {
  const result = conciseProfileSummary(`根据您提供的信息，您的用户画像可以概括如下：

### 用户画像概述
您是一位对音乐有明确偏好的深度听众，也愿意探索新的声音。

### 音乐偏好
- **喜欢的歌曲**：玫瑰少年`)

  assert.equal(result, '您是一位对音乐有明确偏好的深度听众，也愿意探索新的声音。')
  assert.doesNotMatch(result, /###|\*\*|-/)
})

test('bounds a long profile summary without exposing a wall of text', () => {
  const result = conciseProfileSummary('Mili 是你此刻最明亮的音乐坐标。'.repeat(20), '暂无画像', 80)

  assert.ok(result.length <= 80)
  assert.match(result, /^Mili/)
})
