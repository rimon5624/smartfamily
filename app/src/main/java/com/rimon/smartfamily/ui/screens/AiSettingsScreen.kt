package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.smart.GeminiClient
import com.rimon.smartfamily.ui.ShoppingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(vm: ShoppingViewModel, currentModel: String, privacyMode: Boolean, onBack: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(currentModel.ifBlank { GeminiClient.DEFAULT_MODEL }) }
    var masked by remember { mutableStateOf(vm.maskedKey()) }

    Scaffold(
        topBar = {
            SfTopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(R.string.ai_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(stringResource(R.string.ai_free_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(stringResource(R.string.ai_paid_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (privacyMode) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ai_privacy_on), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            if (masked.isNotEmpty()) Text(stringResource(R.string.ai_sub_on) + " · " + masked, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.ai_key)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.ai_model)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = key.isNotBlank() || masked.isNotEmpty(),
                    onClick = {
                        vm.saveAi(if (key.isNotBlank()) key else vm.currentKeyOrEmpty(), model)
                        key = ""
                        masked = vm.maskedKey()
                    },
                ) { Text(stringResource(R.string.ai_save)) }
                if (masked.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.removeAiKey(); masked = "" }) { Text(stringResource(R.string.ai_remove)) }
                }
            }
        }
    }
}
