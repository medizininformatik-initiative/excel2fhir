type Bounds = { left: number; top: number; width: number; height: number }

/** Keep the preferred position below the trigger unless a viewport edge interferes. */
export function tooltipPosition(anchor: Bounds, size: Pick<Bounds, 'width' | 'height'>, viewport: Bounds) {
  const margin = 12
  const leftEdge = viewport.left + margin
  const topEdge = viewport.top + margin
  const rightEdge = viewport.left + viewport.width - margin
  const bottomEdge = viewport.top + viewport.height - margin
  const width = Math.min(size.width, Math.max(0, viewport.width - 2 * margin))
  const height = Math.min(size.height, Math.max(0, viewport.height - 2 * margin))
  const below = anchor.top + anchor.height
  const above = anchor.top - height
  const preferredTop = below + height > bottomEdge && above >= topEdge ? above : below
  return {
    left: Math.max(leftEdge, Math.min(anchor.left, rightEdge - width)),
    top: Math.max(topEdge, Math.min(preferredTop, bottomEdge - height))
  }
}
