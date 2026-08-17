<script setup>
import { computed, ref } from 'vue'
import { ArrowLeft, Award, ChevronDown, ChevronRight, Disc3, LibraryBig, LoaderCircle, Music2, Play, RadioTower, Sparkles, UserRound, X } from 'lucide-vue-next'
import { request } from '../services/api'
import { useMusicStore } from '../stores/music'

const props = defineProps({
  actions: { type: Array, default: () => [] },
  conversationId: { type: String, default: '' },
})

const music = useMusicStore()
const activePanel = ref('')
const catalogTab = ref('SONGS')
const catalog = ref(null)
const catalogLoading = ref(false)
const albumLoading = ref(false)
const panelError = ref('')
const selectedAlbum = ref(null)
const albumDetail = ref(null)
const albumCache = ref({})

const artistSearch = computed(() => {
  for (let index = props.actions.length - 1; index >= 0; index -= 1) {
    const action = props.actions[index]
    if (action?.type === 'SHOW_QQ_ARTIST_RESULTS' && action.artistSearch) return action.artistSearch
  }
  return null
})

const artist = computed(() => artistSearch.value?.artists?.[0] || null)
const previewTracks = computed(() => withSearchId(artist.value?.tracks || []))
const catalogTracks = computed(() => withSearchId(catalog.value?.tracks || artist.value?.tracks || []))
const albumTracks = computed(() => withSearchId(albumDetail.value?.tracks || []))

function withSearchId(tracks) {
  return tracks.map(track => ({ ...track, _searchId: track._searchId || artistSearch.value?.searchId }))
}

function formatCompactCount(value) {
  const count = Math.max(0, Number(value || 0))
  if (count >= 100_000_000) return `${(count / 100_000_000).toFixed(count >= 1_000_000_000 ? 0 : 1)}亿`
  if (count >= 10_000) return `${(count / 10_000).toFixed(count >= 100_000 ? 0 : 1)}万`
  return String(Math.round(count))
}

function formatDuration(durationMs) {
  const totalSeconds = Math.max(0, Math.round(Number(durationMs || 0) / 1000))
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`
}

function isPlaying(track) {
  return music.currentTrack?.provider === track?.provider
    && music.currentTrack?.id === track?.id
    && !music.playbackPaused
}

function playTrack(track, queue) {
  if (track?.id) music.playTrack(track, queue)
}

function toggleInfo(panel) {
  panelError.value = ''
  activePanel.value = activePanel.value === panel ? '' : panel
}

function openCatalog(tab = 'SONGS') {
  catalogTab.value = tab
  selectedAlbum.value = null
  albumDetail.value = null
  panelError.value = ''
  if (!catalog.value && artist.value) {
    catalog.value = {
      ...artist.value,
      songPage: 1,
      songPageSize: Math.max(1, artist.value.tracks?.length || 12),
      albumPage: 1,
      albumPageSize: Math.max(1, artist.value.albums?.length || 8),
    }
  }
  activePanel.value = 'CATALOG'
}

async function loadCatalogPage({ songPage, albumPage }) {
  if (!artist.value?.mid || !props.conversationId || catalogLoading.value) return
  catalogLoading.value = true
  panelError.value = ''
  try {
    const current = catalog.value || artist.value
    const params = new URLSearchParams({
      conversationId: props.conversationId,
      songPage: String(songPage || current.songPage || 1),
      songPageSize: '12',
      albumPage: String(albumPage || current.albumPage || 1),
      albumPageSize: '8',
    })
    const result = await request(`/api/music/qq/artists/${encodeURIComponent(artist.value.mid)}?${params}`)
    catalog.value = result.data
  } catch (error) {
    panelError.value = error.message
  } finally {
    catalogLoading.value = false
  }
}

async function openAlbum(album) {
  if (!album?.mid) return
  activePanel.value = 'ALBUM'
  selectedAlbum.value = album
  panelError.value = ''
  const cached = albumCache.value[album.mid]
  if (cached) {
    albumDetail.value = cached
    return
  }
  if (!props.conversationId) {
    panelError.value = '当前会话标识不可用，暂时无法加载专辑详情。'
    return
  }
  albumLoading.value = true
  albumDetail.value = null
  try {
    const result = await request(`/api/music/qq/albums/${encodeURIComponent(album.mid)}?conversationId=${encodeURIComponent(props.conversationId)}`)
    albumDetail.value = result.data
    albumCache.value = { ...albumCache.value, [album.mid]: result.data }
  } catch (error) {
    panelError.value = error.message
  } finally {
    albumLoading.value = false
  }
}

function closePanel() {
  activePanel.value = ''
  panelError.value = ''
}
</script>

<template>
  <section v-if="artistSearch" class="inline-artist-results" aria-label="QQ 音乐艺人搜索结果">
    <header class="artist-results-header">
      <div class="artist-heading-mark"><UserRound :size="19" /></div>
      <div>
        <span>QQ MUSIC · ARTIST DOSSIER</span>
        <strong>“{{ artistSearch.keyword }}”的艺人档案</strong>
        <small v-if="artist">从 {{ artistSearch.total || 1 }} 位结果中展示第一位最佳匹配 · 所有操作均在卡片内完成</small>
        <small v-else>QQ 音乐暂未返回可读取的艺人资料</small>
      </div>
    </header>

    <article v-if="artist" class="artist-dossier-card">
      <div class="artist-identity">
        <button class="artist-portrait" type="button" :aria-label="`在卡片内查看 ${artist.name} 的简介`" @click="toggleInfo('BIO')">
          <img v-if="artist.imageUrl" :src="artist.imageUrl" :alt="`${artist.name} 头像`" />
          <UserRound v-else :size="58" />
          <i>ARTIST</i>
        </button>
        <button class="artist-identity-copy" type="button" :aria-label="`在卡片内查看 ${artist.name} 的简介`" @click="toggleInfo('BIO')">
          <span class="artist-source"><RadioTower :size="11" /> VERIFIED QQ MUSIC PROFILE</span>
          <h2>{{ artist.name }}</h2>
          <span class="artist-facts">
            <b v-if="artist.foreignName">{{ artist.foreignName }}</b>
            <b v-if="artist.area">{{ artist.area }}</b>
            <b v-if="artist.birthday">{{ artist.birthday }}</b>
          </span>
          <span class="artist-biography">{{ artist.biographySummary || artist.description || 'QQ 音乐暂未提供可核验的艺人简介。' }}</span>
          <span class="artist-counts">
            <span><strong>{{ formatCompactCount(artist.songTotal) }}</strong>歌曲</span>
            <span><strong>{{ formatCompactCount(artist.albumTotal) }}</strong>专辑</span>
            <span v-if="artist.videoTotal"><strong>{{ formatCompactCount(artist.videoTotal) }}</strong>视频</span>
          </span>
        </button>
      </div>

      <div class="artist-agent-summary">
        <button type="button" @click="toggleInfo('ACHIEVEMENTS')">
          <span><Award :size="13" /> 生涯与成就</span><p>{{ artist.achievementSummary }}</p><ChevronDown :size="14" />
        </button>
        <button type="button" @click="toggleInfo('STYLE')">
          <span><Sparkles :size="13" /> 曲风与创作</span><p>{{ artist.styleSummary }}</p><ChevronDown :size="14" />
        </button>
      </div>

      <div class="artist-catalog-preview">
        <section class="artist-song-preview">
          <header><div><Music2 :size="14" /><strong>歌曲目录</strong></div><small>点击即可播放 · {{ artist.tracks.length }} / {{ artist.songTotal }}</small></header>
          <ol>
            <li v-for="(track, index) in previewTracks.slice(0, 6)" :key="`${track.provider}:${track.id}`" :class="{ playing: isPlaying(track) }">
              <button type="button" :aria-label="`播放歌曲 ${track.name}`" @click="playTrack(track, previewTracks)">
                <i><Play :size="11" fill="currentColor" /><em>{{ String(index + 1).padStart(2, '0') }}</em></i>
                <span><strong>{{ track.name }}</strong><small>{{ track.album || track.artists?.join(' / ') || 'QQ 音乐' }}</small></span>
                <time>{{ formatDuration(track.durationMs) }}</time><ChevronRight :size="12" />
              </button>
            </li>
          </ol>
        </section>

        <section class="artist-album-preview">
          <header><div><Disc3 :size="14" /><strong>专辑目录</strong></div><small>点击卡内展开 · {{ artist.albums.length }} / {{ artist.albumTotal }}</small></header>
          <div>
            <button v-for="album in artist.albums.slice(0, 4)" :key="album.mid" type="button" :aria-label="`在卡片内打开专辑 ${album.name}`" @click="openAlbum(album)">
              <img v-if="album.coverUrl" :src="album.coverUrl" :alt="`${album.name} 封面`" />
              <span v-else><Disc3 :size="24" /></span><strong>{{ album.name }}</strong><small>{{ album.publishDate || album.type || '发行日期未知' }}</small>
            </button>
          </div>
        </section>
      </div>

      <section v-if="activePanel" class="artist-inline-panel" aria-live="polite">
        <header>
          <button v-if="activePanel === 'ALBUM'" type="button" aria-label="返回艺人目录" @click="openCatalog('ALBUMS')"><ArrowLeft :size="15" /></button>
          <i v-else class="panel-leading" aria-hidden="true"></i>
          <div>
            <span>IN-CARD VIEW</span>
            <strong v-if="activePanel === 'BIO'">{{ artist.name }} · 艺人简介</strong>
            <strong v-else-if="activePanel === 'ACHIEVEMENTS'">{{ artist.name }} · 生涯与成就</strong>
            <strong v-else-if="activePanel === 'STYLE'">{{ artist.name }} · 曲风与创作</strong>
            <strong v-else-if="activePanel === 'ALBUM'">{{ selectedAlbum?.name }} · 专辑详情</strong>
            <strong v-else>{{ artist.name }} · 完整目录</strong>
          </div>
          <button type="button" aria-label="关闭卡片内详情" @click="closePanel"><X :size="15" /></button>
        </header>

        <div v-if="activePanel === 'BIO'" class="artist-inline-copy"><p>{{ artist.description || artist.biographySummary }}</p><dl><div v-if="artist.foreignName"><dt>外文名</dt><dd>{{ artist.foreignName }}</dd></div><div v-if="artist.area"><dt>地区</dt><dd>{{ artist.area }}</dd></div><div v-if="artist.birthday"><dt>生日 / 成立</dt><dd>{{ artist.birthday }}</dd></div></dl></div>
        <div v-else-if="activePanel === 'ACHIEVEMENTS'" class="artist-inline-copy"><p>{{ artist.achievementSummary }}</p><small>总结仅依据 QQ 音乐简介和当前目录统计，不补充未经来源验证的奖项。</small></div>
        <div v-else-if="activePanel === 'STYLE'" class="artist-inline-copy"><p>{{ artist.styleSummary }}</p><small>资料不足时不根据歌曲名、封面或常识推测曲风。</small></div>

        <template v-else-if="activePanel === 'CATALOG'">
          <nav class="inline-catalog-tabs"><button :class="{ active: catalogTab === 'SONGS' }" @click="catalogTab = 'SONGS'">歌曲 {{ artist.songTotal }}</button><button :class="{ active: catalogTab === 'ALBUMS' }" @click="catalogTab = 'ALBUMS'">专辑 {{ artist.albumTotal }}</button></nav>
          <div v-if="panelError" class="inline-panel-state error">{{ panelError }}</div>
          <div v-if="catalogLoading" class="inline-panel-state"><LoaderCircle class="spin" :size="20" />正在卡片内加载目录</div>
          <div v-else-if="catalogTab === 'SONGS'" class="inline-full-track-list">
            <button v-for="(track, index) in catalogTracks" :key="`${track.provider}:${track.id}`" :class="{ playing: isPlaying(track) }" @click="playTrack(track, catalogTracks)"><i><Play :size="12" fill="currentColor" /></i><span><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }} · {{ track.album || 'QQ 音乐' }}</small></span><time>{{ formatDuration(track.durationMs) }}</time></button>
            <nav v-if="(catalog?.songPage || 1) > 1 || catalog?.hasMoreSongs" class="inline-pagination"><button :disabled="catalogLoading || (catalog?.songPage || 1) <= 1" @click="loadCatalogPage({ songPage: catalog.songPage - 1 })">上一页</button><span>第 {{ catalog?.songPage || 1 }} 页</span><button :disabled="catalogLoading || !catalog?.hasMoreSongs" @click="loadCatalogPage({ songPage: (catalog?.songPage || 1) + 1 })">下一页</button></nav>
          </div>
          <div v-else class="inline-full-album-grid">
            <button v-for="album in catalog?.albums || artist.albums" :key="album.mid" @click="openAlbum(album)"><img v-if="album.coverUrl" :src="album.coverUrl" :alt="album.name" /><span v-else><Disc3 :size="26" /></span><strong>{{ album.name }}</strong><small>{{ album.publishDate || album.type || '发行日期未知' }}</small></button>
            <nav v-if="(catalog?.albumPage || 1) > 1 || catalog?.hasMoreAlbums" class="inline-pagination"><button :disabled="catalogLoading || (catalog?.albumPage || 1) <= 1" @click="loadCatalogPage({ albumPage: catalog.albumPage - 1 })">上一页</button><span>第 {{ catalog?.albumPage || 1 }} 页</span><button :disabled="catalogLoading || !catalog?.hasMoreAlbums" @click="loadCatalogPage({ albumPage: (catalog?.albumPage || 1) + 1 })">下一页</button></nav>
          </div>
        </template>

        <template v-else-if="activePanel === 'ALBUM'">
          <div v-if="panelError" class="inline-panel-state error">{{ panelError }}</div>
          <div v-else-if="albumLoading" class="inline-panel-state"><LoaderCircle class="spin" :size="20" />正在卡片内加载专辑</div>
          <div v-else-if="albumDetail" class="inline-album-detail">
            <header><img v-if="albumDetail.coverUrl" :src="albumDetail.coverUrl" :alt="albumDetail.name" /><span v-else><Disc3 :size="34" /></span><div><strong>{{ albumDetail.name }}</strong><p>{{ albumDetail.artists?.join(' / ') }}</p><small>{{ [albumDetail.publishDate, albumDetail.genre, albumDetail.company].filter(Boolean).join(' · ') }}</small><em>{{ albumDetail.description || `${albumDetail.trackCount} 首歌曲` }}</em></div></header>
            <div class="inline-full-track-list"><button v-for="track in albumTracks" :key="`${track.provider}:${track.id}`" :class="{ playing: isPlaying(track) }" @click="playTrack(track, albumTracks)"><i><Play :size="12" fill="currentColor" /></i><span><strong>{{ track.name }}</strong><small>{{ track.artists?.join(' / ') }}</small></span><time>{{ formatDuration(track.durationMs) }}</time></button></div>
          </div>
        </template>
      </section>

      <footer class="artist-card-footer"><p><LibraryBig :size="14" />歌曲播放、专辑详情和完整目录均在当前卡片内完成。</p><button type="button" @click="activePanel === 'CATALOG' ? closePanel() : openCatalog('SONGS')">{{ activePanel === 'CATALOG' ? '收起完整目录' : '在卡片内查看完整歌曲与专辑' }} <ChevronDown :size="14" /></button></footer>
    </article>

    <div v-else class="artist-empty"><UserRound :size="28" /><span>换一个艺人、乐队或组合名称试试</span></div>
  </section>
</template>

<style scoped>
.inline-artist-results{max-width:100%;margin-top:16px;border:1px solid rgba(166,139,255,.2);border-radius:24px;padding:16px;background:radial-gradient(circle at 12% 0,rgba(129,98,235,.14),transparent 28%),linear-gradient(145deg,rgba(27,27,42,.96),rgba(14,16,24,.97));box-shadow:0 22px 58px rgba(0,0,0,.28)}
.artist-results-header{display:grid;grid-template-columns:auto minmax(0,1fr);align-items:center;gap:11px;padding:1px 2px 15px}.artist-heading-mark{display:grid;width:38px;height:38px;place-items:center;border:1px solid rgba(166,139,255,.24);border-radius:13px;background:rgba(137,105,245,.12);color:#b19aff}.artist-results-header>div:last-child{display:grid;min-width:0;gap:3px}.artist-results-header span{color:#a98fff;font-size:8px;font-weight:850;letter-spacing:.14em}.artist-results-header strong{overflow:hidden;color:#f3f1fa;font-size:14px;text-overflow:ellipsis;white-space:nowrap}.artist-results-header small{color:#7d7c8d;font-size:9px}
.artist-dossier-card{overflow:hidden;border:1px solid rgba(255,255,255,.075);border-radius:20px;background:linear-gradient(135deg,rgba(39,36,58,.82),rgba(13,15,23,.9) 54%);box-shadow:0 16px 38px rgba(0,0,0,.2)}.artist-identity{display:grid;grid-template-columns:176px minmax(0,1fr);gap:18px;padding:18px}.artist-portrait{position:relative;display:grid;width:176px;height:176px;overflow:hidden;border:1px solid rgba(255,255,255,.1);border-radius:22px;padding:0;background:linear-gradient(145deg,#4a3f68,#202334);color:#bba6ff;place-items:center}.artist-portrait img{width:100%;height:100%;object-fit:cover;transition:transform .2s}.artist-portrait:hover img{transform:scale(1.035)}.artist-portrait:after{position:absolute;inset:50% 0 0;background:linear-gradient(transparent,rgba(8,9,15,.78));content:""}.artist-portrait i{position:absolute;z-index:2;right:10px;bottom:9px;border-radius:7px;padding:4px 6px;background:rgba(12,11,20,.72);color:#d8ccff;font-size:7px;font-style:normal;font-weight:800;letter-spacing:.12em}
.artist-identity-copy{display:flex;min-width:0;flex-direction:column;align-items:flex-start;border:0;border-radius:14px;padding:0;background:transparent;color:inherit;text-align:left}.artist-identity-copy:hover h2,.artist-identity-copy:focus-visible h2{color:#c9b8ff}.artist-identity-copy:focus-visible{outline:2px solid rgba(166,139,255,.38)}.artist-source{display:flex;align-items:center;gap:5px;color:#a990ff;font-size:8px;font-weight:800;letter-spacing:.1em}.artist-identity-copy h2{margin:8px 0 5px;color:#faf9ff;font-size:29px;line-height:1.05;letter-spacing:-.035em}.artist-facts{display:flex;flex-wrap:wrap;gap:6px;margin:0 0 11px}.artist-facts b{border:1px solid rgba(255,255,255,.075);border-radius:999px;padding:4px 7px;background:rgba(255,255,255,.035);color:#8d8a99;font-size:8px}.artist-biography{display:-webkit-box;overflow:hidden;color:#a4a1af;font-size:10px;line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:4}.artist-counts{display:flex;gap:8px;margin-top:auto;padding-top:13px}.artist-counts>span{display:flex;align-items:baseline;gap:4px;border-left:2px solid rgba(169,144,255,.52);padding:2px 9px;color:#777584;font-size:8px}.artist-counts strong{color:#e3ddf7;font-size:14px}
.artist-agent-summary{display:grid;grid-template-columns:1fr 1fr;gap:10px;border-block:1px solid rgba(255,255,255,.065);padding:13px 18px;background:rgba(7,8,14,.24)}.artist-agent-summary>button{position:relative;display:block;border:1px solid rgba(166,139,255,.11);border-radius:13px;padding:11px 34px 11px 11px;background:rgba(140,108,246,.035);color:inherit;text-align:left}.artist-agent-summary>button:hover,.artist-agent-summary>button:focus-visible{border-color:rgba(166,139,255,.3);background:rgba(140,108,246,.08);outline:none}.artist-agent-summary>button>svg{position:absolute;top:50%;right:11px;color:#706886;transform:translateY(-50%)}.artist-agent-summary span{display:flex;align-items:center;gap:6px;color:#bba8fb;font-size:8px;font-weight:800}.artist-agent-summary p{display:-webkit-box;overflow:hidden;margin:7px 0 0;color:#8c8997;font-size:9px;line-height:1.65;-webkit-box-orient:vertical;-webkit-line-clamp:3}
.artist-catalog-preview{display:grid;grid-template-columns:minmax(0,1.08fr) minmax(260px,.92fr);gap:14px;padding:16px 18px}.artist-catalog-preview section>header{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:9px}.artist-catalog-preview header>div{display:flex;align-items:center;gap:6px;color:#c7badf}.artist-catalog-preview header strong{font-size:10px}.artist-catalog-preview header small{color:#686775;font-size:8px}
.artist-song-preview ol{display:grid;margin:0;padding:0;list-style:none}.artist-song-preview li{border-top:1px solid rgba(255,255,255,.052)}.artist-song-preview li>button{display:grid;width:100%;min-height:42px;grid-template-columns:34px minmax(0,1fr) auto 14px;align-items:center;gap:6px;border:0;border-radius:8px;padding:0 5px 0 0;background:transparent;color:inherit;text-align:left}.artist-song-preview li>button:hover,.artist-song-preview li>button:focus-visible,.artist-song-preview li.playing>button{background:rgba(166,139,255,.08);outline:none}.artist-song-preview li>button>i{display:flex;align-items:center;gap:3px;color:#696675;font-size:7px;font-style:normal}.artist-song-preview li>button>i svg{opacity:0}.artist-song-preview li>button:hover>i svg,.artist-song-preview li.playing>button>i svg{color:#bba8fb;opacity:1}.artist-song-preview em{font-style:normal}.artist-song-preview li>button>span{display:grid;min-width:0;gap:2px}.artist-song-preview li strong,.artist-song-preview li small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.artist-song-preview li strong{color:#d8d6dd;font-size:9px}.artist-song-preview li small,.artist-song-preview time{color:#62616d;font-size:7px}.artist-song-preview li>button>svg{color:#545161}.artist-song-preview li.playing strong{color:#c9b8ff}
.artist-album-preview>div,.inline-full-album-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:7px}.artist-album-preview button,.inline-full-album-grid>button{display:grid;min-width:0;gap:4px;border:1px solid transparent;border-radius:11px;padding:3px;background:transparent;color:inherit;text-align:left}.artist-album-preview button:hover,.artist-album-preview button:focus-visible,.inline-full-album-grid>button:hover{border-color:rgba(166,139,255,.28);background:rgba(166,139,255,.06);outline:none;transform:translateY(-2px)}.artist-album-preview img,.artist-album-preview button>span,.inline-full-album-grid img,.inline-full-album-grid>button>span{display:grid;width:100%;aspect-ratio:1;overflow:hidden;border-radius:9px;background:linear-gradient(145deg,#39334a,#222530);object-fit:cover;place-items:center}.artist-album-preview button strong,.artist-album-preview button small,.inline-full-album-grid strong,.inline-full-album-grid small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.artist-album-preview button strong,.inline-full-album-grid strong{color:#c8c5cf;font-size:8px}.artist-album-preview button small,.inline-full-album-grid small{color:#5f5d69;font-size:7px}
.artist-inline-panel{border-top:1px solid rgba(166,139,255,.16);padding:16px 18px;background:linear-gradient(145deg,rgba(18,17,29,.98),rgba(9,11,18,.98))}.artist-inline-panel>header{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:9px;margin-bottom:13px}.artist-inline-panel>header>button{display:grid;width:30px;height:30px;place-items:center;border:1px solid rgba(255,255,255,.08);border-radius:9px;background:rgba(255,255,255,.04);color:#8d8998}.artist-inline-panel>header>div{display:grid;gap:3px}.artist-inline-panel>header span{color:#9f86ef;font-size:7px;font-weight:850;letter-spacing:.14em}.artist-inline-panel>header strong{color:#e7e3ef;font-size:12px}.panel-leading{display:block;width:30px;height:1px}.artist-inline-copy p{margin:0;color:#aaa5b2;font-size:10px;line-height:1.8}.artist-inline-copy>small{display:block;margin-top:9px;color:#6d6875;font-size:8px}.artist-inline-copy dl{display:flex;flex-wrap:wrap;gap:8px;margin:13px 0 0}.artist-inline-copy dl>div{border:1px solid rgba(255,255,255,.07);border-radius:10px;padding:7px 9px}.artist-inline-copy dt{color:#696473;font-size:7px}.artist-inline-copy dd{margin:3px 0 0;color:#c1bdc8;font-size:9px}
.inline-catalog-tabs{display:flex;gap:6px;margin-bottom:12px}.inline-catalog-tabs button{border:1px solid rgba(255,255,255,.08);border-radius:999px;padding:6px 11px;background:transparent;color:#77727f;font-size:8px}.inline-catalog-tabs button.active{border-color:rgba(166,139,255,.3);background:rgba(166,139,255,.1);color:#c5b4ff}.inline-full-track-list{display:grid}.inline-full-track-list>button{display:grid;min-height:42px;grid-template-columns:30px minmax(0,1fr) auto;align-items:center;gap:8px;border:0;border-top:1px solid rgba(255,255,255,.055);padding:0 7px;background:transparent;color:inherit;text-align:left}.inline-full-track-list>button:hover,.inline-full-track-list>button.playing{background:rgba(166,139,255,.07)}.inline-full-track-list>button i{display:grid;width:24px;height:24px;place-items:center;border-radius:50%;background:rgba(166,139,255,.08);color:#a78ef4}.inline-full-track-list>button span{display:grid;min-width:0;gap:2px}.inline-full-track-list strong,.inline-full-track-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.inline-full-track-list strong{color:#d9d5df;font-size:9px}.inline-full-track-list small,.inline-full-track-list time{color:#67626f;font-size:7px}.inline-full-album-grid{grid-template-columns:repeat(6,minmax(0,1fr))}.inline-pagination{display:flex;grid-column:1/-1;align-items:center;justify-content:center;gap:8px;margin-top:10px}.inline-pagination button{border:1px solid rgba(255,255,255,.08);border-radius:8px;padding:6px 9px;background:rgba(255,255,255,.04);color:#9c96a5;font-size:8px}.inline-pagination button:disabled{opacity:.35}.inline-pagination span{color:#6e6975;font-size:8px}.inline-panel-state{display:flex;min-height:90px;align-items:center;justify-content:center;gap:7px;color:#85808e;font-size:9px}.inline-panel-state.error{color:#f095a4}
.inline-album-detail>header{display:grid;grid-template-columns:112px minmax(0,1fr);gap:14px;margin-bottom:14px}.inline-album-detail>header>img,.inline-album-detail>header>span{display:grid;width:112px;height:112px;border-radius:14px;background:#242331;object-fit:cover;place-items:center}.inline-album-detail>header>div{display:flex;min-width:0;flex-direction:column;align-items:flex-start;justify-content:center}.inline-album-detail>header strong{color:#f0edf4;font-size:18px}.inline-album-detail>header p{margin:5px 0;color:#aaa5b1;font-size:9px}.inline-album-detail>header small{color:#706b78;font-size:8px}.inline-album-detail>header em{display:-webkit-box;overflow:hidden;margin-top:9px;color:#85808d;font-size:8px;font-style:normal;line-height:1.6;-webkit-box-orient:vertical;-webkit-line-clamp:3}
.artist-card-footer{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:8px;border-top:1px solid rgba(255,255,255,.065);padding:12px 18px;background:rgba(8,9,15,.32)}.artist-card-footer p{display:flex;align-items:center;gap:6px;margin:0;color:#6f6d7a;font-size:8px}.artist-card-footer p svg{color:#9e85ee}.artist-card-footer button{display:flex;align-items:center;gap:6px;border:1px solid rgba(166,139,255,.22);border-radius:10px;padding:8px 11px;background:rgba(145,110,246,.1);color:#c7b6ff;font-size:9px}.artist-empty{display:flex;min-height:120px;align-items:center;justify-content:center;gap:9px;color:#787483;font-size:10px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:780px){.inline-artist-results{padding:12px}.artist-identity{grid-template-columns:112px minmax(0,1fr);gap:13px;padding:14px}.artist-portrait{width:112px;height:132px}.artist-identity-copy h2{font-size:22px}.artist-agent-summary,.artist-catalog-preview{grid-template-columns:1fr}.inline-full-album-grid{grid-template-columns:repeat(4,minmax(0,1fr))}.artist-card-footer{grid-template-columns:1fr}.artist-card-footer button{justify-self:start}}
@media(max-width:500px){.artist-results-header small{display:none}.artist-results-header strong{white-space:normal}.artist-identity{grid-template-columns:1fr}.artist-portrait{width:100%;height:164px}.artist-identity-copy h2{font-size:25px}.artist-counts{flex-wrap:wrap}.artist-agent-summary{padding:11px}.artist-catalog-preview{padding:13px}.artist-album-preview>div{grid-template-columns:repeat(2,minmax(0,1fr))}.artist-album-preview button:nth-child(n+3){display:none}.artist-inline-panel{padding:13px}.inline-full-album-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.inline-album-detail>header{grid-template-columns:78px minmax(0,1fr)}.inline-album-detail>header>img,.inline-album-detail>header>span{width:78px;height:78px}.artist-card-footer p{display:none}.artist-card-footer button{width:100%;justify-content:center}}
</style>
