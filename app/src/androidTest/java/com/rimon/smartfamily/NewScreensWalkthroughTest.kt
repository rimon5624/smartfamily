package com.rimon.smartfamily

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.rimon.smartfamily.data.db.ApplianceEntity
import com.rimon.smartfamily.data.db.TreePersonEntity
import com.rimon.smartfamily.model.AppModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 2.2: the new screens (wizards, tree, dryer, keyboard over a bottom field), in Hebrew, with screenshots for review. */
@RunWith(AndroidJUnit4::class)
class NewScreensWalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val c get() = (target.applicationContext as SmartFamilyApp).container
    private val shots by lazy { File(target.getExternalFilesDir(null), "screens").apply { mkdirs() } }
    private lateinit var fam: String

    @Before fun setUp() = runBlocking<Unit> {
        c.settings.setOnboardingDone(true); c.settings.setLastSeenVersion(BuildConfig.VERSION_NAME); c.settings.setUpdateChecks(false)
        c.settings.setAppLock(false); c.settings.setSecureScreens(false)
        com.rimon.smartfamily.security.Sensitive.allowCaptureForTests = true
        if (c.db.families().all().none { it.deletedAt == null }) c.settings.setActiveFamily(c.families.createFamily("משפחת בדיקה", AppModule.entries.toSet()).id)
        fam = c.settings.current().activeFamilyId
        val me = c.settings.current().userId
        // a three-generation tree
        if (c.db.treePersons().observeForFamily(fam).first().isEmpty()) {
            fun p(id: String, name: String, parents: String = "", spouse: String = "", birth: String = "", member: String = "") =
                TreePersonEntity(id = id, familyId = fam, name = name, parents = parents, spouse = spouse, birth = birth, memberId = member)
            listOf(
                p("g1", "סבא שרול", spouse = "g2", birth = "1956-03-01"), p("g2", "סבתא אפרת", spouse = "g1", birth = "1958-07-11"),
                p("c1", "אביתר", "g1,g2", spouse = "c2", birth = "1988-01-20", member = me), p("c2", "בתיה", spouse = "c1", birth = "1990-02-02"),
                p("c3", "אח", "g1,g2", birth = "1992-05-05"),
                p("k1", "הדס", "c1,c2", birth = "2016-09-09"), p("k2", "צור", "c1,c2", birth = "2019-11-11"),
            ).forEach { c.db.treePersons().upsert(it) }
        }
        if (c.db.appliances().observeSorted(fam).first().none { it.type == "dryer" })
            c.db.appliances().upsert(ApplianceEntity(id = "dryer-test", familyId = fam, type = "dryer", name = "מייבש", brand = "Samsung", model = "DV90", createdBy = me))
    }

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(1200)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        File(shots, "n_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }
    private fun go(route: String) { MainActivity.pendingRoute.value = route; compose.waitForIdle(); Thread.sleep(600) }
    private fun hasTag(t: String) = compose.onAllNodesWithTag(t, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun str(id: Int) = target.getString(id)

    @Test fun newScreens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { hasTag("area_home") }
            shot("01_start_home")
            go("ai"); compose.waitUntil(5_000) { hasTag("ai_status") }; shot("02_ai_wizard")
            go("notify_check"); compose.waitUntil(5_000) { hasTag("nc_summary") }; shot("03_notify_check")
            go("transcription"); shot("04_transcription")
            go("smarthome"); shot("05_smarthome")
            go("cloud"); shot("06_cloud")
            go("backup"); shot("07_backup")
            go("prices"); compose.waitForIdle()
            if (hasText(str(R.string.prices_stores).substringBefore("%").trim().take(6))) runCatching {
                compose.onAllNodesWithText(str(R.string.prices_stores).substringBefore("(").substringBefore("%").trim(), substring = true).onFirst().performClick()
            }
            shot("08_prices_stores")
            go("family"); compose.waitUntil(8_000) { hasTag("tree_graph") }; shot("09_tree")
            go("appliance/dryer-test"); compose.waitForIdle()
            runCatching { compose.onAllNodesWithText(str(R.string.apl_tab_wash)).onFirst().performClick() }
            shot("10_dryer")
            // keyboard over the lowest field of a dialog: add appliance → focus the last text field
            go("appliances"); compose.waitUntil(8_000) { hasTag("apl_add_fab") }
            compose.onNodeWithTag("apl_add_fab").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasSetTextAction()).onLast().performClick()
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("בדיקה")
            shot("11_keyboard_dialog")
            // chat with the keyboard open
            val chat = runBlocking { c.chat.ensureFamilyChat(fam, c.settings.current().userId, listOf(c.settings.current().userId), "המשפחה") }
            if (chat != null) {
                repeat(2) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); Thread.sleep(400) }
                go("chat/${chat.id}"); compose.waitUntil(8_000) { hasTag("chat_input") }
                compose.onNodeWithTag("chat_input").performClick(); compose.onNodeWithTag("chat_input").performTextInput("שלום")
                shot("12_chat_keyboard")
            }
        }
    }
}
