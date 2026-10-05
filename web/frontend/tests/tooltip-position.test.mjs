import test from 'node:test'
import assert from 'node:assert/strict'
import { tooltipPosition } from '../src/tooltip-position.ts'

const viewport = { left: 0, top: 0, width: 1000, height: 800 }
const size = { width: 384, height: 200 }

test('help stays directly below its trigger when there is enough space', () => {
  assert.deepEqual(tooltipPosition({ left: 100, top: 100, width: 24, height: 24 }, size, viewport), { left: 100, top: 124 })
})

test('help shifts away from either horizontal edge and flips above the bottom edge', () => {
  assert.deepEqual(tooltipPosition({ left: 950, top: 700, width: 24, height: 24 }, size, viewport), { left: 604, top: 500 })
  assert.equal(tooltipPosition({ left: -5, top: 100, width: 24, height: 24 }, size, viewport).left, 12)
})

test('oversized help stays within a narrow, offset visual viewport', () => {
  const narrow = { left: 100, top: 200, width: 300, height: 160 }
  assert.deepEqual(tooltipPosition({ left: 380, top: 330, width: 24, height: 24 }, size, narrow), { left: 112, top: 212 })
})
