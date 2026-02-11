/**
 * Convert Kotlin ARGB Int (signed 32-bit) to CSS rgba() string.
 *
 * Kotlin packs colors as 0xAARRGGBB in a signed Int.
 * Negative values (e.g. -65536 for red = 0xFFFF0000) are handled
 * via unsigned right shift (>>>).
 */
export function argbIntToCss(argb: number): string {
  const unsigned = argb >>> 0;
  const a = ((unsigned >> 24) & 0xff) / 255;
  const r = (unsigned >> 16) & 0xff;
  const g = (unsigned >> 8) & 0xff;
  const b = unsigned & 0xff;
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}
