import assert from 'node:assert/strict'
import test from 'node:test'
import { storeBrowserPassword } from './browserCredentials.js'

test('stores a successful login in the browser password manager', async () => {
  const stored = []
  class FakePasswordCredential {
    constructor(data) {
      Object.assign(this, data)
    }
  }
  const environment = {
    PasswordCredential: FakePasswordCredential,
    navigator: {
      credentials: {
        store: async credential => stored.push(credential),
      },
    },
  }

  const saved = await storeBrowserPassword('  listener  ', 'music-password', environment)

  assert.equal(saved, true)
  assert.equal(stored.length, 1)
  assert.deepEqual({
    id: stored[0].id,
    name: stored[0].name,
    password: stored[0].password,
  }, {
    id: 'listener',
    name: 'listener',
    password: 'music-password',
  })
})

test('does not persist a password when the browser credential API is unavailable', async () => {
  assert.equal(await storeBrowserPassword('listener', 'music-password', {}), false)
})

test('credential-manager rejection never turns a successful login into a failure', async () => {
  class FakePasswordCredential {
    constructor(data) {
      Object.assign(this, data)
    }
  }
  const environment = {
    PasswordCredential: FakePasswordCredential,
    navigator: {
      credentials: {
        store: async () => { throw new Error('user declined') },
      },
    },
  }

  assert.equal(await storeBrowserPassword('listener', 'music-password', environment), false)
})
