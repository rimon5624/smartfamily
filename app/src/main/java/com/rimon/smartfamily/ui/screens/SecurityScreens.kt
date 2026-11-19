package com.rimon.smartfamily.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.BuildConfig
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.prefs.AppSettings
import com.rimon.smartfamily.ui.AppViewModel

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
    }
}

/** REL-3 "what's new" after an update, and REL-4 a notice when a newer release exists. */
@Composable
fun WhatsNewAndUpdates(vm: AppViewModel, s: AppSettings) {
    val context = LocalContext.current
    val update by vm.update.collectAsState()
    var dismissedUpdate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(s.updateChecks) { if (s.updateChecks) vm.checkUpdate() }
    if (s.lastSeenVersion != BuildConfig.VERSION_NAME) {
        AlertDialog(
            onDismissRequest = { vm.markVersionSeen() },
            title = { Text(stringResource(R.string.whats_new_title, BuildConfig.VERSION_NAME)) },
            text = { Text(stringResource(R.string.whats_new_body), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { vm.markVersionSeen() }) { Text(stringResource(R.string.ok)) } },
        )
        return
    }
    val u = update
    if (u != null && !dismissedUpdate) {
        AlertDialog(
            onDismissRequest = { dismissedUpdate = true },
            title = { Text(stringResource(R.string.update_title, u.version)) },
            text = { Text(stringResource(R.string.update_body) + if (u.notes.isNotBlank()) "\n\n" + u.notes else "", Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    dismissedUpdate = true
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl.ifBlank { u.pageUrl }))) }
                }) { Text(stringResource(R.string.update_download)) }
            },
            dismissButton = { TextButton(onClick = { dismissedUpdate = true }) { Text(stringResource(R.string.update_later)) } },
        )
    }
}
