const REPORT_BOILERPLATE = [
  /^根据.*(?:如下|分析|概括)/,
  /^(?:用户|音乐)?画像(?:概述|分析|总结)?[：:]?$/,
  /^音乐偏好(?:分析)?[：:]?$/,
  /^喜欢的(?:歌曲|歌手)[：:]?$/,
  /^最常听的(?:歌曲|歌手)[：:]?$/,
]

function cleanLine(value) {
  return value
    .replace(/^\s{0,3}#{1,6}\s*/, '')
    .replace(/^\s*[-+*]\s+/, '')
    .replace(/\*\*|__|`/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function shorten(value, maximum) {
  if (value.length <= maximum) return value
  const candidate = value.slice(0, maximum + 1)
  const boundary = Math.max(candidate.lastIndexOf('。'), candidate.lastIndexOf('！'), candidate.lastIndexOf('？'))
  if (boundary >= Math.floor(maximum * .55)) return candidate.slice(0, boundary + 1)
  return `${value.slice(0, maximum - 1).trimEnd()}…`
}

/** Keeps current and historical profile prose suitable for a compact summary card. */
export function conciseProfileSummary(value, fallback = '你的音乐画像正在慢慢显影。', maximum = 150) {
  const lines = String(value || '')
    .replace(/\r/g, '')
    .split('\n')
    .map(cleanLine)
    .filter(Boolean)
    .filter(line => !REPORT_BOILERPLATE.some(pattern => pattern.test(line)))

  if (!lines.length) return fallback
  const prose = lines.find(line => !/^(?:喜欢|最常听).*(?:歌曲|歌手)[：:]?$/.test(line)) || lines[0]
  return shorten(prose, Math.max(40, maximum))
}
