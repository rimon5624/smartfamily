package com.rimon.smartfamily

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.model.Hub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Walks through the whole app on an emulator, in Hebrew, with every feature switched on:
 * every bottom-bar item, everything under "More", every area and every section of every area.
 * Fails if any of them does not open or crashes; saves a screenshot of each screen for review.
 */
@RunWith(AndroidJUnit4::class)
class AppWalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val shots by lazy { File(target.getExternalFilesDir(null), "screens").apply { deleteRecursively(); mkdirs() } }
    private var n = 0

    @Before fun setUp() = runBlocking {
        val c = (target.applicationContext as SmartFamilyApp).container
        c.settings.setOnboardingDone(true)
        c.settings.setLastSeenVersion(BuildConfig.VERSION_NAME)
        c.settings.setUpdateChecks(false)
        c.settings.setAppLock(false)
        if (c.db.families().all().none { it.deletedAt == null }) {
            val f = c.families.createFamily("משפחת בדיקה", AppModule.entries.toSet())
            c.settings.setActiveFamily(f.id)
        }
        c.settings.setLanguage("iw")
        withContext(Dispatchers.Main) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("iw")) }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400) // let animations settle
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(shots, "%02d_%s.png".format(n++, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun openItem(item: String) {
        if (has("nav_$item")) compose.onNodeWithTag("nav_$item").performClick()
        else {
            compose.onNodeWithTag("nav_more").performClick()
            compose.waitUntil(5_000) { has("more_$item") }
            compose.onNodeWithTag("more_$item").performClick()
        }
        compose.waitForIdle()
    }

    @Test fun everyAreaAndSectionOpens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { has("screen_today") }
            shot("today")
            for (hub in Hub.entries) {
                openItem(hub.key)
                compose.waitUntil(8_000) { has("area_${hub.key}") }
                for (sec in hub.sections) {
                    compose.waitUntil(5_000) { has("sec_${sec.key}") }
                    compose.onNodeWithTag("sec_${sec.key}", useUnmergedTree = true).performClick()
                    compose.waitForIdle()
                    // still in the same area (a section must not throw us somewhere else)
                    compose.onNodeWithTag("area_${hub.key}", useUnmergedTree = true).assertExists()
                    shot("${hub.key}_${sec.key}")
                }
            }
            // "More" sheet, families and settings
            compose.onNodeWithTag("nav_more").performClick(); shot("more")
            compose.onNodeWithTag("more_families").performClick(); shot("families")
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithTag("more_settings").performClick(); shot("settings")
            openItem("today")
            compose.waitUntil(5_000) { has("screen_today") }
        }
    }
}
