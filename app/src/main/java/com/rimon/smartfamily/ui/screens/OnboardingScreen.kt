package com.rimon.smartfamily.ui.screens

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
    // rememberSaveable keeps the step across the activity restart caused by a language change.
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    val defaultFamily = stringResource(R.string.onb_family_default)
    var familyName by rememberSaveable { mutableStateOf("") }
    // Stored as a comma-separated string: a Set cannot be saved in the instance state bundle.
    var chosenCsv by rememberSaveable {
        mutableStateOf(AppModule.entries.filter { it.defaultOn }.joinToString(",") { it.key })
    }
    val chosen: Set<String> = chosenCsv.split(',').filter { it.isNotBlank() }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp)
    ) {
        LinearProgressIndicator(
            progress = { (step + 1f) / STEPS },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
        )
        Text(
            stringResource(R.string.onb_step, step + 1, STEPS),
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
            when (s) {
                0 -> LanguageStep(onPick = { vm.chooseLanguage(it); step = 1 })
                1 -> TextStep(
                    title = stringResource(R.string.onb_name_title),
                    body = null,
                    hint = stringResource(R.string.onb_name_hint),
                    value = name,
                    onValue = { name = it },
                )
                2 -> TextStep(
                    title = stringResource(R.string.onb_family_title),
                    body = stringResource(R.string.onb_family_body),
                    hint = stringResource(R.string.onb_family_hint),
                    value = familyName,
                    onValue = { familyName = it },
                )
                else -> ModulesStep(chosen) { key, on -> chosenCsv = (if (on) chosen + key else chosen - key).joinToString(",") }
            }
        }

        if (step > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) }
                val canGo = when (step) {
                    1 -> name.isNotBlank()
                    2 -> familyName.isNotBlank()
                    else -> true
                }
                Button(
                    enabled = canGo,
                    onClick = {
                        if (step < STEPS - 1) {
                            if (step == 1 && familyName.isEmpty()) familyName = defaultFamily
                            step++
                        }
                        else vm.finishOnboarding(
                            name,
                            familyName,
                            chosen.mapNotNull { AppModule.fromKey(it) }.toSet(),
                        )
                    },
                ) { Text(stringResource(if (step < STEPS - 1) R.string.next else R.string.onb_finish)) }
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

@Composable
private fun ModulesStep(chosen: Set<String>, onToggle: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onb_modules_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_modules_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppModule.entries) { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    onClick = { onToggle(m.key, m.key !in chosen) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ModuleBadge(m, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(m.title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(m.subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = m.key in chosen, onCheckedChange = { onToggle(m.key, it) })
                    }
                }
            }
        }
    }
}
