package edu.fnosari.classmanager.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The app's main colour, chosen by the teacher. Plain ARGB maths so it can be unit-tested and
 * used from places that have no Compose scope, such as the system bar setup in MainActivity.
 */
object Palette {
    const val DEFAULT = 0xFF16866F.toInt()

    /** Offered in settings. Deep enough to carry white text in light mode. */
    val PRESETS = listOf(
        DEFAULT,                  // green (Pronote-ish)
        0xFF10777C.toInt(),       // teal
        0xFF1D6FA5.toInt(),       // blue
        0xFF4A55A2.toInt(),       // indigo
        0xFF7A4E9B.toInt(),       // purple
        0xFFA8447A.toInt(),       // plum
        0xFFB4462F.toInt(),       // brick
        0xFFB07213.toInt(),       // ochre
        0xFF4E5D6C.toInt(),       // slate
    )

    fun red(argb: Int) = (argb shr 16) and 0xFF
    fun green(argb: Int) = (argb shr 8) and 0xFF
    fun blue(argb: Int) = argb and 0xFF

    /** [t] = 0 keeps [from], 1 gives [to]. Alpha is taken from [from]. */
    fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * f).roundToInt().coerceIn(0, 255)
        return (from and 0xFF000000.toInt()) or
            (mix(red(from), red(to)) shl 16) or
            (mix(green(from), green(to)) shl 8) or
            mix(blue(from), blue(to))
    }

    fun lighten(argb: Int, t: Float) = blend(argb, 0xFFFFFFFF.toInt(), t)
    fun darken(argb: Int, t: Float) = blend(argb, 0xFF000000.toInt(), t)

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Float {
        fun channel(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(red(argb)) +
            0.7152f * channel(green(argb)) +
            0.0722f * channel(blue(argb))
    }

    /**
     * True when dark text and dark system-bar icons belong on top of this colour.
     *
     * The threshold sits above the WCAG crossover (0.179), which keeps white text on every
     * preset in light mode — they are brand surfaces and all land below it — while the paler
     * primaries used in dark mode flip to dark text, where white would be unreadable.
     */
    fun isLight(argb: Int): Boolean = luminance(argb) > 0.28f

    fun onColor(argb: Int): Int = if (isLight(argb)) 0xFF14201C.toInt() else 0xFFFFFFFF.toInt()

    /**
     * The colour actually used as `colorScheme.primary`: the chosen one in light mode, a lighter
     * version in dark mode, where a deep colour would swallow the top bar.
     */
    fun primaryFor(seed: Int, dark: Boolean): Int = if (dark) lighten(seed, 0.45f) else seed
}
