import assert from 'node:assert/strict'
import test from 'node:test'
import { getMusicGreeting } from './musicGreeting.js'

test('music greeting follows the energy of each time period', () => {
  assert.equal(getMusicGreeting(new Date(2026, 7, 17, 8, 30)).salutation, '早上好')
  assert.equal(getMusicGreeting(new Date(2026, 7, 17, 12, 0)).status, '午间充电')
  assert.equal(getMusicGreeting(new Date(2026, 7, 17, 16, 0)).theme, 'afternoon')
  assert.equal(getMusicGreeting(new Date(2026, 7, 17, 20, 0)).salutation, '晚上好')
  assert.equal(getMusicGreeting(new Date(2026, 7, 17, 23, 0)).theme, 'night')
})

test('official holiday state overrides the ordinary greeting without losing its time theme', () => {
  const springFestival = getMusicGreeting(new Date(2026, 1, 17, 9, 0))
  assert.equal(springFestival.salutation, '春节快乐')
  assert.equal(springFestival.status, '法定节假日 · 春节')
  assert.equal(springFestival.theme, 'morning')
  assert.equal(springFestival.isHoliday, true)

  const nationalDay = getMusicGreeting(new Date(2026, 9, 1, 20, 0))
  assert.equal(nationalDay.salutation, '国庆快乐')
  assert.equal(nationalDay.theme, 'evening')
})

test('ordinary dates are not marked as official holidays', () => {
  const ordinaryDay = getMusicGreeting(new Date(2026, 7, 17, 12, 0))
  assert.equal(ordinaryDay.isHoliday, false)
  assert.equal(ordinaryDay.holidayName, '')
})
