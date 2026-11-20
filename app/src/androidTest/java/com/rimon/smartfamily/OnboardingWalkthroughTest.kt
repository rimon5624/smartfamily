package com.rimon.smartfamily

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** First run in Hebrew: language → name → "join my family" / "new family" → the join step. Screenshots each. */
@RunWith(AndroidJUnit4::class)
class OnboardingWalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val c get() = (target.applicationContext as SmartFamilyApp).container
    private val shots by lazy { File(target.getExternalFilesDir(null), "screens").apply { mkdirs() } }

    @Before fun firstRun() = runBlocking { c.settings.setOnboardingDone(false); c.settings.setSecureScreens(false); c.settings.setAppLock(false) }
    @After fun done() = runBlocking { c.settings.setOnboardingDone(true) }

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(900)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(shots, "onb_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    private fun text(id: Int) = target.getString(id)
    private fun has(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun firstRunOffersJoining() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { has(text(R.string.lang_he)) }
            shot("1_language")
            compose.onNodeWithText(text(R.string.lang_he)).performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("בתיה")
            shot("2_name")
            compose.onNodeWithText(text(R.string.next)).performClick()
            compose.waitUntil(5_000) { has(text(R.string.onb_join_title)) }
            shot("3_choice")
            compose.onAllNodesWithText(text(R.string.onb_join_title)).onFirst().performClick()
            compose.waitUntil(5_000) { has(text(R.string.cloud_sign_in)) || has(text(R.string.join_send)) }
            shot("4_join")
        }
    }
}
