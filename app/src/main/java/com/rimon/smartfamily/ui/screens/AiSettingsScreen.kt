package com.rimon.smartfamily.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.smart.GeminiClient
import com.rimon.smartfamily.ui.ShoppingViewModel
import com.rimon.smartfamily.ui.components.GuideStep
import com.rimon.smartfamily.ui.components.OpenSiteButton
import com.rimon.smartfamily.ui.components.SfTopAppBar
import com.rimon.smartfamily.ui.components.TestResult

private val KEY_PATTERN = Regex("^AIza[0-9A-Za-z_\\-]{30,}$")

/** 2.2 AI connection wizard: get a free key → paste (spotted on the clipboard) → pick a model from the live list → test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(vm: ShoppingViewModel, currentModel: String, privacyMode: Boolean, viewer: Viewer? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(currentModel.ifBlank { GeminiClient.DEFAULT_MODEL }) }
    var masked by remember { mutableStateOf(vm.maskedKey()) }
    var clipKey by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    val models by vm.models.collectAsState()
    val modelsError by vm.modelsError.collectAsState()
    val checks by vm.checks.collectAsState()
    val testing by vm.testing.collectAsState()
    val shared by remember(viewer?.familyId) { vm.familySharesKey(viewer?.familyId.orEmpty()) }.collectAsState(initial = emptyList())
    val sharing = shared.any { it.kind == "AI_KEY" }
    val connected = masked.isNotEmpty() || vm.usesFamilyKey

    // Coming back from AI Studio with a key copied: offer to paste it.
    val life = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        life.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (masked.isEmpty() && key.isBlank()) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() }.getOrNull()
                clipKey = t?.takeIf { KEY_PATTERN.matches(it) }
            }
        }
    }
    LaunchedEffect(connected) { if (connected) vm.loadModels() }

    fun checkText(c: GeminiClient.Check?): Pair<Boolean?, String> = when (c) {
        null -> null to ""
        is GeminiClient.Check.Ok -> true to context.getString(R.string.ai_check_ok, c.ms.toString())
        GeminiClient.Check.BadKey -> false to context.getString(R.string.ai_check_badkey)
        GeminiClient.Check.NotForKey -> false to context.getString(R.string.ai_check_notforkey)
        GeminiClient.Check.NeedsBilling -> false to context.getString(R.string.ai_check_billing)
        GeminiClient.Check.Quota -> false to context.getString(R.string.ai_check_quota)
        GeminiClient.Check.Offline -> false to context.getString(R.string.ai_check_offline)
        is GeminiClient.Check.Other -> false to c.message
    }

    Scaffold(
        topBar = {
            SfTopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (connected) stringResource(R.string.ai_status_on) + (if (masked.isNotEmpty()) " · $masked" else " · " + stringResource(R.string.ai_family_key))
                else stringResource(R.string.ai_status_off),
                style = MaterialTheme.typography.titleMedium,
                color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("ai_status"),
            )
            if (privacyMode) Text(stringResource(R.string.ai_privacy_on), color = MaterialTheme.colorScheme.error)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(stringResource(R.string.ai_free_note) + "\n" + stringResource(R.string.ai_paid_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }

            GuideStep(1, stringResource(R.string.ai_step1_title), stringResource(R.string.ai_step1_body), done = connected) {
                OpenSiteButton(stringResource(R.string.ai_get_key), "https://aistudio.google.com/apikey")
                if (connected) OpenSiteButton(stringResource(R.string.ai_usage), "https://aistudio.google.com/usage")
            }
            GuideStep(2, stringResource(R.string.ai_step2_title), stringResource(R.string.ai_step2_body), done = masked.isNotEmpty()) {
                clipKey?.let { ck ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ai_paste_found, ck.take(4) + "…" + ck.takeLast(3)), Modifier.weight(1f))
                            TextButton(onClick = { key = ck; clipKey = null; vm.loadModels(ck) }) { Text(stringResource(R.string.ai_paste)) }
                        }
                    }
                }
                OutlinedTextField(
                    value = key, onValueChange = { key = it.trim() }, label = { Text(stringResource(R.string.ai_key)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().testTag("ai_key"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = key.isNotBlank(), onClick = {
                        vm.saveAi(key, model); key = ""; masked = vm.maskedKey(); vm.loadModels(); vm.testModel(model)
                    }) { Text(stringResource(R.string.ai_save)) }
                    if (masked.isNotEmpty()) OutlinedButton(onClick = { vm.removeAiKey(); masked = "" }) { Text(stringResource(R.string.ai_remove)) }
                }
            }
            GuideStep(3, stringResource(R.string.ai_step3_title), stringResource(R.string.ai_step3_body), done = checks[model] is GeminiClient.Check.Ok) {
                val (ok, text) = checkText(checks[model])
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ai_model) + ": " + (models.firstOrNull { it.id == model }?.name ?: model), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (testing == model) CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                    else OutlinedButton(enabled = connected || key.isNotBlank(), onClick = { vm.testModel(model, key) }, modifier = Modifier.testTag("ai_test")) { Text(stringResource(R.string.ai_test)) }
                }
                TestResult(ok, text)
                if (modelsError != null) TestResult(false, if (modelsError == "offline") stringResource(R.string.ai_check_offline) else modelsError.orEmpty())
                TextButton(onClick = { advanced = !advanced }) {
                    Text(stringResource(if (advanced) R.string.ai_models_hide else R.string.ai_models_show, models.size.toString()))
                }
                if (advanced) {
                    TextButton(onClick = { vm.loadModels(key) }) { Text(stringResource(R.string.ai_models_refresh)) }
                    models.forEach { m ->
                        val (mok, mtext) = checkText(checks[m.id])
                        Column(Modifier.fillMaxWidth().clickable { model = m.id; vm.saveModel(m.id); vm.testModel(m.id, key) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = model == m.id, onClick = { model = m.id; vm.saveModel(m.id); vm.testModel(m.id, key) })
                                Column(Modifier.weight(1f)) {
                                    Text(m.name + if (m.id == GeminiClient.DEFAULT_MODEL) " · " + stringResource(R.string.ai_recommended) else "", style = MaterialTheme.typography.bodyMedium)
                                    Text(m.description.take(110), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (mtext.isNotBlank()) Row { Spacer(Modifier.padding(start = 48.dp)); TestResult(mok, mtext) }
                        }
                    }
                }
            }
            if (viewer?.canManage == true && masked.isNotEmpty()) {
                GuideStep(4, stringResource(R.string.ai_step4_title), stringResource(R.string.ai_step4_body), done = sharing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ai_share_family), Modifier.weight(1f))
                        Switch(checked = sharing, onCheckedChange = { vm.shareKeyWithFamily(viewer.familyId, it) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
