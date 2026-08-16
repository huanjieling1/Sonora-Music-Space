import { createDecipheriv } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const [songMid, suppliedMediaMid] = process.argv.slice(2)
if (!songMid || !/^[A-Za-z0-9]+$/.test(songMid)) {
  throw new Error('Usage: node diagnose-playback.js <songMid> [mediaMid]')
}
const mediaMid = suppliedMediaMid || songMid
const sessionDirectory = path.resolve(process.cwd(), '../../runtime-data')
const key = Buffer.from((await readFile(path.join(sessionDirectory, 'qq-session.key'), 'utf8')).trim(), 'base64')
const payload = Buffer.from((await readFile(path.join(sessionDirectory, 'qq-session.dat'), 'utf8')).trim(), 'base64')
const iv = payload.subarray(0, 12)
const encrypted = payload.subarray(12)
const tag = encrypted.subarray(encrypted.length - 16)
const ciphertext = encrypted.subarray(0, encrypted.length - 16)
const decipher = createDecipheriv('aes-256-gcm', key, iv)
decipher.setAuthTag(tag)
const cookie = Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8')

function cookieValue(name) {
  return cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`, 'i'))?.[1] || ''
}

const uin = cookieValue('uin') || cookieValue('wxuin') || '0'
const importantFields = ['uin', 'wxuin', 'qm_keyst', 'qqmusic_key', 'psrf_qqopenid', 'psrf_qqaccess_token', 'tmeLoginType']
console.log(JSON.stringify({
  maskedUin: uin.length > 4 ? `${'*'.repeat(Math.min(8, uin.length - 4))}${uin.slice(-4)}` : 'missing',
  cookieFieldCount: cookie.split(';').filter(Boolean).length,
  importantFields: Object.fromEntries(importantFields.map(name => [name, Boolean(cookieValue(name))])),
}, null, 2))

const types = {
  flac: ['F000', '.flac'],
  320: ['M800', '.mp3'],
  128: ['M500', '.mp3'],
  m4a: ['C400', '.m4a'],
}
for (const [quality, [prefix, extension]] of Object.entries(types)) {
  const filename = `${prefix}${songMid}${mediaMid}${extension}`
  const guid = '1429839143'
  const data = {
    req_0: { module: 'vkey.GetVkeyServer', method: 'CgiGetVkey', param: {
      filename: [filename], guid, songmid: [songMid], songtype: [0], uin, loginflag: 1, platform: '20',
    } },
    loginUin: uin,
    comm: { uin, format: 'json', ct: 24, cv: 0 },
  }
  const url = new URL('https://u.y.qq.com/cgi-bin/musicu.fcg')
  url.searchParams.set('format', 'json')
  url.searchParams.set('sign', process.env.QQ_MUSIC_VKEY_SIGN || 'zzannc1o6o9b4i971602f3554385022046ab796512b7012')
  url.searchParams.set('data', JSON.stringify(data))
  const response = await fetch(url, { headers: {
    Accept: 'application/json,text/plain,*/*', Cookie: cookie,
    Referer: 'https://y.qq.com/portal/player.html', 'User-Agent': 'Mozilla/5.0 SonoraDiagnostic/1.0',
  } })
  const body = await response.json()
  const item = body?.req_0?.data?.midurlinfo?.[0] || {}
  console.log(JSON.stringify({
    quality,
    httpStatus: response.status,
    topCode: body?.code,
    requestCode: body?.req_0?.code,
    result: item.result,
    message: item.errmsg || item.msg || '',
    purlPresent: Boolean(item.purl),
    vkeyPresent: Boolean(item.vkey),
    sipCount: body?.req_0?.data?.sip?.length || 0,
  }))
  const bridgeUrl = new URL('http://127.0.0.1:3200/play')
  bridgeUrl.searchParams.set('songmid', songMid)
  bridgeUrl.searchParams.set('mediaId', mediaMid)
  bridgeUrl.searchParams.set('quality', quality)
  const bridgeResponse = await fetch(bridgeUrl, { headers: { 'X-QQ-Music-Cookie': cookie } })
  const bridgeBody = await bridgeResponse.json()
  let httpsStatus = null
  if (bridgeBody.url) {
    const upgraded = new URL(bridgeBody.url)
    upgraded.protocol = 'https:'
    const probe = await fetch(upgraded, { method: 'HEAD', redirect: 'manual' })
    httpsStatus = probe.status
  }
  console.log(JSON.stringify({
    quality,
    bridgeStatus: bridgeResponse.status,
    bridgeCode: bridgeBody.code || '',
    bridgeUrlPresent: Boolean(bridgeBody.url),
    bridgeProtocol: bridgeBody.url ? new URL(bridgeBody.url).protocol : '',
    bridgeHost: bridgeBody.url ? new URL(bridgeBody.url).host : '',
    upgradedHttpsStatus: httpsStatus,
  }))
}
