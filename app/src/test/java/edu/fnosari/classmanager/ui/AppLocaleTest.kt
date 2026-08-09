package edu.fnosari.classmanager.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import edu.fnosari.classmanager.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the pre-Android-13 path, where the app overrides the resource configuration itself.
 * On API 33+ the framework owns the setting, so [AppLocale.wrap] is a no-op there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppLocaleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun wrapSwitchesResourcesToEnglish() {
        val wrapped = AppLocale.wrap(context, "en")
        assertEquals("Settings", wrapped.getString(R.string.settings))
    }

    @Test fun wrapSwitchesResourcesToFrench() {
        val wrapped = AppLocale.wrap(context, "fr")
        assertEquals("Réglages", wrapped.getString(R.string.settings))
    }

    @Test fun systemTagLeavesContextUntouched() {
        assertEquals(context, AppLocale.wrap(context, AppLocale.SYSTEM))
    }

    @Test fun applyRequestsRecreateBelowApi33() {
        assertTrue(AppLocale.apply(context, "en"))
    }

    @Test fun currentReadsStoredValueBelowApi33() {
        assertEquals("fr", AppLocale.current(context, "fr"))
    }
}
