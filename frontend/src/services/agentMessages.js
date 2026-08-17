export function restoreHistoryMessage(item) {
  return {
    ...item,
    actions: Array.isArray(item?.actions) ? item.actions : [],
    error: false,
  }
}
