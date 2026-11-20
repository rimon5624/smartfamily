package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.NotificationsActive
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onCloud: () -> Unit,
    onBackup: () -> Unit,
    onModules: (String) -> Unit,
    onAi: () -> Unit,
    onSmartHome: () -> Unit,
    onNotifyCheck: () -> Unit = {},
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val name by vm.displayName.collectAsStateWithLifecycle()
    val active by vm.activeFamily.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    val settings = s ?: return

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            SfTopAppBar(title = { Text(stringResource(R.string.settings_title)) }, actions = { HelpButton(R.string.help_settings) })
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

            SectionTitle(stringResource(R.string.settings_security))
            InfoRow(
                title = stringResource(R.string.lock_setting),
                subtitle = stringResource(R.string.lock_setting_sub),
                leading = { Icon(Icons.Outlined.Lock, null) },
                trailing = { Switch(checked = settings.appLock, onCheckedChange = { vm.setAppLock(it) }) },
            )
            if (settings.appLock) Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1, 5, 15).forEach { m ->
                    FilterChip(selected = settings.lockMinutes == m, onClick = { vm.setLockMinutes(m) },
                        label = { Text(if (m == 0) stringResource(R.string.lock_now) else stringResource(R.string.lock_after, m)) })
                }
            }
            InfoRow(
                title = stringResource(R.string.secure_setting),
                subtitle = stringResource(R.string.secure_setting_sub),
                leading = { Spacer(Modifier.size(24.dp)) },
                trailing = { Switch(checked = settings.secureScreens, onCheckedChange = { vm.setSecureScreens(it) }) },
            )
            InfoRow(
                title = stringResource(R.string.kid_setting),
                subtitle = stringResource(R.string.kid_setting_sub),
                leading = { Spacer(Modifier.size(24.dp)) },
            )
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO" to R.string.kid_auto, "ON" to R.string.kid_on, "SENIOR" to R.string.kid_senior, "OFF" to R.string.kid_off).forEach { (k, l) ->
                    FilterChip(selected = settings.kidMode == k, onClick = { vm.setKidMode(k) }, label = { Text(stringResource(l)) })
                }
            }
            InfoRow(
                title = stringResource(R.string.update_setting),
                subtitle = stringResource(R.string.update_setting_sub, com.rimon.smartfamily.BuildConfig.VERSION_NAME),
                leading = { Spacer(Modifier.size(24.dp)) },
                trailing = { Switch(checked = settings.updateChecks, onCheckedChange = { vm.setUpdateChecks(it) }) },
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
            ClickRow(
                Icons.Outlined.AutoAwesome,
                stringResource(R.string.ai_title),
                stringResource(if (vm.aiConfigured) R.string.ai_sub_on else R.string.ai_sub_off),
                onAi,
            )
            ClickRow(Icons.Outlined.Sensors, stringResource(R.string.sh_title), stringResource(R.string.sh_hc) + " · " + stringResource(R.string.sh_st), onSmartHome)
            ClickRow(Icons.Outlined.Notifications, stringResource(R.string.nc_title), stringResource(R.string.nc_sub), onNotifyCheck)
            CityRow(settings.zmanimCity, settings.language, settings.favCities.split(',').filter { it.isNotBlank() }, { vm.toggleFavCity(it) }) { vm.setZmanimCity(it) }
            // Home screen cards: size of the greeting and the Shabbat card
            // 2.1: order of the bottom bar and where the chat lives
            androidx.compose.foundation.layout.Box(Modifier.padding(start = 40.dp, top = 8.dp, bottom = 8.dp)) {
                NavSettings(com.rimon.smartfamily.model.NavItems.ordered(settings.navOrder), settings.chatPlacement,
                    onOrder = { vm.setNav(order = it.joinToString(",")) }, onChat = { vm.setNav(chat = it) })
            }
            InfoRow(title = stringResource(R.string.home_cards), subtitle = stringResource(R.string.home_cards_sub), leading = { Spacer(Modifier.size(24.dp)) })
            Text(stringResource(R.string.home_card_greeting), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 40.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("FULL" to R.string.card_full, "COMPACT" to R.string.card_compact, "HIDE" to R.string.card_hide).forEach { (k, l) ->
                    FilterChip(selected = settings.homeGreeting == k, onClick = { vm.setHomeCards(greeting = k) }, label = { Text(stringResource(l)) })
                }
            }
            Text(stringResource(R.string.home_card_shabbat), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 40.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO" to R.string.card_auto, "COMPACT" to R.string.card_compact, "FULL" to R.string.card_full, "HIDE" to R.string.card_hide).forEach { (k, l) ->
                    FilterChip(selected = settings.homeShabbat == k, onClick = { vm.setHomeCards(shabbat = k) }, label = { Text(stringResource(l)) })
                }
            }
            InfoRow(
                title = stringResource(R.string.settings_reminders),
                subtitle = stringResource(R.string.settings_reminders_sub),
                leading = { Icon(Icons.Outlined.Alarm, null) },
                trailing = { Switch(checked = settings.choreReminders, onCheckedChange = { vm.setChoreReminders(it) }) },
            )
            InfoRow(
                title = stringResource(R.string.inv_alerts),
                subtitle = stringResource(R.string.inv_alerts_sub),
                leading = { Icon(Icons.Outlined.NotificationsActive, null) },
                trailing = { Switch(checked = settings.expiryAlerts, onCheckedChange = { vm.setExpiryAlerts(it) }) },
            )

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
private fun CityRow(current: String, language: String, favs: List<String> = emptyList(), onFav: (String) -> Unit = {}, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val city = com.rimon.smartfamily.smart.Cities.byKey(current)
    val he = language != "en"
    ClickRow(Icons.Outlined.LocationCity, stringResource(R.string.shabbat_city), if (he) city.he else city.en) { open = true }
    if (open) CityPickerDialog(he, current, favs, onFav, onDismiss = { open = false }, onPick = { open = false; onPick(it) })
}

/** Search every Israeli locality (Hebrew or English), or take the nearest one from a single location fix. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CityPickerDialog(he: Boolean, current: String, favs: List<String>, onFav: (String) -> Unit, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val results = remember(q) { com.rimon.smartfamily.smart.Cities.search(q) }
    fun locate() {
        locating = true
        scope.launch {
            val loc = com.rimon.smartfamily.smart.OneShotLocation.get(ctx)
            locating = false
            if (loc != null) onPick(com.rimon.smartfamily.smart.Cities.nearest(loc.latitude, loc.longitude).key)
            else android.widget.Toast.makeText(ctx, ctx.getString(R.string.chat_no_location), android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) locate() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shabbat_city)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.city_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) })
                androidx.compose.material3.TextButton(enabled = !locating, onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) locate()
                    else perm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }) {
                    Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.size(6.dp))
                    Text(stringResource(if (locating) R.string.city_locating else R.string.city_use_location))
                }
                // favourites first: one tap to switch when travelling
                if (favs.isNotEmpty()) {
                    Text(stringResource(R.string.city_favs), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        favs.forEach { k ->
                            val c = com.rimon.smartfamily.smart.Cities.byKey(k)
                            androidx.compose.material3.FilterChip(selected = k == current, onClick = { onPick(k) }, label = { Text(if (he) c.he else c.en) },
                                leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(16.dp)) })
                        }
                    }
                }
                Text(stringResource(R.string.city_count, results.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(results.size, key = { results[it].key }) { i ->
                        val c = results[i]
                        Row(Modifier.fillMaxWidth().clickable { onPick(c.key) }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 4.dp)) {
                                Text((if (c.key == current) "✓ " else "") + (if (he) c.he else c.en), style = MaterialTheme.typography.bodyLarge)
                                Text(if (he) c.en else c.he, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onFav(c.key) }) {
                                Icon(if (c.key in favs) Icons.Outlined.Star else Icons.Outlined.StarBorder, stringResource(R.string.city_fav_toggle),
                                    tint = if (c.key in favs) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (results.isEmpty()) item { Text(stringResource(R.string.city_none), modifier = Modifier.padding(8.dp)) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
            SfTopAppBar(
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
        Spacer(Modifier.height(8.dp))
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
            // 2.2: a new account needs a strong password (free check on the phone; Supabase's leak check is [PAID])
            val weak = com.rimon.smartfamily.security.PasswordRules.problem(password, email)
            if (password.isNotEmpty() && weak != null) Text(stringResource(weak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.signIn(email, password) }, enabled = ready) { Text(stringResource(R.string.cloud_sign_in)) }
                OutlinedButton(onClick = { vm.signUp(email, password) }, enabled = ready && weak == null) { Text(stringResource(R.string.cloud_sign_up)) }
            }
        }
        if (settings.lastSyncError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cloud_last_error, settings.lastSyncError), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        // The project address and public key are built into the app. Only family owners/admins can open them,
        // and only under "Advanced", so nobody changes them by mistake.
        val canSee by vm.canSeeCloudDetails.collectAsStateWithLifecycle(initialValue = false)
        if (canSee) {
            var advanced by remember { mutableStateOf(false) }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            TextButton(onClick = { advanced = !advanced }) { Text(stringResource(if (advanced) R.string.cloud_advanced_hide else R.string.cloud_advanced)) }
            if (advanced) {
                Text(stringResource(R.string.cloud_advanced_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                // 2.2 wizard for a family with its own cloud project
                val ref = Regex("https://([a-z0-9]+)\\.supabase\\.co").find(url)?.groupValues?.get(1)
                com.rimon.smartfamily.ui.components.GuideStep(1, stringResource(R.string.cw_step1), stringResource(R.string.cw_step1_body)) {
                    com.rimon.smartfamily.ui.components.OpenSiteButton(stringResource(R.string.cw_dashboard),
                        if (ref != null) "https://supabase.com/dashboard/project/$ref/settings/api" else "https://supabase.com/dashboard/projects")
                }
                com.rimon.smartfamily.ui.components.GuideStep(2, stringResource(R.string.cw_step2), stringResource(R.string.cw_step2_body)) {}
                Spacer(Modifier.height(8.dp))
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
                val cloudTest by vm.cloudTest.collectAsStateWithLifecycle()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.testCloud(url, key) }, enabled = url.isNotBlank() && key.isNotBlank()) { Text(stringResource(R.string.ai_test)) }
                    Button(onClick = { vm.saveConnection(url, key) }, enabled = url.isNotBlank() && key.isNotBlank() && !busy) {
                        Text(stringResource(R.string.cloud_save))
                    }
                }
                cloudTest?.let { com.rimon.smartfamily.ui.components.TestResult(if (it == "…") null else it == "ok", stringResource(when (it) {
                    "…" -> R.string.nc_sending; "ok" -> R.string.cw_ok; "offline" -> R.string.ai_check_offline; "key" -> R.string.cw_bad_key; else -> R.string.cw_bad_url })) }
                Spacer(Modifier.height(8.dp))
                if (ref != null) com.rimon.smartfamily.ui.components.OpenSiteButton(stringResource(R.string.cw_firebase), "https://console.firebase.google.com/")
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
        // 2.2 wizard: password → where to save → the app re-opens the file to prove it can be restored
        val check by vm.backupCheck.collectAsStateWithLifecycle()
        com.rimon.smartfamily.ui.components.GuideStep(1, stringResource(R.string.bw_step1), stringResource(R.string.bw_step1_body), done = password.length >= 8 && password == confirm) {}
        com.rimon.smartfamily.ui.components.GuideStep(2, stringResource(R.string.bw_step2), stringResource(R.string.bw_step2_body), done = check == "ok") {
            check?.let { com.rimon.smartfamily.ui.components.TestResult(if (it == "…") null else it == "ok", stringResource(when (it) { "…" -> R.string.bw_checking; "ok" -> R.string.backup_verified; else -> R.string.backup_verify_failed })) }
        }
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

        // 2.0: weekly automatic backup to Downloads/SmartFamily, with the password above
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var autoOn by remember { mutableStateOf(com.rimon.smartfamily.backup.AutoBackupWorker.isOn(ctx)) }
        val last = remember(autoOn) { com.rimon.smartfamily.backup.AutoBackupWorker.last(ctx) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_backup), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (last > 0) stringResource(R.string.auto_backup_last, java.text.DateFormat.getDateInstance().format(java.util.Date(last)))
                    else stringResource(R.string.auto_backup_sub),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            androidx.compose.material3.Switch(
                checked = autoOn,
                enabled = autoOn || (password.length >= 8 && password == confirm),
                onCheckedChange = { on ->
                    if (on) com.rimon.smartfamily.backup.AutoBackupWorker.enable(ctx, password) else com.rimon.smartfamily.backup.AutoBackupWorker.disable(ctx)
                    autoOn = on
                },
            )
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
