package com.rimon.smartfamily.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.ChatEntity
import com.rimon.smartfamily.data.db.MemberEntity
import com.rimon.smartfamily.data.repo.ShownMessage
import com.rimon.smartfamily.ui.ChatViewModel
import com.rimon.smartfamily.ui.components.Avatar
import com.rimon.smartfamily.ui.components.HelpButton
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val REACTIONS = listOf("👍", "❤️", "😂", "😮", "🙏", "✅")

@Composable
private fun chatTitle(chat: ChatEntity, members: List<MemberEntity>, me: String): String = when (chat.kind) {
    "FAMILY" -> stringResource(R.string.chat_family)
    "DIRECT" -> chat.members.split(',').firstOrNull { it != me }?.let { id -> members.firstOrNull { it.userId == id }?.displayName } ?: chat.title
    else -> chat.title
}

// ---------------- chat list ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: ChatViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val people by vm.people.collectAsState(initial = emptyList())
    val chats by remember(viewer.userId) { vm.chats(viewer.userId) }.collectAsState(initial = emptyList())
    val reads by remember(viewer.familyId, viewer.userId) { vm.myReads(viewer.familyId, viewer.userId) }.collectAsState(initial = emptyList())
    val busy by vm.busy.collectAsState()
    val active = members.filter { it.status == "ACTIVE" }
    val iAmMember = active.any { it.userId == viewer.userId }
    var creating by remember { mutableStateOf(false) }

    // In a family that only shares with us we never create its family chat or publish keys there.
    LaunchedEffect(viewer.familyId, active.size, iAmMember) {
        if (vm.signedIn && iAmMember) vm.prepare(viewer.familyId, active.map { it.userId })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_chat) },
            )
        },
        floatingActionButton = {
            if (vm.signedIn && iAmMember) ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.chat_new)) })
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!vm.signedIn) {
                Text(stringResource(R.string.chat_needs_cloud), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.chat_e2e), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val mine = chats
            if (mine.isEmpty()) Text(stringResource(R.string.chat_none), Modifier.padding(24.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(mine, key = { it.id }) { chat ->
                    val preview by remember(chat.id) { vm.lastPreview(chat.id) }.collectAsState(initial = "")
                    val read = reads.firstOrNull { it.chatId == chat.id }?.lastReadAt ?: 0L
                    val unread = chat.lastMessageAt > read && preview.isNotEmpty()
                    val title = chatTitle(chat, people, viewer.userId)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(chat.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(title, 48.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal)
                            Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (chat.lastMessageAt > 0) Text(timeLabel(chat.lastMessageAt), style = MaterialTheme.typography.labelSmall)
                            if (unread) Badge { Text(" ") }
                        }
                    }
                }
            }
        }
    }

    if (creating) NewChatDialog(people.filter { it.userId != viewer.userId }, onDismiss = { creating = false }) { title, ids, direct ->
        creating = false
        vm.createChat(viewer.familyId, title, ids, direct)
    }
}

@Composable
private fun NewChatDialog(others: List<MemberEntity>, onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf("") } // csv of user ids
    val ids = picked.split(',').filter { it.isNotBlank() }
    val direct = ids.size == 1 && title.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_new)) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.chat_new_group)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.chat_pick_members), style = MaterialTheme.typography.labelLarge)
                others.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        picked = (if (m.userId in ids) ids - m.userId else ids + m.userId).joinToString(",")
                    }) {
                        Checkbox(m.userId in ids, null)
                        Text(m.displayName)
                    }
                }
                if (direct) Text(stringResource(R.string.chat_direct), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(enabled = ids.isNotEmpty() && (direct || title.isNotBlank()), onClick = { onCreate(title.trim(), ids, direct) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun timeLabel(ms: Long): String {
    val now = System.currentTimeMillis()
    val pattern = if (now - ms < 20 * 60 * 60 * 1000L) "HH:mm" else "dd/MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}

// ---------------- conversation ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel, viewer: Viewer, chatId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val chat by remember(chatId) { vm.chat(chatId) }.collectAsState(initial = null)
    val members by vm.people.collectAsState(initial = emptyList())
    val messages by remember(chatId) { vm.shown(chatId) }.collectAsState(initial = emptyList())
    val reactions by remember(chatId) { vm.reactions(chatId) }.collectAsState(initial = emptyList())
    val reads by remember(chatId) { vm.reads(chatId) }.collectAsState(initial = emptyList())
    val transcripts by vm.transcripts.collectAsState()
    val recording by vm.recording.collectAsState()
    val busy by vm.busy.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var replyTo by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val names = members.associate { it.userId to it.displayName }
    val c = chat ?: return

    LaunchedEffect(c.id) { vm.live(c) }
    LaunchedEffect(messages.size) {
        vm.loadTranscripts(messages.filter { it.entity.kind == "voice" }.map { it.entity.id })
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendMedia(c, uri, "image") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.sendMedia(c, uri, "file") }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(context, R.string.chat_mic_needed, Toast.LENGTH_LONG).show()
    }

    // Messages older than the disappearing period are hidden here (and wiped by the sender's phone below).
    val cutoff = if (c.disappearDays > 0) System.currentTimeMillis() - c.disappearDays * 86_400_000L else 0L
    LaunchedEffect(messages.size, c.disappearDays) {
        if (cutoff > 0) messages.filter { it.entity.sentAt < cutoff && it.entity.senderId == viewer.userId && it.entity.ciphertext.isNotEmpty() }
            .forEach { vm.delete(it.entity) }
    }
    val visible = messages.filter { it.entity.sentAt >= cutoff }.filter { m ->
        query.isBlank() || (m.payload?.text?.contains(query, ignoreCase = true) == true) ||
            (m.payload?.transcript?.contains(query, ignoreCase = true) == true) || (transcripts[m.entity.id]?.contains(query, true) == true)
    }
    val others = c.members.split(',').filter { it.isNotBlank() && it != viewer.userId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.chat_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        val t = chatTitle(c, members, viewer.userId)
                        Avatar(t, 36.dp)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(others.mapNotNull { names[it] }.joinToString(", "), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, stringResource(R.string.chat_search)) }
                    IconButton(onClick = { info = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.chat_info)) }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().imePadding()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visible, key = { it.entity.id }) { m ->
                    val mine = m.entity.senderId == viewer.userId
                    val readByAll = mine && others.isNotEmpty() && others.all { u -> reads.any { it.userId == u && it.lastReadAt >= m.entity.sentAt } }
                    MessageBubble(
                        vm = vm, m = m, mine = mine, showName = c.kind != "DIRECT" && !mine,
                        senderName = names[m.entity.senderId] ?: "?",
                        replied = messages.firstOrNull { it.entity.id == m.entity.replyTo },
                        repliedName = messages.firstOrNull { it.entity.id == m.entity.replyTo }?.let { names[it.entity.senderId] },
                        reactions = reactions.filter { it.messageId == m.entity.id }.groupBy { it.emoji }.mapValues { it.value.size },
                        readByAll = readByAll,
                        transcript = m.payload?.transcript?.takeIf { it.isNotBlank() } ?: transcripts[m.entity.id],
                        onReply = { replyTo = m.entity.id },
                        onEdit = { editing = m.entity.id; text = m.payload?.text.orEmpty() },
                        onDelete = { vm.delete(m.entity) },
                        onReact = { vm.react(m.entity, it) },
                    )
                }
            }
            val replied = messages.firstOrNull { it.entity.id == replyTo }
            AnimatedVisibility(replied != null || editing.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (editing.isNotEmpty()) stringResource(R.string.chat_edit)
                            else stringResource(R.string.chat_replying_to, names[replied?.entity?.senderId] ?: "") + ": " + (replied?.payload?.text ?: "").take(60),
                            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(onClick = { replyTo = ""; if (editing.isNotEmpty()) { editing = ""; text = "" } }) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { attach = true }) { Icon(Icons.Outlined.AttachFile, stringResource(R.string.chat_attach_file)) }
                    DropdownMenu(attach, { attach = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_attach_photo)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = {
                            attach = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.chat_attach_file)) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) }, onClick = {
                            attach = false; pickFile.launch(arrayOf("*/*"))
                        })
                    }
                }
                OutlinedTextField(
                    value = if (recording) stringResource(R.string.chat_recording) else text,
                    onValueChange = { if (!recording) text = it },
                    placeholder = { Text(stringResource(R.string.chat_type)) },
                    modifier = Modifier.weight(1f).heightIn(max = 140.dp),
                    shape = RoundedCornerShape(24.dp),
                    readOnly = recording,
                )
                Spacer(Modifier.size(6.dp))
                if (text.isNotBlank() && !recording) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).clip(CircleShape).clickable {
                        val target = messages.firstOrNull { it.entity.id == editing }
                        if (target != null) vm.edit(target, text) else vm.sendText(c, text, replyTo)
                        text = ""; replyTo = ""; editing = ""
                    }) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                } else {
                    // Hold to record, release to send (CHT-6).
                    Surface(
                        shape = CircleShape,
                        color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (recording) 60.dp else 48.dp).pointerInput(c.id) {
                            awaitEachGesture {
                                awaitFirstDown()
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) { micPermission.launch(Manifest.permission.RECORD_AUDIO); waitForUpOrCancellation(); return@awaitEachGesture }
                                vm.startRecording()
                                val up = waitForUpOrCancellation()
                                vm.stopRecording(c, send = up != null)
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Mic, stringResource(R.string.chat_record), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                }
            }
        }
    }

    if (info) ChatInfoDialog(vm, c, members.filter { it.status == "ACTIVE" }, viewer, onDismiss = { info = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    vm: ChatViewModel, m: ShownMessage, mine: Boolean, showName: Boolean, senderName: String,
    replied: ShownMessage?, repliedName: String?, reactions: Map<String, Int>, readByAll: Boolean, transcript: String?,
    onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val p = m.payload
    val deleted = p?.deleted == true
    val bg = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box {
            Column(
                Modifier.widthIn(max = 300.dp).clip(shape).background(bg)
                    .combinedClickable(onClick = {}, onLongClick = { if (!deleted && p != null) menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (showName) Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (replied != null) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 4.dp)) {
                        Column(Modifier.padding(6.dp)) {
                            Text(repliedName ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(replied.payload?.text?.ifBlank { null } ?: "📎", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                when {
                    p == null -> Text(stringResource(R.string.chat_encrypted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    deleted -> Text(stringResource(R.string.chat_deleted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    m.entity.kind == "image" -> ImageContent(vm, m)
                    m.entity.kind == "voice" -> VoiceContent(vm, m, transcript)
                    m.entity.kind == "file" -> FileContent(vm, m)
                    else -> Text(p.text, style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (m.entity.editedAt != null && !deleted) Text(stringResource(R.string.chat_edited) + " · ", style = MaterialTheme.typography.labelSmall)
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.entity.sentAt)), style = MaterialTheme.typography.labelSmall)
                    if (mine) Icon(Icons.Outlined.DoneAll, if (readByAll) stringResource(R.string.chat_read_all) else null, Modifier.padding(start = 4.dp).size(14.dp),
                        tint = if (readByAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
            }
            DropdownMenu(menu, { menu = false }) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    REACTIONS.forEach { e -> Text(e, Modifier.clip(CircleShape).clickable { menu = false; onReact(e) }.padding(6.dp), style = MaterialTheme.typography.titleLarge) }
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_reply)) }, onClick = { menu = false; onReply() })
                if (p?.text?.isNotBlank() == true) DropdownMenuItem(text = { Text(stringResource(R.string.chat_copy)) }, onClick = { menu = false; clipboard.setText(AnnotatedString(p.text)) })
                if (mine && m.entity.kind == "text") DropdownMenuItem(text = { Text(stringResource(R.string.chat_edit)) }, onClick = { menu = false; onEdit() })
                if (mine) DropdownMenuItem(text = { Text(stringResource(R.string.chat_delete_all)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    if (reactions.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
            reactions.forEach { (e, n) ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(end = 4.dp).clickable { onReact(e) }) {
                    Text(if (n > 1) "$e $n" else e, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ImageContent(vm: ChatViewModel, m: ShownMessage) {
    val context = LocalContext.current
    val file by produceState<File?>(null, m.entity.id) { value = vm.media(m) }
    val bmp = remember(file) { file?.let { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull() } }
    if (bmp == null) Text("📷 " + stringResource(R.string.chat_photo))
    else Image(
        bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).clickable { file?.let { openFile(context, it, "image/*") } },
    )
}

@Composable
private fun FileContent(vm: ChatViewModel, m: ShownMessage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.AttachFile, null)
        Text(m.payload?.mediaName.orEmpty(), Modifier.weight(1f, fill = false).padding(horizontal = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { scope.launch { vm.media(m)?.let { openFile(context, it, m.payload?.mediaMime ?: "*/*") } } }) { Text(stringResource(R.string.chat_open_file)) }
    }
}

@Composable
private fun VoiceContent(vm: ChatViewModel, m: ShownMessage, transcript: String?) {
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(m.entity.id) { onDispose { player?.release(); player = null } }
    Column(Modifier.widthIn(min = 180.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val current = player
                if (current != null) { current.release(); player = null }
                else scope.launch {
                    val f = vm.media(m) ?: return@launch
                    player = MediaPlayer().apply {
                        setDataSource(f.absolutePath); prepare(); start()
                        setOnCompletionListener { it.release(); player = null }
                    }
                }
            }) { Icon(if (player != null) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, stringResource(R.string.chat_voice)) }
            Text("🎤 " + "%d:%02d".format((m.payload?.durationSec ?: 0) / 60, (m.payload?.durationSec ?: 0) % 60))
            Spacer(Modifier.weight(1f))
            if (transcript == null) TextButton(onClick = { vm.transcribe(m) }) { Text(stringResource(R.string.chat_transcribe)) }
        }
        if (transcript != null) Text(transcript, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun openFile(context: android.content.Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("", uri)
    runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---------------- info: members, safety numbers, disappearing ----------------

@Composable
private fun ChatInfoDialog(vm: ChatViewModel, chat: ChatEntity, members: List<MemberEntity>, viewer: Viewer, onDismiss: () -> Unit) {
    val keys by remember { vm.keys() }.collectAsState(initial = emptyList())
    var picked by remember(chat.members) { mutableStateOf(chat.members.split(',').filter { it.isNotBlank() }.toSet()) }
    val canEdit = chat.kind == "GROUP" && (chat.createdBy == viewer.userId || viewer.canManage)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_info)) },
        text = {
            LazyColumn {
                item { Text(stringResource(R.string.chat_members), style = MaterialTheme.typography.titleSmall) }
                items(members, key = { "m" + it.id }) { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canEdit && m.userId != viewer.userId) Checkbox(m.userId in picked, { on -> picked = if (on) picked + m.userId else picked - m.userId })
                        else if (m.userId in picked) Icon(Icons.Outlined.Lock, null, Modifier.padding(12.dp).size(16.dp))
                        if (canEdit || m.userId in picked) Text(m.displayName)
                    }
                }
                if (canEdit) item {
                    TextButton(onClick = { vm.updateMembers(chat, picked.toList()); onDismiss() }) { Text(stringResource(R.string.chat_save_members)) }
                }
                item {
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.chat_disappear), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to R.string.dis_off, 1 to R.string.dis_1, 7 to R.string.dis_7, 30 to R.string.dis_30).forEach { (d, label) ->
                            FilterChip(chat.disappearDays == d, { vm.setDisappearing(chat, d) }, { Text(stringResource(label)) })
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.chat_safety), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.chat_safety_body), style = MaterialTheme.typography.bodySmall)
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(stringResource(R.string.chat_my_number), style = MaterialTheme.typography.labelMedium)
                            Text(vm.myFingerprint(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                items(keys.filter { it.userId in picked && it.deviceId != vm.myDevice }, key = { "k" + it.id }) { k ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(members.firstOrNull { it.userId == k.userId }?.displayName ?: "?", style = MaterialTheme.typography.labelMedium)
                        Text(k.fingerprint, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
