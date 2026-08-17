import http from 'node:http'
import { cancelQrLogin, pollQrLogin, startQrLogin } from './qq-browser-login.js'

const host = '127.0.0.1'
const port = Number.parseInt(process.env.QQ_MUSIC_BRIDGE_PORT || '3200', 10)
const vkeySign = process.env.QQ_MUSIC_VKEY_SIGN || 'zzannc1o6o9b4i971602f3554385022046ab796512b7012'
const timeoutMs = 10_000

const qualityFiles = {
  flac: { prefix: 'F000', extension: '.flac' },
  320: { prefix: 'M800', extension: '.mp3' },
  128: { prefix: 'M500', extension: '.mp3' },
  m4a: { prefix: 'C400', extension: '.m4a' },
}

function json(response, status, payload) {
  const body = JSON.stringify(payload)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  })
  response.end(body)
}

function isLoopback(address = '') {
  return address === '127.0.0.1' || address === '::1' || address === '::ffff:127.0.0.1'
}

function safeCookie(request) {
  const value = String(request.headers['x-qq-music-cookie'] || '').trim()
  if (!value || value.length > 16_384 || /[\r\n]/.test(value)) return ''
  return value
}

function cookieValue(cookie, name) {
  const match = cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`, 'i'))
  return match?.[1] || ''
}

function secureQqPlaybackUrl(domain, purl) {
  try {
    const url = new URL(`${domain}${purl}`)
    const officialQqHost = url.hostname === 'qq.com' || url.hostname.endsWith('.qq.com')
    if (url.protocol === 'http:' && officialQqHost) {
      url.protocol = 'https:'
    }
    return url.protocol === 'https:' && officialQqHost ? url.toString() : ''
  } catch {
    return ''
  }
}

async function upstreamJson(url, headers = {}) {
  const response = await fetch(url, {
    headers: {
      Accept: 'application/json,text/plain,*/*',
      'User-Agent': 'Mozilla/5.0 SonoraLocal/1.0',
      ...headers,
    },
    signal: AbortSignal.timeout(timeoutMs),
  })
  if (!response.ok) throw new Error(`UPSTREAM_HTTP_${response.status}`)
  return response.json()
}

async function search(requestUrl) {
  const key = requestUrl.searchParams.get('key')?.trim() || ''
  const limit = Math.min(30, Math.max(1, Number.parseInt(requestUrl.searchParams.get('limit') || '20', 10)))
  const page = Math.min(50, Math.max(1, Number.parseInt(requestUrl.searchParams.get('page') || '1', 10)))
  const type = String(requestUrl.searchParams.get('type') || 'TRACK').trim().toUpperCase()
  const searchType = { TRACK: 0, ARTIST: 1, ALBUM: 2, PLAYLIST: 3, VIDEO: 4, LYRIC: 7, USER: 8 }[type]
  if (!key) return { status: 400, body: { code: 'INVALID_QUERY', message: '缺少搜索关键词' } }
  if (searchType === undefined) return { status: 400, body: { code: 'INVALID_SEARCH_TYPE', message: '不支持的搜索分类' } }

  const data = {
    comm: { ct: '19', cv: '1859', uin: '0' },
    req: {
      module: 'music.search.SearchCgiService',
      method: 'DoSearchForQQMusicDesktop',
      param: { grp: 1, query: key, search_type: searchType, num_per_page: limit, page_num: page },
    },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('data', JSON.stringify(data))
  const payload = await upstreamJson(url, { Referer: 'https://y.qq.com/', Origin: 'https://y.qq.com' })
  const response = payload?.req
  if (Number(response?.code) !== 0 || !response?.data?.body) {
    throw new Error(`QQ_SEARCH_${response?.code ?? 'INVALID'}`)
  }
  const body = response.data.body
  const meta = response.data.meta || {}
  const track = item => ({
    songMid: String(item?.mid || item?.songmid || '').trim(),
    mediaMid: String(item?.file?.media_mid || item?.strMediaMid || item?.media_mid || item?.mid || '').trim(),
    name: String(item?.name || item?.title || item?.songname || '').trim(),
    artists: Array.isArray(item.singer) ? item.singer.map(artist => artist.name).filter(Boolean) : [],
    album: String(item?.album?.name || item?.albumname || '').trim(),
    albumMid: String(item?.album?.mid || item?.albummid || '').trim(),
    durationMs: Math.max(0, Number(item.interval) || 0) * 1000,
    payPlay: Boolean(item?.pay?.pay_play || item?.pay?.payplay),
    qualities: {
      flac: Number(item?.file?.size_flac || item?.sizeflac) > 0,
      320: Number(item?.file?.size_320mp3 || item?.size320) > 0,
      128: Number(item?.file?.size_128mp3 || item?.size128) > 0,
      m4a: true,
    },
  })
  const songList = Array.isArray(body?.song?.list) ? body.song.list : []
  const tracks = type === 'TRACK' ? songList.map(track).filter(item => item.songMid && item.name) : []
  const artists = (Array.isArray(body?.singer?.list) ? body.singer.list : []).map(item => ({
    id: String(item.singerMID || item.singerID || ''),
    mid: String(item.singerMID || ''),
    name: stripMarkup(item.singerName || item.singerName_hilight),
    imageUrl: secureQqImageUrl(item.singerPic) || singerImageUrl(item.singerMID),
    songCount: Math.max(0, Number(item.songNum) || 0),
    albumCount: Math.max(0, Number(item.albumNum) || 0),
    videoCount: Math.max(0, Number(item.mvNum) || 0),
    externalUrl: item.singerMID ? `https://y.qq.com/n/ryqq/singer/${item.singerMID}` : '',
  })).filter(item => item.id && item.name)
  const albums = (Array.isArray(body?.album?.list) ? body.album.list : []).map(item => ({
    id: String(item.albumMID || item.albumID || ''),
    mid: String(item.albumMID || ''),
    name: stripMarkup(item.albumName || item.albumName_hilight),
    coverUrl: secureQqImageUrl(item.albumPic) || albumImageUrl(item.albumMID),
    artists: Array.isArray(item.singer_list) ? item.singer_list.map(artist => artist.name).filter(Boolean) : [item.singerName].filter(Boolean),
    publishDate: String(item.publicTime || ''),
    trackCount: Math.max(0, Number(item.song_count) || 0),
    externalUrl: item.albumMID ? `https://y.qq.com/n/ryqq/albumDetail/${item.albumMID}` : '',
  })).filter(item => item.id && item.name)
  const playlists = (Array.isArray(body?.songlist?.list) ? body.songlist.list : []).map(publicPlaylist)
    .filter(item => /^\d+$/.test(item.id) && item.name)
  const videos = (Array.isArray(body?.mv?.list) ? body.mv.list : []).map(item => ({
    id: String(item.v_id || item.mv_id || ''),
    name: stripMarkup(item.mv_name || item.mvName_hilight),
    coverUrl: secureQqImageUrl(item.mv_pic_url),
    artists: Array.isArray(item.singer_list) ? item.singer_list.map(artist => artist.name).filter(Boolean) : [item.singer_name].filter(Boolean),
    durationMs: Math.max(0, Number(item.duration) || 0) * 1000,
    playCount: Math.max(0, Number(item.play_count) || 0),
    publishDate: String(item.publish_date || ''),
    externalUrl: item.v_id ? `https://y.qq.com/n/ryqq/mv/${item.v_id}` : '',
  })).filter(item => item.id && item.name)
  const lyrics = type === 'LYRIC' ? songList.map(item => ({
    ...track(item),
    snippet: lyricSnippet(item.content, key),
  })).filter(item => item.songMid && item.name) : []
  const users = (Array.isArray(body?.user?.list) ? body.user.list : []).map(item => ({
    id: String(item.encrypt_uin || item.docid || ''),
    name: stripMarkup(item.title || item.title_hilight),
    avatarUrl: secureQqImageUrl(item.pic || item.iconurl),
    followerCount: Math.max(0, Number(item.fans_num) || 0),
    playlistCount: Math.max(0, Number(item.diss_num) || 0),
    badge: String(item.identify_title || ''),
    externalUrl: item.encrypt_uin ? `https://y.qq.com/n/ryqq/profile/${item.encrypt_uin}` : '',
  })).filter(item => item.id && item.name)
  const total = Math.max(0, Number(meta.sum) || 0)
  return { status: 200, body: { keyword: key, type, page, pageSize: limit, total,
    hasNext: Number(meta.nextpage) > page || page * limit < total,
    tracks, artists, albums, playlists, videos, lyrics, users } }
}

function stripMarkup(value) {
  return String(value || '').replace(/<[^>]+>/g, '').replace(/&amp;/g, '&').trim()
}

function lyricSnippet(content, keyword) {
  const lines = String(content || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  const normalized = String(keyword || '').toLocaleLowerCase()
  const index = Math.max(0, lines.findIndex(line => line.toLocaleLowerCase().includes(normalized)))
  return lines.slice(index, index + 3).join(' / ').slice(0, 240)
}

function secureQqImageUrl(value) {
  if (!value) return ''
  try {
    const url = new URL(String(value).startsWith('//') ? `https:${value}` : String(value))
    const officialQqHost = ['qq.com', 'gtimg.cn', 'qpic.cn']
      .some(hostname => url.hostname === hostname || url.hostname.endsWith(`.${hostname}`))
    if (!officialQqHost) return ''
    url.protocol = 'https:'
    return url.toString()
  } catch {
    return ''
  }
}

function singerImageUrl(mid) {
  return secureQqImageUrl(`https://y.gtimg.cn/music/photo_new/T001R500x500M000${mid}.jpg?max_age=2592000`)
}

function albumImageUrl(mid) {
  return secureQqImageUrl(`https://y.gtimg.cn/music/photo_new/T002R300x300M000${mid}.jpg?max_age=2592000`)
}

function publicPlaylist(item) {
  return {
    id: String(item?.dissid || item?.id || ''),
    name: String(item?.dissname || item?.title || '').trim(),
    description: String(item?.introduction || item?.desc || '').trim(),
    coverUrl: secureQqImageUrl(item?.imgurl || item?.picurl),
    creatorName: String(item?.creator?.name || item?.creator?.nick || item?.host_nick || '').trim(),
    creatorAvatarUrl: secureQqImageUrl(item?.creator?.avatarUrl || item?.creator?.headurl || item?.headurl),
    listenCount: Math.max(0, Number(item?.listennum) || 0),
    trackCount: Math.max(0, Number(item?.songnum || item?.song_count) || 0),
    tags: Array.isArray(item?.tag) ? item.tag.map(tag => tag?.name).filter(Boolean) : [],
  }
}

function playlistTrack(item) {
  const songMid = String(item?.mid || item?.songmid || '').trim()
  const albumMid = String(item?.album?.mid || item?.albummid || '').trim()
  return {
    songMid,
    mediaMid: String(item?.file?.media_mid || item?.strMediaMid || item?.media_mid || songMid).trim(),
    name: String(item?.name || item?.title || item?.songname || '').trim(),
    artists: Array.isArray(item?.singer) ? item.singer.map(artist => artist?.name).filter(Boolean) : [],
    album: String(item?.album?.name || item?.album?.title || item?.albumname || '').trim(),
    albumMid,
    durationMs: Math.max(0, Number(item?.interval) || 0) * 1000,
    payPlay: Boolean(item?.pay?.pay_play || item?.pay?.payplay),
    qualities: {
      flac: Number(item?.file?.size_flac || item?.sizeflac) > 0,
      320: Number(item?.file?.size_320mp3 || item?.size320) > 0,
      128: Number(item?.file?.size_128mp3 || item?.size128) > 0,
      m4a: true,
    },
  }
}

async function homePlaylists(requestUrl) {
  const limit = Math.min(24, Math.max(1, Number.parseInt(requestUrl.searchParams.get('limit') || '12', 10)))
  const page = Math.min(20, Math.max(1, Number.parseInt(requestUrl.searchParams.get('page') || '1', 10)))
  const start = (page - 1) * limit
  const url = new URL('https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg')
  Object.entries({
    picmid: '1',
    rnd: String(Math.random()),
    g_tk: '5381',
    loginUin: '0',
    hostUin: '0',
    format: 'json',
    inCharset: 'utf8',
    outCharset: 'utf-8',
    notice: '0',
    platform: 'yqq.json',
    needNewCode: '0',
    categoryId: '10000000',
    sortId: '5',
    sin: String(start),
    ein: String(start + limit - 1),
  }).forEach(([name, value]) => url.searchParams.set(name, value))

  const payload = await upstreamJson(url, { Referer: 'https://y.qq.com/n/ryqq/category' })
  const list = Array.isArray(payload?.data?.list) ? payload.data.list : []
  const playlists = list.map(publicPlaylist).filter(item => /^\d+$/.test(item.id) && item.name)
  return { status: 200, body: { page, pageSize: limit, hasNext: playlists.length >= limit, playlists } }
}

async function playlist(requestUrl) {
  const id = requestUrl.searchParams.get('id')?.trim() || ''
  const limit = Math.min(100, Math.max(1, Number.parseInt(requestUrl.searchParams.get('limit') || '60', 10)))
  if (!/^\d{5,20}$/.test(id)) {
    return { status: 400, body: { code: 'INVALID_PLAYLIST_ID', message: 'QQ 音乐歌单标识不正确' } }
  }

  const data = {
    comm: { ct: 24, cv: 0 },
    req_0: {
      module: 'music.srfDissInfo.aiDissInfo',
      method: 'uniform_get_Dissinfo',
      param: {
        disstid: Number(id), enc_host_uin: '', tag: 1, userinfo: 1,
        song_begin: 0, song_num: limit, onlysonglist: 0,
      },
    },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('data', JSON.stringify(data))
  const payload = await upstreamJson(url, { Referer: `https://y.qq.com/n/ryqq/playlist/${id}` })
  const response = payload?.req_0
  const directory = response?.data?.dirinfo
  if (Number(response?.code) !== 0 || Number(response?.data?.code) !== 0 || !directory) {
    return { status: 404, body: { code: 'PLAYLIST_NOT_FOUND', message: 'QQ 音乐歌单不存在或未公开' } }
  }
  const tracks = (Array.isArray(response?.data?.songlist) ? response.data.songlist : [])
    .map(playlistTrack)
    .filter(track => /^[A-Za-z0-9]+$/.test(track.songMid) && track.name)
  return {
    status: 200,
    body: {
      ...publicPlaylist({ ...directory, dissid: id, dissname: directory.title, imgurl: directory.picurl }),
      description: String(directory.desc || '').trim(),
      externalUrl: `https://y.qq.com/n/ryqq/playlist/${id}`,
      tracks,
    },
  }
}

async function artist(requestUrl) {
  const mid = requestUrl.searchParams.get('mid')?.trim() || ''
  const songPage = Math.min(50, Math.max(1, Number.parseInt(requestUrl.searchParams.get('songPage') || '1', 10)))
  const songLimit = Math.min(30, Math.max(5, Number.parseInt(requestUrl.searchParams.get('songLimit') || '20', 10)))
  const albumPage = Math.min(50, Math.max(1, Number.parseInt(requestUrl.searchParams.get('albumPage') || '1', 10)))
  const albumLimit = Math.min(24, Math.max(5, Number.parseInt(requestUrl.searchParams.get('albumLimit') || '12', 10)))
  if (!/^[A-Za-z0-9]{5,30}$/.test(mid)) {
    return { status: 400, body: { code: 'INVALID_ARTIST_ID', message: 'QQ 音乐歌手标识不正确' } }
  }

  const data = {
    comm: { ct: 24, cv: 0 },
    singer: {
      module: 'music.musichallSinger.SingerInfoInter',
      method: 'GetSingerDetail',
      param: { singer_mids: [mid], ex_singer: 1, wiki_singer: 1, group_singer: 0 },
    },
    songs: {
      module: 'musichall.song_list_server',
      method: 'GetSingerSongList',
      param: { singerMid: mid, begin: (songPage - 1) * songLimit, num: songLimit, order: 1 },
    },
    albums: {
      module: 'music.musichallAlbum.AlbumListServer',
      method: 'GetAlbumList',
      param: { singerMid: mid, begin: (albumPage - 1) * albumLimit, num: albumLimit, order: 1 },
    },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('data', JSON.stringify(data))
  const payload = await upstreamJson(url, { Referer: `https://y.qq.com/n/ryqq/singer/${mid}`, Origin: 'https://y.qq.com' })
  const singer = payload?.singer?.data?.singer_list?.[0]
  if (Number(payload?.singer?.code) !== 0 || !singer?.basic_info) {
    return { status: 404, body: { code: 'ARTIST_NOT_FOUND', message: 'QQ 音乐歌手不存在' } }
  }
  const basic = singer.basic_info
  const extra = singer.ex_info || {}
  const songItems = Array.isArray(payload?.songs?.data?.songList) ? payload.songs.data.songList : []
  const albumItems = Array.isArray(payload?.albums?.data?.albumList) ? payload.albums.data.albumList : []
  const songTotal = Math.max(0, Number(payload?.songs?.data?.totalNum) || 0)
  const albumTotal = Math.max(0, Number(payload?.albums?.data?.total) || 0)
  const areaNames = { 1: '中国大陆', 2: '中国香港', 3: '中国台湾', 4: '韩国', 5: '日本', 6: '欧美' }
  return {
    status: 200,
    body: {
      mid,
      name: String(basic.name || '').trim(),
      imageUrl: secureQqImageUrl(singer?.pic?.pic) || singerImageUrl(mid),
      foreignName: String(extra.foreign_name || '').trim(),
      birthday: String(extra.birthday || '').trim(),
      area: areaNames[Number(extra.area)] || '',
      description: String(extra.desc || '').trim(),
      externalUrl: `https://y.qq.com/n/ryqq/singer/${mid}`,
      songTotal,
      albumTotal,
      songPage,
      songPageSize: songLimit,
      hasMoreSongs: songPage * songLimit < songTotal,
      albumPage,
      albumPageSize: albumLimit,
      hasMoreAlbums: albumPage * albumLimit < albumTotal,
      tracks: songItems.map(item => playlistTrack(item?.songInfo || item))
        .filter(track => /^[A-Za-z0-9]+$/.test(track.songMid) && track.name),
      albums: albumItems.map(item => ({
        mid: String(item?.albumMid || '').trim(),
        name: String(item?.albumName || '').trim(),
        coverUrl: item?.albumMid ? albumImageUrl(item.albumMid) : '',
        publishDate: String(item?.publishDate || '').trim(),
        type: String(item?.albumType || '').trim(),
        trackCount: Math.max(0, Number(item?.totalNum) || 0),
        externalUrl: item?.albumMid ? `https://y.qq.com/n/ryqq/albumDetail/${item.albumMid}` : '',
      })).filter(item => item.mid && item.name),
    },
  }
}

async function album(requestUrl) {
  const mid = requestUrl.searchParams.get('mid')?.trim() || ''
  if (!/^[A-Za-z0-9]{5,30}$/.test(mid)) {
    return { status: 400, body: { code: 'INVALID_ALBUM_ID', message: 'QQ 音乐专辑标识不正确' } }
  }
  const url = new URL('https://c.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg')
  url.searchParams.set('albummid', mid)
  url.searchParams.set('format', 'json')
  const payload = await upstreamJson(url, { Referer: `https://y.qq.com/n/ryqq/albumDetail/${mid}` })
  const data = payload?.data
  if (Number(payload?.code) !== 0 || !data?.name) {
    return { status: 404, body: { code: 'ALBUM_NOT_FOUND', message: 'QQ 音乐专辑不存在' } }
  }
  return {
    status: 200,
    body: {
      mid,
      name: String(data.name || '').trim(),
      coverUrl: albumImageUrl(mid),
      artists: [String(data.singername || '').trim()].filter(Boolean),
      artistMid: String(data.singermid || '').trim(),
      publishDate: String(data.aDate || '').trim(),
      genre: String(data.genre || '').trim(),
      language: String(data.lan || '').trim(),
      company: String(data.company || '').trim(),
      description: String(data.desc || '').trim(),
      trackCount: Math.max(0, Number(data.total_song_num || data.total) || 0),
      externalUrl: `https://y.qq.com/n/ryqq/albumDetail/${mid}`,
      tracks: (Array.isArray(data.list) ? data.list : []).map(playlistTrack)
        .filter(track => /^[A-Za-z0-9]+$/.test(track.songMid) && track.name),
    },
  }
}

async function video(requestUrl) {
  const id = requestUrl.searchParams.get('id')?.trim() || ''
  if (!/^[A-Za-z0-9]{5,30}$/.test(id)) {
    return { status: 400, body: { code: 'INVALID_VIDEO_ID', message: 'QQ 音乐视频标识不正确' } }
  }
  const data = {
    comm: { ct: 24, cv: 0 },
    req_0: {
      module: 'gosrf.Stream.MvUrlProxy',
      method: 'GetMvUrls',
      param: { vids: [id], request_typetypes: [1, 2, 3, 4] },
    },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('data', JSON.stringify(data))
  const payload = await upstreamJson(url, { Referer: `https://y.qq.com/n/ryqq/mv/${id}` })
  const variants = Array.isArray(payload?.req_0?.data?.[id]?.mp4) ? payload.req_0.data[id].mp4 : []
  const playable = variants.filter(item => Number(item?.code) === 0 && Array.isArray(item?.freeflow_url) && item.freeflow_url.length)
    .sort((left, right) => Number(right.fileSize || 0) - Number(left.fileSize || 0))[0]
  const playbackUrl = String(playable?.freeflow_url?.find(value => String(value).startsWith('https://')) || playable?.freeflow_url?.[0] || '')
    .replace(/^http:/, 'https:')
  if (!playbackUrl) {
    return { status: 404, body: { code: 'VIDEO_UNAVAILABLE', message: '当前视频暂时无法播放' } }
  }
  return { status: 200, body: { id, playbackUrl, durationMs: Math.max(0, Number(payload?.req_0?.data?.[id]?.duration) || 0) * 1000, quality: Number(playable?.filetype || 0), externalUrl: `https://y.qq.com/n/ryqq/mv/${id}` } }
}

async function play(request, requestUrl) {
  const cookie = safeCookie(request)
  if (!cookie) return { status: 401, body: { code: 'QQ_SESSION_REQUIRED', message: 'QQ 音乐登录态未配置或已失效' } }

  const songMid = requestUrl.searchParams.get('songmid')?.trim() || ''
  const mediaMid = requestUrl.searchParams.get('mediaId')?.trim() || songMid
  const quality = requestUrl.searchParams.get('quality') || '128'
  const fileType = qualityFiles[quality]
  if (!/^[A-Za-z0-9]+$/.test(songMid) || !/^[A-Za-z0-9]+$/.test(mediaMid) || !fileType) {
    return { status: 400, body: { code: 'INVALID_PLAYBACK_REQUEST', message: '播放参数不正确' } }
  }

  const uin = cookieValue(cookie, 'uin') || cookieValue(cookie, 'wxuin') || '0'
  const guid = String(Math.floor(100000000 + Math.random() * 9000000000))
  const data = {
    req_0: {
      module: 'vkey.GetVkeyServer',
      method: 'CgiGetVkey',
      param: {
        filename: [`${fileType.prefix}${songMid}${mediaMid}${fileType.extension}`],
        guid,
        songmid: [songMid],
        songtype: [0],
        uin,
        loginflag: 1,
        platform: '20',
      },
    },
    loginUin: uin,
    comm: { uin, format: 'json', ct: 24, cv: 0 },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('sign', vkeySign)
  url.searchParams.set('data', JSON.stringify(data))

  const payload = await upstreamJson(url, {
    Referer: 'https://y.qq.com/portal/player.html',
    Cookie: cookie,
  })
  const domain = payload?.req_0?.data?.sip?.find?.(item => !String(item).startsWith('http://ws'))
    || payload?.req_0?.data?.sip?.[0]
  const purl = payload?.req_0?.data?.midurlinfo?.[0]?.purl || ''
  const playbackUrl = domain && purl ? secureQqPlaybackUrl(domain, purl) : ''
  if (!playbackUrl) {
    return {
      status: 404,
      body: { code: 'QQ_PLAYBACK_UNAVAILABLE', message: '当前登录账号无法获取该音质的播放地址' },
    }
  }
  return { status: 200, body: { url: playbackUrl, quality } }
}

function decodeBase64Text(value) {
  if (!value) return ''
  try {
    return Buffer.from(String(value), 'base64').toString('utf8')
  } catch {
    return ''
  }
}

async function lyric(requestUrl) {
  const songMid = requestUrl.searchParams.get('songmid')?.trim() || ''
  if (!/^[A-Za-z0-9]+$/.test(songMid)) {
    return { status: 400, body: { code: 'INVALID_LYRIC_REQUEST', message: '歌曲标识不正确' } }
  }

  const url = new URL('https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg')
  Object.entries({
    songmid: songMid,
    format: 'json',
    inCharset: 'utf8',
    outCharset: 'utf-8',
    notice: '0',
    platform: 'yqq.json',
    needNewCode: '0',
    pcachetime: String(Date.now()),
  }).forEach(([name, value]) => url.searchParams.set(name, value))

  const payload = await upstreamJson(url, { Referer: `https://y.qq.com/n/ryqq/songDetail/${songMid}` })
  if (Number(payload?.code) !== 0 || !payload?.lyric) {
    return { status: 404, body: { code: 'LYRIC_NOT_FOUND', message: '这首歌曲暂未提供歌词' } }
  }
  return {
    status: 200,
    body: {
      songMid,
      lyric: decodeBase64Text(payload.lyric),
      translation: decodeBase64Text(payload.trans),
      romanization: decodeBase64Text(payload.roma),
    },
  }
}

const server = http.createServer(async (request, response) => {
  const startedAt = Date.now()
  if (!isLoopback(request.socket.remoteAddress)) {
    json(response, 403, { code: 'LOOPBACK_ONLY', message: '仅允许本机访问' })
    return
  }

  let status = 500
  try {
    const requestUrl = new URL(request.url || '/', `http://${host}:${port}`)
    let result
    if (request.method === 'GET' && requestUrl.pathname === '/health') {
      result = { status: 200, body: { ready: true, name: 'sonora-qq-music-bridge' } }
    } else if (request.method === 'POST' && requestUrl.pathname === '/auth/qr/start') {
      result = { status: 200, body: await startQrLogin() }
    } else if (request.method === 'GET' && requestUrl.pathname === '/auth/qr/status') {
      const loginId = requestUrl.searchParams.get('id')?.trim() || ''
      if (loginId) {
        const qrResult = await pollQrLogin(loginId)
        if (qrResult.status === 'FAILED') {
          console.error(`[qq-bridge] ${qrResult.errorCode || 'QQ_LOGIN_COMPLETION_FAILED'} stage=${qrResult.stage || 'completion'}`)
        }
        result = { status: 200, body: qrResult }
      } else {
        result = { status: 400, body: { code: 'INVALID_LOGIN_ID', message: '缺少二维码登录标识' } }
      }
    } else if (request.method === 'DELETE' && requestUrl.pathname === '/auth/qr') {
      const loginId = requestUrl.searchParams.get('id')?.trim() || ''
      result = loginId
        ? { status: 200, body: await cancelQrLogin(loginId) }
        : { status: 400, body: { code: 'INVALID_LOGIN_ID', message: '缺少二维码登录标识' } }
    } else if (request.method === 'GET' && requestUrl.pathname === '/search') {
      result = await search(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/home/playlists') {
      result = await homePlaylists(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/playlist') {
      result = await playlist(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/artist') {
      result = await artist(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/album') {
      result = await album(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/video') {
      result = await video(requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/play') {
      result = await play(request, requestUrl)
    } else if (request.method === 'GET' && requestUrl.pathname === '/lyric') {
      result = await lyric(requestUrl)
    } else {
      result = { status: 404, body: { code: 'NOT_FOUND', message: '接口不存在' } }
    }
    status = result.status
    json(response, result.status, result.body)
  } catch (error) {
    const timeout = error?.name === 'TimeoutError'
    status = timeout ? 504 : 502
    const errorCode = error?.publicCode || (timeout ? 'UPSTREAM_TIMEOUT' : 'UPSTREAM_FAILED')
    const message = error?.publicMessage || (timeout ? 'QQ 音乐请求超时' : 'QQ 音乐接口暂时不可用')
    console.error(`[qq-bridge] ${errorCode} stage=${error?.stage || 'request'} cause=${error?.message || 'unknown'}`)
    json(response, status, {
      code: errorCode,
      message,
    })
  } finally {
    console.log(`${new Date().toISOString()} ${request.method} ${new URL(request.url || '/', `http://${host}`).pathname} ${status} ${Date.now() - startedAt}ms`)
  }
})

server.listen(port, host, () => {
  console.log(`Sonora QQ Music Bridge listening on http://${host}:${port}`)
})
