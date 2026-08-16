export function shuffleTracks(tracks, random = Math.random) {
  const shuffled = Array.isArray(tracks) ? [...tracks] : []
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const target = Math.floor(Math.max(0, Math.min(0.999999999, random())) * (index + 1))
    ;[shuffled[index], shuffled[target]] = [shuffled[target], shuffled[index]]
  }
  return shuffled
}

