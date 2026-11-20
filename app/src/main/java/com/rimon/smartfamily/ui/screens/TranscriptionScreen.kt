package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.smart.LocalWhisper
import com.rimon.smartfamily.smart.WhisperModels
import com.rimon.smartfamily.ui.components.SfTopAppBar
import kotlinx.coroutines.delay

/** 2.2: how voice messages are transcribed — cloud, offline on this phone (whisper, like "הרימון המתמלל"), auto, or off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(hebrew: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(WhisperModels.mode(context)) }
    var wifiOnly by remember { mutableStateOf(WhisperModels.wifiOnly(context)) }
    var selected by remember { mutableStateOf(WhisperModels.selectedId(context)) }
    var tick by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    val unsupported = remember { LocalWhisper.unsupported() }

    Scaffold(topBar = {
        SfTopAppBar(title = { Text(stringResource(R.string.tr_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } })
    }) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.tr_intro), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to R.string.tr_auto, "offline" to R.string.tr_offline, "cloud" to R.string.tr_cloud, "off" to R.string.tr_off).forEach { (k, l) ->
                    FilterChip(selected = mode == k, onClick = { mode = k; WhisperModels.setMode(context, k) }, label = { Text(stringResource(l)) })
                }
            }
            Text(stringResource(when (mode) { "auto" -> R.string.tr_auto_body; "offline" -> R.string.tr_offline_body; "cloud" -> R.string.tr_cloud_body; else -> R.string.tr_off_body }),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (unsupported != null) Text(stringResource(R.string.tr_unsupported), color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tr_wifi_only), Modifier.weight(1f))
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; WhisperModels.setWifiOnly(context, it) })
            }
            problem?.let { Text(stringResource(if (it == "space") R.string.tr_no_space else R.string.tr_dl_failed), color = MaterialTheme.colorScheme.error) }
            WhisperModels.all.forEach { m ->
                val ready = tick >= 0 && WhisperModels.isReady(context, m)
                val prog = if (tick >= 0) WhisperModels.progress(context, m) else null
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = ready && (selected == m.id || (selected.isBlank() && WhisperModels.selected(context)?.id == m.id)), enabled = ready,
                                onClick = { selected = m.id; WhisperModels.select(context, m.id) })
                            Column(Modifier.weight(1f)) {
                                Text((if (hebrew) m.he else m.en) + " · " + m.sizeText, style = MaterialTheme.typography.titleSmall)
                                Text(if (hebrew) m.heDesc else m.enDesc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        when {
                            ready -> OutlinedButton(onClick = { WhisperModels.delete(context, m); tick++ }) { Text(stringResource(R.string.tr_delete)) }
                            prog != null && prog >= 0 -> {
                                LinearProgressIndicator(progress = { prog / 100f }, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$prog%", Modifier.weight(1f))
                                    OutlinedButton(onClick = { WhisperModels.cancel(context, m); tick++ }) { Text(stringResource(R.string.cancel)) }
                                }
                            }
                            else -> Button(enabled = unsupported == null, onClick = { problem = WhisperModels.download(context, m); tick++ }) {
                                Text(stringResource(R.string.tr_download, m.sizeText))
                            }
                        }
                        if (prog == -1) Text(stringResource(R.string.tr_dl_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
