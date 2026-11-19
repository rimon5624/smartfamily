package com.rimon.smartfamily.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.BuildConfig
import com.rimon.smartfamily.R
import com.rimon.smartfamily.backup.BackupManager
import com.rimon.smartfamily.backup.BackupPayload
import com.rimon.smartfamily.data.prefs.ThemeMode
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.InfoRow
import com.rimon.smartfamily.ui.components.SectionTitle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onCloud: () -> Unit,
    onBackup: () -> Unit,
    onModules: (String) -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val name by vm.displayName.collectAsStateWithLifecycle()
    val active by vm.activeFamily.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    val settings = s ?: return

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, actions = { HelpButton(R.string.help_settings) })
        },
    ) { inner ->
        Column(
            Modifier
                .padding(top = inner.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionTitle(stringResource(R.string.settings_profile))
            ClickRow(Icons.Outlined.Person, stringResource(R.string.settings_profile_name), name) { editingName = true }

            SectionTitle(stringResource(R.string.settings_appearance))
            InfoRow(
                title = stringResource(R.string.settings_language),
                leading = { Icon(Icons.Outlined.Translate, null) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = settings.language == "he", onClick = { vm.chooseLanguage("he") },
                            label = { Text(stringResource(R.string.lang_he)) })
                        FilterChip(selected = settings.language == "en", onClick = { vm.chooseLanguage("en") },
                            label = { Text(stringResource(R.string.lang_en)) })
                    }
                },
            )
            InfoRow(
                title = stringResource(R.string.settings_theme),
                leading = { Icon(Icons.Outlined.Palette, null) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 38.dp)) {
                listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                ).forEach { (mode, label) ->
                    FilterChip(selected = settings.themeMode == mode, onClick = { vm.setTheme(mode) }, label = { Text(stringResource(label)) })
                }
            }
            InfoRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_sub),
                leading = { Spacer(Modifier.size(24.dp)) },
                trailing = { Switch(checked = settings.dynamicColor, onCheckedChange = { vm.setDynamicColor(it) }) },
            )

            SectionTitle(stringResource(R.string.settings_data))
            InfoRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_sub),
                leading = { Icon(Icons.Outlined.Lock, null) },
                trailing = { Switch(checked = settings.privacyMode, onCheckedChange = { vm.setPrivacy(it) }) },
            )
            active?.let { fam ->
                ClickRow(Icons.Outlined.Tune, stringResource(R.string.modules_title), stringResource(R.string.settings_modules_sub)) {
                    onModules(fam.id)
                }
            }
            ClickRow(
                Icons.Outlined.Cloud,
                stringResource(R.string.settings_cloud),
                if (vm.isSignedIn && settings.accountEmail.isNotBlank()) stringResource(R.string.settings_cloud_sub_on, settings.accountEmail)
                else stringResource(R.string.settings_cloud_sub_off),
                onCloud,
            )
            ClickRow(Icons.Outlined.Backup, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_sub), onBackup)

            SectionTitle(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (editingName) {
        TextInputDialog(
            title = stringResource(R.string.settings_profile_name),
            label = stringResource(R.string.onb_name_hint),
            initial = name,
            onDismiss = { editingName = false },
            onConfirm = { vm.setDisplayName(it); editingName = false },
        )
    }
}

@Composable
private fun ClickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp)) {
            InfoRow(
                title = title,
                subtitle = subtitle,
                leading = { Icon(icon, null) },
                trailing = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, busy: Boolean, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(top = inner.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
fun CloudScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings = s ?: return
    var url by remember(settings.supabaseUrl) { mutableStateOf(settings.supabaseUrl) }
    var key by remember(settings.supabaseAnonKey) { mutableStateOf(settings.supabaseAnonKey) }
    var email by remember { mutableStateOf(settings.accountEmail) }
    var password by remember { mutableStateOf("") }
    val signedIn = vm.isSignedIn && settings.accountEmail.isNotBlank()

    SubScreen(stringResource(R.string.cloud_title), onBack, busy) {
        Text(stringResource(R.string.cloud_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.cloud_url)) },
            placeholder = { Text("https://xxxx.supabase.co") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.cloud_key)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.saveConnection(url, key) }, enabled = url.isNotBlank() && key.isNotBlank() && !busy) {
            Text(stringResource(R.string.cloud_save))
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        SectionTitle(stringResource(R.string.cloud_account))
        if (signedIn) {
            Text(stringResource(R.string.cloud_signed_in_as, settings.accountEmail), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.cloud_last_sync,
                    if (settings.lastSyncAt == 0L) stringResource(R.string.cloud_never)
                    else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(settings.lastSyncAt)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.syncNow() }, enabled = !busy) { Text(stringResource(R.string.cloud_sync_now)) }
                OutlinedButton(onClick = { vm.signOut() }, enabled = !busy) { Text(stringResource(R.string.cloud_sign_out)) }
            }
        } else if (!settings.supabaseConfigured) {
            Text(stringResource(R.string.cloud_connect_first), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.cloud_email)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.cloud_password)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(12.dp))
            val ready = email.contains('@') && password.length >= 6 && !busy
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.signIn(email, password) }, enabled = ready) { Text(stringResource(R.string.cloud_sign_in)) }
                OutlinedButton(onClick = { vm.signUp(email, password) }, enabled = ready) { Text(stringResource(R.string.cloud_sign_up)) }
            }
        }
    }
}

@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<BackupPayload?>(null) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri: Uri? ->
        if (uri != null) vm.exportBackup(uri, password)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.readBackup(uri, restorePassword) { pendingRestore = it }
    }

    SubScreen(stringResource(R.string.backup_title), onBack, busy) {
        Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.backup_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)

        SectionTitle(stringResource(R.string.backup_export))
        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.backup_password)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            supportingText = { Text(stringResource(R.string.backup_password_short)) },
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it }, label = { Text(stringResource(R.string.backup_password_confirm)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
            isError = confirm.isNotEmpty() && confirm != password,
            supportingText = { if (confirm.isNotEmpty() && confirm != password) Text(stringResource(R.string.backup_password_mismatch)) },
        )
        Button(
            onClick = { createLauncher.launch(BackupManager.suggestedName()) },
            enabled = password.length >= 8 && password == confirm && !busy,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(Icons.Outlined.Backup, null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.backup_export))
        }

        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        SectionTitle(stringResource(R.string.backup_restore))
        OutlinedTextField(
            value = restorePassword, onValueChange = { restorePassword = it },
            label = { Text(stringResource(R.string.backup_restore_password)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedButton(
            onClick = { openLauncher.launch(arrayOf("*/*")) },
            enabled = restorePassword.isNotEmpty() && !busy,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(stringResource(R.string.backup_restore)) }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.backup_working))
            }
        }
    }

    pendingRestore?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(stringResource(R.string.backup_restore_confirm_body, payload.families.count { it.deletedAt == null }))
            },
            confirmButton = {
                TextButton(onClick = { vm.restoreBackup(payload); pendingRestore = null }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
