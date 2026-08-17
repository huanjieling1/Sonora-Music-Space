export async function storeBrowserPassword(account, password, environment = globalThis) {
  const normalizedAccount = String(account || '').trim()
  const normalizedPassword = String(password || '')
  const credentials = environment?.navigator?.credentials
  const PasswordCredential = environment?.PasswordCredential

  if (!normalizedAccount || !normalizedPassword || !credentials?.store || typeof PasswordCredential !== 'function') {
    return false
  }

  try {
    const credential = new PasswordCredential({
      id: normalizedAccount,
      name: normalizedAccount,
      password: normalizedPassword,
    })
    await credentials.store(credential)
    return true
  } catch {
    // Password-manager support and save prompts are controlled by the browser.
    return false
  }
}
