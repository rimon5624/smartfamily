package com.rimon.smartfamily.ui.screens

import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.ModuleBadge

private const val STEPS = 4

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    // Steps: 0 language · 1 name · 2 new family or join · then 3/4 = family name + areas, or 3 = join.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var joining by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val defaultFamily = stringResource(R.string.onb_family_default)
    var familyName by rememberSaveable { mutableStateOf("") }
    // areas chosen at the start (2.1.2: by area, with the same names the app uses)
    var areasCsv by rememberSaveable { mutableStateOf("food,calendar,tasks,family") }
    val areas = areasCsv.split(',').filter { it.isNotBlank() }.toSet()
    val total = if (joining) 4 else 5

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / total },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
        )
        Text(
            stringResource(R.string.onb_step, step + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { s ->
            when {
                s == 0 -> LanguageStep(onPick = { vm.chooseLanguage(it); step = 1 })
                s == 1 -> TextStep(
                    title = stringResource(R.string.onb_name_title),
                    body = null,
                    hint = stringResource(R.string.onb_name_hint),
                    value = name,
                    onValue = { name = it },
                )
                s == 2 -> ChoiceStep(
                    onNew = { joining = false; if (familyName.isEmpty()) familyName = defaultFamily; step = 3 },
                    onJoin = { joining = true; step = 3 },
                )
                joining -> JoinStep(vm, name)
                s == 3 -> TextStep(
                    title = stringResource(R.string.onb_family_title),
                    body = stringResource(R.string.onb_family_body),
                    hint = stringResource(R.string.onb_family_hint),
                    value = familyName,
                    onValue = { familyName = it },
                )
                else -> AreasStep(areas) { key, on -> areasCsv = (if (on) areas + key else areas - key).joinToString(",") }
            }
        }

        if (step > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
                // the choice step and the join step have their own buttons
                if (step != 2 && !(joining && step == 3)) {
                    val canGo = when (step) {
                        1 -> name.isNotBlank()
                        3 -> familyName.isNotBlank()
                        else -> true
                    }
                    val last = step == 4
                    Button(
                        enabled = canGo,
                        onClick = {
                            if (!last) step++
                            else vm.finishOnboarding(
                                name, familyName,
                                areas.mapNotNull { com.rimon.smartfamily.model.Hub.byKey(it) }.flatMap { it.features }.toSet(),
                            )
                        },
                    ) { Text(stringResource(if (!last) R.string.next else R.string.onb_finish)) }
                }
            }
        }
    }
}

/** New family, or join the family someone already created (the usual case for the second parent). */
@Composable
private fun ChoiceStep(onNew: () -> Unit, onJoin: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onb_choice_title), style = MaterialTheme.typography.headlineMedium)
        Card(onClick = onJoin, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(stringResource(R.string.onb_join_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onb_join_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(onClick = onNew, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(stringResource(R.string.onb_new_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onb_new_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Join: sign in (same account on all your phones, or a new one), then the invite code or its QR. */
@Composable
private fun JoinStep(vm: AppViewModel, name: String) {
    val signedIn = vm.isSignedIn
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val nowSignedIn = remember(refresh, busy) { vm.isSignedIn }
    androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.onb_join_title), style = MaterialTheme.typography.headlineMedium) }
        if (!(signedIn || nowSignedIn)) {
            item { Text(stringResource(R.string.onb_join_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                androidx.compose.material3.OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.cloud_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                androidx.compose.material3.OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.cloud_password)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            item {
                val ready = email.contains('@') && password.length >= 6 && !busy
                val weak = com.rimon.smartfamily.security.PasswordRules.problem(password, email)
                if (password.isNotEmpty() && weak != null) Text(stringResource(weak) + " " + stringResource(R.string.pw_for_new), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = ready, onClick = { vm.setDisplayName(name); vm.signIn(email, password); refresh++ }) { Text(stringResource(R.string.cloud_sign_in)) }
                    androidx.compose.material3.OutlinedButton(enabled = ready && weak == null, onClick = { vm.setDisplayName(name); vm.signUp(email, password); refresh++ }) { Text(stringResource(R.string.cloud_sign_up)) }
                }
            }
        } else {
            item { Text(stringResource(R.string.onb_join_code), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                androidx.compose.material3.OutlinedTextField(code, { code = it.uppercase().trim() }, label = { Text(stringResource(R.string.join_code_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = code.length >= 4 && !busy, onClick = { vm.joinFromOnboarding(name, code) }) { Text(stringResource(R.string.join_send)) }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        scope.launch {
                            val raw = runCatching { com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan().await().rawValue }.getOrNull()
                            raw?.removePrefix("smartfamily:join:")?.trim()?.takeIf { it.isNotBlank() }?.let { code = it; vm.joinFromOnboarding(name, it) }
                        }
                    }) { Text(stringResource(R.string.join_scan)) }
                }
            }
        }
        item { Text(stringResource(R.string.onb_join_after), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    LaunchedEffect(busy) { if (!busy) refresh++ }
}

/** The areas to start with; everything can be changed later in Settings → Features. */
@Composable
private fun AreasStep(chosen: Set<String>, onToggle: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onb_modules_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_modules_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(com.rimon.smartfamily.model.Hub.entries.size) { i ->
                val h = com.rimon.smartfamily.model.Hub.entries[i]
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), onClick = { onToggle(h.key, h.key !in chosen) }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(navIcon(h.key), null)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(h.title), style = MaterialTheme.typography.titleSmall)
                            Text(h.sections.map { stringResource(it.title) }.distinct().joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = h.key in chosen, onCheckedChange = { onToggle(h.key, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageStep(onPick: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Home, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.onb_choose_language), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onPick("he") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.lang_he), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onPick("en") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.lang_en), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TextStep(title: String, body: String?, hint: String, value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(hint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* the old per-module step was replaced by AreasStep (2.1.2) */
