package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import com.rimon.smartfamily.data.db.FamilyEntity
import com.rimon.smartfamily.data.db.FamilyShareEntity
import com.rimon.smartfamily.data.repo.ShareItem
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.ui.UiMessage
import com.rimon.smartfamily.ui.components.HelpButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartFamilyApp).container
    private val repo = c.shares
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    val items = MutableStateFlow<List<ShareItem>>(emptyList())
    val targetName = MutableStateFlow<String?>(null)
    val shareable get() = repo.shareable

    private fun launchSafe(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) {
            _messages.tryEmit(if (e.message == "family not found") UiMessage.Res(R.string.share_not_found) else UiMessage.Text(e.message ?: e.javaClass.simpleName))
        }
    }

    fun family(id: String) = c.families.observeFamily(id)
    fun shares(familyId: String) = repo.involving(familyId)
    fun loadItems(familyId: String, m: AppModule) = launchSafe { items.value = repo.items(familyId, m) }
    fun lookup(code: String) = launchSafe { targetName.value = if (code.isBlank()) null else repo.familyName(code) ?: "" }
    fun offer(f: FamilyEntity, code: String, m: AppModule, item: ShareItem?, write: Boolean) = launchSafe {
        repo.offer(f, code, m, item, write, c.settings.current().userId)
        _messages.tryEmit(UiMessage.Res(R.string.share_sent))
    }
    fun decide(s: FamilyShareEntity, accept: Boolean) = launchSafe { repo.decide(s, accept); c.sync.syncNow() }
    fun revoke(s: FamilyShareEntity) = launchSafe { repo.revoke(s) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(vm: ShareViewModel, familyId: String, isAdmin: Boolean, onBack: () -> Unit) {
    val family by remember(familyId) { vm.family(familyId) }.collectAsState(initial = null)
    val shares by remember(familyId) { vm.shares(familyId) }.collectAsState(initial = emptyList())
    val clipboard = LocalClipboardManager.current
    var creating by remember { mutableStateOf(false) }
    val incoming = shares.filter { it.targetFamilyId == familyId }
    val outgoing = shares.filter { it.familyId == familyId }
    Scaffold(
        topBar = {
            SfTopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_sharing) },
            )
        },
        floatingActionButton = { if (isAdmin) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.share_new)) }) },
    ) { inner ->
        LazyColumn(Modifier.padding(inner), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.share_my_code), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.share_my_code_body), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(familyId, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { clipboard.setText(AnnotatedString(familyId)) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.chat_copy)) }
                        }
                    }
                }
            }
            if (incoming.isNotEmpty()) item { Text(stringResource(R.string.share_incoming), style = MaterialTheme.typography.titleMedium) }
            items(incoming, key = { "i" + it.id }) { s ->
                ShareCard(s, from = true) {
                    if (s.status == "PENDING" && isAdmin) Row {
                        Button(onClick = { vm.decide(s, true) }) { Text(stringResource(R.string.share_accept)) }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = { vm.decide(s, false) }) { Text(stringResource(R.string.share_decline)) }
                    }
                    if (s.status == "ACCEPTED" && isAdmin) TextButton(onClick = { vm.decide(s, false) }) { Text(stringResource(R.string.share_stop)) }
                }
            }
            if (outgoing.isNotEmpty()) item { Text(stringResource(R.string.share_outgoing), style = MaterialTheme.typography.titleMedium) }
            items(outgoing, key = { "o" + it.id }) { s ->
                ShareCard(s, from = false) {
                    if (isAdmin) TextButton(onClick = { vm.revoke(s) }) { Text(stringResource(R.string.share_stop)) }
                }
            }
            if (shares.isEmpty()) item { Text(stringResource(R.string.share_none)) }
        }
    }
    val f = family
    if (creating && f != null) NewShareDialog(vm, f, onDismiss = { creating = false })
}

@Composable
private fun ShareCard(s: FamilyShareEntity, from: Boolean, actions: @Composable () -> Unit) {
    val module = AppModule.entries.firstOrNull { it.key == s.module }
    Card(
        Modifier.fillMaxWidth(),
        colors = if (s.status == "PENDING") CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text((if (from) s.sourceName else s.targetName).ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
            Text(
                (module?.let { stringResource(it.title) } ?: s.module) + (if (s.itemTitle.isNotBlank()) " · " + s.itemTitle else " · " + stringResource(R.string.share_whole)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(if (s.canWrite) R.string.share_can_edit else R.string.share_read_only) + " · " + stringResource(
                    when (s.status) { "ACCEPTED" -> R.string.share_status_accepted; "DECLINED" -> R.string.share_status_declined; else -> R.string.share_status_pending }
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            actions()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewShareDialog(vm: ShareViewModel, f: FamilyEntity, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var module by remember { mutableStateOf<AppModule?>(null) }
    var item by remember { mutableStateOf<ShareItem?>(null) }
    var write by rememberSaveable { mutableStateOf(false) }
    val items by vm.items.collectAsState()
    val name by vm.targetName.collectAsState()
    LaunchedEffect(Unit) { vm.targetName.value = null; vm.items.value = emptyList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_new)) },
        text = {
            LazyColumn {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(code, { code = it.trim() }, label = { Text(stringResource(R.string.share_their_code)) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.lookup(code) }) { Text(stringResource(R.string.share_check)) }
                    }
                    name?.let { Text(if (it.isBlank()) stringResource(R.string.share_not_found) else "✓ $it", color = if (it.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.share_what), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.shareable.forEach { m ->
                            FilterChip(module == m, { module = m; item = null; vm.loadItems(f.id, m) }, { Text(stringResource(m.title)) })
                        }
                    }
                }
                if (module != null && items.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.share_which_item), style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth().clickable { item = null }.padding(6.dp)) { Text((if (item == null) "● " else "○ ") + stringResource(R.string.share_whole)) }
                    }
                    items(items, key = { it.id }) { i ->
                        Row(Modifier.fillMaxWidth().clickable { item = i }.padding(6.dp)) { Text((if (item?.id == i.id) "● " else "○ ") + i.title) }
                    }
                }
                item {
                    if (module != AppModule.CHAT) Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(write, { write = it }); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.share_allow_edit))
                    } else Text(stringResource(R.string.share_chat_note), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = code.isNotBlank() && module != null && name?.isNotBlank() == true, onClick = {
                vm.offer(f, code, module!!, item, write); onDismiss()
            }) { Text(stringResource(R.string.share_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
