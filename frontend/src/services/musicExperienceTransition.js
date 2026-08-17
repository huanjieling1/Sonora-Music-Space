import { nextTick } from 'vue'

const ENTER_DURATION_MS = 520
const EXIT_DURATION_MS = 360

export async function runMusicExperienceTransition(navigate, direction = 'enter') {
  if (typeof document === 'undefined' || typeof navigate !== 'function') return navigate?.()

  const root = document.documentElement
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (reducedMotion) return navigate()

  setTransitionOrigin(root, direction)
  root.dataset.musicExperienceTransition = direction

  if (typeof document.startViewTransition === 'function') {
    root.dataset.musicExperienceNative = 'true'
    const transition = document.startViewTransition(async () => {
      await navigate()
      await nextTick()
    })
    transition.finished.finally(() => clearTransitionState(root))
    return transition.updateCallbackDone
  }

  try {
    if (direction === 'exit') {
      await wait(EXIT_DURATION_MS)
      await navigate()
      await nextTick()
      return
    }
    await navigate()
    await nextTick()
    await wait(ENTER_DURATION_MS)
  } finally {
    clearTransitionState(root)
  }
}

function setTransitionOrigin(root, direction) {
  const dock = direction === 'enter'
    ? document.querySelector('.player-dock:not(.immersive)')
    : null
  const rect = dock?.getBoundingClientRect()

  if (rect?.width && rect?.height) {
    root.style.setProperty('--music-transition-origin-x', `${rect.left + rect.width / 2}px`)
    root.style.setProperty('--music-transition-origin-y', `${rect.top + rect.height / 2}px`)
    return
  }

  root.style.setProperty('--music-transition-origin-x', 'calc(100vw - 130px)')
  root.style.setProperty('--music-transition-origin-y', 'calc(100vh - 54px)')
}

function clearTransitionState(root) {
  delete root.dataset.musicExperienceTransition
  delete root.dataset.musicExperienceNative
  root.style.removeProperty('--music-transition-origin-x')
  root.style.removeProperty('--music-transition-origin-y')
}

function wait(milliseconds) {
  return new Promise(resolve => window.setTimeout(resolve, milliseconds))
}
