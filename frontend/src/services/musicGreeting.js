const HOLIDAY_PERIODS = [
  { start: 20250101, end: 20250101, name: '元旦', greeting: '元旦快乐', eyebrow: 'NEW YEAR, NEW SOUND', message: '新的一年，先让喜欢的旋律为今天开场。' },
  { start: 20250128, end: 20250204, name: '春节', greeting: '春节快乐', eyebrow: 'SPRING FESTIVAL', message: '把团圆与好心情装进歌单，假期慢慢听。' },
  { start: 20250404, end: 20250406, name: '清明假期', greeting: '假期好', eyebrow: 'A QUIET SPRING DAY', message: '春风正好，听些清澈舒展的声音。' },
  { start: 20250501, end: 20250505, name: '劳动节', greeting: '劳动节快乐', eyebrow: 'TAKE A WELL-EARNED BREAK', message: '把忙碌按下暂停，给自己一段自在的播放时间。' },
  { start: 20250531, end: 20250602, name: '端午节', greeting: '端午安康', eyebrow: 'DRAGON BOAT FESTIVAL', message: '粽香与旋律都已就位，今天听点轻快的。' },
  { start: 20251001, end: 20251008, name: '国庆·中秋假期', greeting: '双节快乐', eyebrow: 'HOLIDAY IN FULL SWING', message: '月色与长假都正好，让歌单陪你出发，也陪你好好休息。' },
  { start: 20260101, end: 20260103, name: '元旦', greeting: '元旦快乐', eyebrow: 'NEW YEAR, NEW SOUND', message: '新的一年，先让喜欢的旋律为今天开场。' },
  { start: 20260215, end: 20260223, name: '春节', greeting: '春节快乐', eyebrow: 'SPRING FESTIVAL', message: '把团圆与好心情装进歌单，假期慢慢听。' },
  { start: 20260404, end: 20260406, name: '清明假期', greeting: '假期好', eyebrow: 'A QUIET SPRING DAY', message: '春风正好，听些清澈舒展的声音。' },
  { start: 20260501, end: 20260505, name: '劳动节', greeting: '劳动节快乐', eyebrow: 'TAKE A WELL-EARNED BREAK', message: '把忙碌按下暂停，给自己一段自在的播放时间。' },
  { start: 20260619, end: 20260621, name: '端午节', greeting: '端午安康', eyebrow: 'DRAGON BOAT FESTIVAL', message: '粽香与旋律都已就位，今天听点轻快的。' },
  { start: 20260925, end: 20260927, name: '中秋节', greeting: '中秋快乐', eyebrow: 'MOONLIGHT LISTENING', message: '月色适合慢听，挑一张温柔歌单陪你。' },
  { start: 20261001, end: 20261007, name: '国庆节', greeting: '国庆快乐', eyebrow: 'HOLIDAY IN FULL SWING', message: '长假模式开启，让歌单陪你出发，也陪你好好休息。' },
]

const TIME_SCENES = [
  { until: 6, salutation: '夜深了', eyebrow: 'LATE NIGHT MODE', status: '安静收尾', theme: 'night', message: '把音量调低一点，留一张安静的歌单陪你慢慢收尾。' },
  { until: 11, salutation: '早上好', eyebrow: 'MORNING ENERGY', status: '活力开场', theme: 'morning', message: '新一天别急着按下重复，让一首有活力的歌先把状态点亮。' },
  { until: 14, salutation: '中午好', eyebrow: 'MIDDAY BREATHER', status: '午间充电', theme: 'noon', message: '忙了一上午，先放松几分钟，让轻快旋律陪你补满能量。' },
  { until: 18, salutation: '下午好', eyebrow: 'AFTERNOON FLOW', status: '稳稳向前', theme: 'afternoon', message: '把注意力调到刚刚好，来点稳定节奏陪你继续向前。' },
  { until: 22, salutation: '晚上好', eyebrow: 'EVENING UNWIND', status: '晚间松弛', theme: 'evening', message: '今天辛苦了，把白天的喧闹交给音乐慢慢放下。' },
  { until: 24, salutation: '夜深了', eyebrow: 'LATE NIGHT MODE', status: '安静收尾', theme: 'night', message: '把音量调低一点，留一张安静的歌单陪你慢慢收尾。' },
]

function dateKey(date) {
  return date.getFullYear() * 10000 + (date.getMonth() + 1) * 100 + date.getDate()
}

export function getMusicGreeting(date = new Date()) {
  const safeDate = date instanceof Date && Number.isFinite(date.getTime()) ? date : new Date()
  const scene = TIME_SCENES.find(item => safeDate.getHours() < item.until) || TIME_SCENES[TIME_SCENES.length - 1]
  const holiday = HOLIDAY_PERIODS.find(item => dateKey(safeDate) >= item.start && dateKey(safeDate) <= item.end)
  if (!holiday) return { ...scene, holidayName: '', isHoliday: false }
  return {
    ...scene,
    salutation: holiday.greeting,
    eyebrow: `${holiday.eyebrow} · ${scene.eyebrow}`,
    status: `法定节假日 · ${holiday.name}`,
    message: `${holiday.message} ${scene.message}`,
    holidayName: holiday.name,
    isHoliday: true,
  }
}
