import { readonly, shallowRef } from 'vue'

const activeConfirmation = shallowRef(null)

export function confirmAction(options = {}) {
  if (activeConfirmation.value?.resolve) activeConfirmation.value.resolve(false)

  return new Promise((resolve) => {
    activeConfirmation.value = {
      eyebrow: options.eyebrow || '确认操作',
      title: options.title || '确定要继续吗？',
      message: options.message || '请确认这项操作是否符合你的预期。',
      subject: options.subject || '',
      hint: options.hint || '此操作无法撤销',
      confirmText: options.confirmText || '确认删除',
      cancelText: options.cancelText || '暂不删除',
      tone: options.tone || 'danger',
      resolve,
    }
  })
}

export function useConfirmation() {
  function finish(result) {
    const current = activeConfirmation.value
    if (!current) return
    activeConfirmation.value = null
    current.resolve(result)
  }

  return {
    confirmation: readonly(activeConfirmation),
    confirm: () => finish(true),
    cancel: () => finish(false),
  }
}

