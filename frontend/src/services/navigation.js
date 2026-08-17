function isInternalLocation(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
}

export function returnState(route) {
  return { returnTo: route?.fullPath || '/' }
}

export function navigateBack(router, fallback = '/music', browserHistory = window.history) {
  const previous = browserHistory?.state?.back
  if (isInternalLocation(previous)) return router.back()

  const returnTo = browserHistory?.state?.returnTo
  if (isInternalLocation(returnTo)) return router.push(returnTo)

  return router.push(fallback)
}
