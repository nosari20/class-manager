package edu.fnosari.classmanager.ui

import edu.fnosari.classmanager.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    private val green = Palette.DEFAULT      // 0xFF16866F

    @Test fun blendEndsAreTheInputs() {
        assertEquals(green, Palette.blend(green, 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), Palette.blend(green, 0xFFFFFFFF.toInt(), 1f))
    }

    @Test fun blendKeepsAlphaAndMovesTowardsTarget() {
        val mid = Palette.blend(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f)
        assertEquals(0xFF, (mid shr 24) and 0xFF)
        assertEquals(128, Palette.red(mid))
        assertEquals(128, Palette.green(mid))
        assertEquals(128, Palette.blue(mid))
    }

    @Test fun lightenRaisesLuminanceAndDarkenLowersIt() {
        assertTrue(Palette.luminance(Palette.lighten(green, 0.5f)) > Palette.luminance(green))
        assertTrue(Palette.luminance(Palette.darken(green, 0.5f)) < Palette.luminance(green))
    }

    @Test fun luminanceOfBlackAndWhiteAreTheExtremes() {
        assertEquals(0f, Palette.luminance(0xFF000000.toInt()), 0.001f)
        assertEquals(1f, Palette.luminance(0xFFFFFFFF.toInt()), 0.001f)
    }

    @Test fun everyPresetTakesWhiteTextInLightMode() {
        // the light theme paints the top bar with the raw preset, so none may read as "light"
        Palette.PRESETS.forEach { preset ->
            assertFalse("preset ${Integer.toHexString(preset)}", Palette.isLight(preset))
            assertEquals(0xFFFFFFFF.toInt(), Palette.onColor(preset))
        }
    }

    @Test fun darkModePrimaryIsLighterThanTheSeed() {
        Palette.PRESETS.forEach { preset ->
            val darkPrimary = Palette.primaryFor(preset, dark = true)
            assertTrue(Palette.luminance(darkPrimary) > Palette.luminance(preset))
        }
        assertEquals(green, Palette.primaryFor(green, dark = false))
    }

    @Test fun darkModeTopBarTakesDarkTextForEveryPreset() {
        // the dark theme lightens the primary, and white on a pale bar is unreadable
        Palette.PRESETS.forEach { preset ->
            val darkPrimary = Palette.primaryFor(preset, dark = true)
            assertTrue("preset ${Integer.toHexString(preset)}", Palette.isLight(darkPrimary))
            assertEquals(0xFF14201C.toInt(), Palette.onColor(darkPrimary))
        }
    }

    @Test fun onColorFlipsWithBackgroundBrightness() {
        assertEquals(0xFFFFFFFF.toInt(), Palette.onColor(0xFF000000.toInt()))
        assertEquals(0xFF14201C.toInt(), Palette.onColor(0xFFFFFFFF.toInt()))
    }
}
