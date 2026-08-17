const QQ_HOME_PAGE_KEY_PREFIX = 'sonora:music:qq-home-page'
export const QQ_HOME_MAX_PAGE = 20

function storageKey(userId) {
  return `${QQ_HOME_PAGE_KEY_PREFIX}:${userId || 'anonymous'}`
}

export function readQqHomePage(storage, userId) {
  try {
    const page = Number.parseInt(storage?.getItem(storageKey(userId)) || '1', 10)
    return Number.isSafeInteger(page) && page > 0 && page <= QQ_HOME_MAX_PAGE ? page : 1
  } catch {
    return 1
  }
}

export function writeQqHomePage(storage, userId, page) {
  if (!Number.isSafeInteger(page) || page < 1 || page > QQ_HOME_MAX_PAGE) return
  try {
    storage?.setItem(storageKey(userId), String(page))
  } catch {
    // Browsing can continue when storage is disabled or full.
  }
}

export function nextQqHomePage(page) {
  return Number.isSafeInteger(page) && page > 0 && page < QQ_HOME_MAX_PAGE ? page + 1 : 1
}
