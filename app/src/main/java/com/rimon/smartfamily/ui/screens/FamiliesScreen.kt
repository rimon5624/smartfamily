package com.rimon.smartfamily.ui.screens

import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.QrCode2
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.InviteEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.model.Role
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.components.Avatar
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.InfoRow
import com.rimon.smartfamily.ui.components.SectionTitle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamiliesScreen(vm: AppViewModel, padding: PaddingValues, onOpen: (String) -> Unit) {
    val families by vm.families.collectAsStateWithLifecycle()
    val active by vm.activeFamily.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.families_title)) },
                actions = { HelpButton(R.string.help_families) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.families_new)) },
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(families, key = { it.id }) { f ->
                Card(
                    onClick = { onOpen(f.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(f.name, 48.dp)
                        Spacer(Modifier.size(14.dp))
                        Text(f.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (active?.id == f.id) {
                            AssistChip(onClick = {}, label = { Text(stringResource(R.string.families_active)) })
                        } else {
                            TextButton(onClick = { vm.setActiveFamily(f.id) }) { Text(stringResource(R.string.families_make_active)) }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { joining = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Outlined.GroupAdd, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.families_join))
                }
                // 2.0: join by scanning the QR shown on the other phone
                val scanCtx = LocalContext.current
                val scanScope = androidx.compose.runtime.rememberCoroutineScope()
                OutlinedButton(onClick = {
                    scanScope.launch {
                        val raw = runCatching { com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(scanCtx).startScan().await().rawValue }.getOrNull()
                        val code = raw?.removePrefix("smartfamily:join:")?.trim()
                        if (!code.isNullOrBlank() && vm.isSignedIn) vm.join(code) {} else if (!code.isNullOrBlank()) joining = true
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.join_scan))
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.families_new),
            label = stringResource(R.string.family_name_hint),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { vm.createFamily(it); creating = false },
        )
    }
    if (joining) {
        val signedIn = vm.isSignedIn
        if (!signedIn) {
            AlertDialog(
                onDismissRequest = { joining = false },
                confirmButton = { TextButton(onClick = { joining = false }) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.join_title)) },
                text = { Text(stringResource(R.string.family_needs_cloud)) },
            )
        } else {
            TextInputDialog(
                title = stringResource(R.string.join_title),
                label = stringResource(R.string.join_code_hint),
                initial = "",
                confirmText = stringResource(R.string.join_send),
                onDismiss = { joining = false },
                onConfirm = { code -> vm.join(code) { joining = false } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDetailScreen(vm: AppViewModel, familyId: String, onBack: () -> Unit, onOpenModules: (String) -> Unit, onOpenSharing: (String) -> Unit = {}) {
    val family by remember(familyId) { vm.family(familyId) }.collectAsState(initial = null)
    val members by remember(familyId) { vm.members(familyId) }.collectAsState(initial = emptyList())
    val invites by remember(familyId) { vm.invites(familyId) }.collectAsState(initial = emptyList())
    val settings by vm.settings.collectAsStateWithLifecycle()
    val uid = settings?.userId.orEmpty()
    val me = members.firstOrNull { it.userId == uid }
    val canManage = me?.let { Role.parse(it.role).canManage } == true
    val isOwner = me?.role == Role.OWNER.name

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingChild by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val f = family

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(f?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (canManage) IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.family_rename)) }
                    if (isOwner) IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.family_delete)) }
                    HelpButton(R.string.help_families)
                },
            )
        },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 32.dp)) {
            item {
                OutlinedButton(onClick = { onOpenModules(familyId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Tune, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.family_modules))
                }
                OutlinedButton(onClick = { onOpenSharing(familyId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.share_title))
                }
            }
            if (f != null) {
                item {
                    SectionTitle(stringResource(R.string.family_kashrut))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "kosher" to R.string.kashrut_kosher,
                            "mehadrin" to R.string.kashrut_mehadrin,
                            "none" to R.string.kashrut_none,
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = f.kashrut == value,
                                enabled = canManage,
                                onClick = { vm.setKashrut(familyId, value) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.family_members)) }
            items(members, key = { it.id }) { m ->
                MemberRow(m, isMe = m.userId == uid, canManage = canManage, vm = vm)
            }
            if (canManage) {
                item {
                    TextButton(onClick = { addingChild = true }) {
                        Icon(Icons.Outlined.ChildCare, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.family_add_child))
                    }
                }
                item { SectionTitle(stringResource(R.string.family_invites)) }
                if (!vm.isSignedIn) {
                    item {
                        Text(stringResource(R.string.family_needs_cloud), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    if (invites.isEmpty()) {
                        item { Text(stringResource(R.string.family_no_invites), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(invites, key = { it.code }) { inv ->
                        InviteRow(
                            inv,
                            onCopy = { clipboard.setText(AnnotatedString(inv.code)) },
                            onShare = { shareInvite(context, f?.name.orEmpty(), inv) },
                            onRevoke = { vm.revokeInvite(inv) },
                        )
                    }
                    item {
                        Button(
                            onClick = { vm.createInvite(familyId) { shareInvite(context, f?.name.orEmpty(), it) } },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Icon(Icons.Outlined.GroupAdd, null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.family_invite_new))
                        }
                    }
                }
            }
        }
    }

    if (renaming && f != null) {
        TextInputDialog(
            title = stringResource(R.string.family_rename),
            label = stringResource(R.string.family_name_hint),
            initial = f.name,
            onDismiss = { renaming = false },
            onConfirm = { vm.renameFamily(familyId, it); renaming = false },
        )
    }
    if (addingChild) {
        TextInputDialog(
            title = stringResource(R.string.family_add_child),
            label = stringResource(R.string.family_child_name),
            initial = "",
            onDismiss = { addingChild = false },
            onConfirm = { vm.addChild(familyId, it); addingChild = false },
        )
    }
    if (deleting && f != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.family_delete)) },
            text = { Text(stringResource(R.string.family_delete_confirm, f.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = false; vm.deleteFamily(familyId); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MemberRow(m: MemberEntity, isMe: Boolean, canManage: Boolean, vm: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    val role = Role.parse(m.role)
    val subtitle = buildString {
        append(stringResource(role.title))
        if (isMe) append(" · ").append(stringResource(R.string.family_you))
        if (m.status == "PENDING") append(" · ").append(stringResource(R.string.family_pending))
    }
    InfoRow(
        title = m.displayName.ifBlank { "—" },
        subtitle = subtitle,
        leading = { Avatar(m.displayName) },
        trailing = {
            if (canManage && !isMe && role != Role.OWNER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.status == "PENDING") {
                        TextButton(onClick = { vm.approve(m.id) }) { Text(stringResource(R.string.family_approve)) }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.family_change_role)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        listOf(Role.ADMIN, Role.MEMBER, Role.CHILD, Role.GUEST).forEach { r ->
                            DropdownMenuItem(
                                text = { Text(stringResource(r.title)) },
                                onClick = { menu = false; vm.setRole(m.id, r) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.family_remove), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; vm.removeMember(m.id) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun InviteRow(inv: InviteEntity, onCopy: () -> Unit, onShare: () -> Unit, onRevoke: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inv.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                Text(
                    stringResource(
                        R.string.family_invite_code,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(inv.expiresAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            var qr by remember { mutableStateOf(false) }
            IconButton(onClick = { qr = true }) { Icon(Icons.Outlined.QrCode2, stringResource(R.string.invite_qr)) }
            if (qr) AlertDialog(
                onDismissRequest = { qr = false },
                confirmButton = { TextButton(onClick = { qr = false }) { Text(stringResource(R.string.close)) } },
                title = { Text(stringResource(R.string.invite_qr)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        QrImage("smartfamily:join:" + inv.code)
                        Text(inv.code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.invite_qr_sub), style = MaterialTheme.typography.bodySmall)
                    }
                },
            )
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, null) }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
            IconButton(onClick = onRevoke) { Icon(Icons.Outlined.Delete, stringResource(R.string.family_invite_revoke)) }
        }
    }
}

private fun shareInvite(context: android.content.Context, familyName: String, inv: InviteEntity) {
    val text = context.getString(R.string.family_invite_share_text, familyName, inv.code)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text(confirmText ?: stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
