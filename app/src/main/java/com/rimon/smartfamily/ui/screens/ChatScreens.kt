package com.rimon.smartfamily.ui.screens

import com.rimon.smartfamily.ui.components.SfTopAppBar
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.repeatOnLifecycle
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
import com.rimon.smartfamily.data.repo.MessagePayload
import androidx.compose.foundation.verticalScroll
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
fun ChatListScreen(vm: ChatViewModel, viewer: Viewer, onBack: () -> Unit, onOpen: (String) -> Unit, showBack: Boolean = true) {
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val people by vm.people.collectAsState(initial = emptyList())
    val listLife = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(Unit) { if (vm.signedIn) listLife.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { vm.liveList() } }
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
            SfTopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = { if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
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
            val mine = chats.filter { it.kind != "ALBUM" }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, viewer: Viewer, chatId: String, onBack: () -> Unit, onOpenRoute: (String) -> Unit = {}) {
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
    var newPoll by remember { mutableStateOf(false) }
    val polls by remember(viewer.familyId) { vm.polls(viewer.familyId) }.collectAsState(initial = emptyList())
    val votes by remember(viewer.familyId) { vm.votes(viewer.familyId) }.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    val names = members.associate { it.userId to it.displayName }
    val c = chat ?: return

    // 2.2: only while the chat is really on screen — notifications come back the moment the app goes to the background
    val chatLife = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(c.id) { chatLife.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { vm.live(c) } }
    LaunchedEffect(messages.size) {
        vm.loadTranscripts(messages.filter { it.entity.kind == "voice" }.map { it.entity.id })
        val n = listState.layoutInfo.totalItemsCount
        if (n > 0) listState.animateScrollToItem(n - 1)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendMedia(c, uri, "image") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.sendMedia(c, uri, "file") }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.sendMedia(c, uri, "video") }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.sendLocation(c) }
    val starred by vm.starred.collectAsState()
    val summary by vm.summary.collectAsState()
    val scheduledList by remember(c.id) { vm.scheduled(c.id) }.collectAsState(initial = emptyList())
    var more by remember { mutableStateOf(false) }
    var showStarred by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showScheduled by remember { mutableStateOf(false) }
    var cardPicker by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf<ShownMessage?>(null) }
    var scheduling by remember { mutableStateOf(false) }
    val allChats by remember(viewer.userId) { vm.chats(viewer.userId) }.collectAsState(initial = emptyList())
    val myRead = reads.firstOrNull { it.userId == viewer.userId }?.lastReadAt ?: 0L
    val pinned = messages.filter { m -> reactions.any { it.messageId == m.entity.id && it.emoji == "📌" && it.deletedAt == null } }
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
            SfTopAppBar(
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
                    Box {
                        IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, null) }
                        DropdownMenu(more, { more = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_summarize)) }, onClick = { more = false; vm.summarize(c, messages, myRead.coerceAtMost(System.currentTimeMillis() - 1), names) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_media)) }, onClick = { more = false; showMedia = true })
                            DropdownMenuItem(text = { Text("📊 " + stringResource(R.string.chat_all_polls)) }, onClick = { more = false; onOpenRoute("polls") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_starred)) }, onClick = { more = false; showStarred = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.chat_scheduled_list, scheduledList.size)) }, onClick = { more = false; showScheduled = true })
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().imePadding()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            pinned.lastOrNull()?.let { pm ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text("📌 " + (pm.payload?.text?.ifBlank { null } ?: pm.payload?.cardTitle ?: "…") + if (pinned.size > 1) "  (+${pinned.size - 1})" else "",
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
            }
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
                        starred = m.entity.id in starred, pinned = pinned.any { it.entity.id == m.entity.id },
                        onStar = { vm.toggleStar(m.entity.id) }, onPin = { vm.togglePin(m.entity) }, onForward = { forwarding = m },
                        onOpenRoute = onOpenRoute, myName = names[viewer.userId].orEmpty(),
                        pollSlot = { pp -> PollContent(vm, viewer, polls.firstOrNull { it.id == pp.cardId }, votes, names, pp.cardTitle, onOpenRoute) },
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
                        DropdownMenuItem(text = { Text("🎬 " + stringResource(R.string.chat_video)) }, onClick = {
                            attach = false; pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        })
                        DropdownMenuItem(text = { Text("📍 " + stringResource(R.string.chat_location)) }, onClick = {
                            attach = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) vm.sendLocation(c)
                            else locPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        })
                        DropdownMenuItem(text = { Text("📊 " + stringResource(R.string.chat_poll)) }, onClick = { attach = false; newPoll = true })
                        DropdownMenuItem(text = { Text("🃏 " + stringResource(R.string.chat_card)) }, onClick = { attach = false; cardPicker = true })
                    }
                }
                // 2.2: the field stays editable while recording, so the keyboard does not close and nothing jumps under the finger
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (!recording) text = it },
                    placeholder = { Text(stringResource(if (recording) R.string.chat_recording else R.string.chat_type)) },
                    modifier = Modifier.weight(1f).heightIn(max = 140.dp).testTag("chat_input"),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = if (recording) ({ Text("🔴", Modifier.padding(start = 6.dp)) }) else null,
                )
                Spacer(Modifier.size(6.dp))
                if (text.isNotBlank() && !recording) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).clip(CircleShape).combinedClickable(
                        onLongClick = { if (editing.isEmpty()) scheduling = true },
                        onClick = {
                            val target = messages.firstOrNull { it.entity.id == editing }
                            if (target != null) vm.edit(target, text) else vm.sendText(c, text, replyTo)
                            text = ""; replyTo = ""; editing = ""
                        },
                    )) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                } else {
                    // Hold to record, release to send (CHT-6).
                    Surface(
                        shape = CircleShape,
                        color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp).graphicsLayer { val k = if (recording) 1.25f else 1f; scaleX = k; scaleY = k }.testTag("chat_mic").pointerInput(c.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (!granted) { micPermission.launch(Manifest.permission.RECORD_AUDIO); waitForUpOrCancellation(); return@awaitEachGesture }
                                vm.startRecording()
                                // Stops only when the finger is lifted; sliding far sideways cancels (like WhatsApp).
                                val start = down.position
                                var cancelled = false
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    ch.consume()
                                    if (kotlin.math.abs(ch.position.x - start.x) > 140.dp.toPx()) cancelled = true
                                    if (!ch.pressed) break
                                }
                                vm.stopRecording(c, send = !cancelled)
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
    summary?.let { s ->
        AlertDialog(onDismissRequest = { vm.clearSummary() }, title = { Text(stringResource(R.string.chat_summarize)) },
            text = { Text(s, Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) },
            confirmButton = { TextButton(onClick = { vm.clearSummary() }) { Text(stringResource(R.string.ok)) } })
    }
    if (showStarred || showMedia) {
        val list = if (showStarred) messages.filter { it.entity.id in starred } else messages.filter { it.entity.kind in setOf("image", "video", "file") && it.payload?.deleted != true }
        AlertDialog(onDismissRequest = { showStarred = false; showMedia = false },
            title = { Text(stringResource(if (showStarred) R.string.chat_starred else R.string.chat_media)) },
            text = {
                LazyColumn {
                    if (list.isEmpty()) item { Text(stringResource(R.string.chat_none)) }
                    items(list, key = { "s" + it.entity.id }) { m ->
                        if (m.entity.kind == "image") ImageContent(vm, m) else Text((names[m.entity.senderId] ?: "") + ": " + (m.payload?.text?.ifBlank { null } ?: m.payload?.mediaName ?: ""), Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStarred = false; showMedia = false }) { Text(stringResource(R.string.ok)) } })
    }
    if (showScheduled) AlertDialog(onDismissRequest = { showScheduled = false }, title = { Text(stringResource(R.string.chat_scheduled_list, scheduledList.size)) },
        text = {
            Column {
                if (scheduledList.isEmpty()) Text(stringResource(R.string.chat_schedule_hint))
                scheduledList.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(s.sendAt)) + " · " + s.text, Modifier.weight(1f))
                        TextButton(onClick = { vm.cancelScheduled(s.id) }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showScheduled = false }) { Text(stringResource(R.string.ok)) } })
    if (scheduling) {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context, { _, y, mo, d ->
            android.app.TimePickerDialog(context, { _, h, mi ->
                val at = java.util.Calendar.getInstance().apply { set(y, mo, d, h, mi, 0) }.timeInMillis
                vm.schedule(c, text, at); text = ""
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show()
        scheduling = false
    }
    forwarding?.let { fm ->
        AlertDialog(onDismissRequest = { forwarding = null }, title = { Text(stringResource(R.string.chat_forward)) },
            text = {
                LazyColumn {
                    items(allChats.filter { it.id != c.id && it.familyId == c.familyId }, key = { "f" + it.id }) { other ->
                        Text(chatTitle(other, members, viewer.userId), Modifier.fillMaxWidth().clickable { vm.forward(fm, other); forwarding = null }.padding(12.dp))
                    }
                }
            }, confirmButton = { TextButton(onClick = { forwarding = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (cardPicker) CardPickerDialog(vm, c, viewer, onDismiss = { cardPicker = false })
    if (newPoll) NewPollDialog(onDismiss = { newPoll = false }) { q, opts, multi, anon, closes -> vm.createPoll(c, q, opts, multi, anon, closes); newPoll = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    vm: ChatViewModel, m: ShownMessage, mine: Boolean, showName: Boolean, senderName: String,
    replied: ShownMessage?, repliedName: String?, reactions: Map<String, Int>, readByAll: Boolean, transcript: String?,
    onReply: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit,
    starred: Boolean = false, pinned: Boolean = false, onStar: () -> Unit = {}, onPin: () -> Unit = {}, onForward: () -> Unit = {},
    onOpenRoute: (String) -> Unit = {}, myName: String = "",

    pollSlot: @Composable (MessagePayload) -> Unit = {},
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
                    m.entity.kind == "video" -> FileContent(vm, m)
                    m.entity.kind == "card" && p.cardType == "poll" -> pollSlot(p)
                    m.entity.kind == "card" -> CardContent(p, onOpenRoute)
                    m.entity.kind == "location" -> LocationContent(p)
                    else -> Text(mentions(p.text, myName), style = MaterialTheme.typography.bodyLarge)
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
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_forward)) }, onClick = { menu = false; onForward() })
                DropdownMenuItem(text = { Text(stringResource(if (pinned) R.string.chat_unpin else R.string.chat_pin)) }, onClick = { menu = false; onPin() })
                DropdownMenuItem(text = { Text(stringResource(if (starred) R.string.chat_unstar else R.string.chat_star)) }, onClick = { menu = false; onStar() })
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


/** @mentions are highlighted; a mention of me is bold. */
@Composable
private fun mentions(text: String, myName: String): androidx.compose.ui.text.AnnotatedString {
    val color = MaterialTheme.colorScheme.primary
    return androidx.compose.ui.text.buildAnnotatedString {
        var last = 0
        Regex("@[\\p{L}\\p{N}_]+").findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            val me = myName.isNotBlank() && m.value.drop(1).equals(myName.split(' ').first(), ignoreCase = true)
            pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = if (me) FontWeight.Bold else FontWeight.SemiBold)); append(m.value); pop()
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

private fun cardRoute(type: String, id: String) = when (type) {
    "shopping" -> "shopping/list/$id"; "recipes" -> "recipe/$id"; "travel" -> "trip/$id"; "polls" -> "polls"; "calendar" -> "calendar"
    "chores" -> "chores"; "appliances" -> "appliance/$id"; "home_book" -> "homebook/0"; "knowledge" -> "homebook/1"; else -> "home"
}

@Composable
private fun CardContent(p: MessagePayload, onOpenRoute: (String) -> Unit) {
    Card(onClick = { onOpenRoute(cardRoute(p.cardType, p.cardId)) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (p.cardType) { "shopping" -> "🛒"; "recipes" -> "🍲"; "travel" -> "🧳"; "polls" -> "📊"; "calendar" -> "📅"; "chores" -> "🧹"; else -> "📎" }, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.padding(start = 8.dp)) {
                Text(p.cardTitle, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.chat_open_file), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LocationContent(p: MessagePayload) {
    val context = LocalContext.current
    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${p.lat},${p.lon}?q=${p.lat},${p.lon}"))) } }) {
        Text("📍 " + p.text + "\n" + String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon))
    }
}

@Composable
private fun CardPickerDialog(vm: ChatViewModel, chat: ChatEntity, viewer: Viewer, onDismiss: () -> Unit) {
    val modules = listOf(com.rimon.smartfamily.model.AppModule.SHOPPING, com.rimon.smartfamily.model.AppModule.RECIPES, com.rimon.smartfamily.model.AppModule.TRAVEL,
        com.rimon.smartfamily.model.AppModule.POLLS, com.rimon.smartfamily.model.AppModule.CALENDAR, com.rimon.smartfamily.model.AppModule.CHORES)
    var module by remember { mutableStateOf(modules.first()) }
    var list by remember { mutableStateOf<List<com.rimon.smartfamily.data.repo.ShareItem>>(emptyList()) }
    LaunchedEffect(module) { vm.cardItems(viewer.familyId, module) { list = it } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.chat_card)) },
        text = {
            LazyColumn {
                item { ChoiceIn(null, modules.map { it.key to stringResource(it.title) }, module.key) { k -> module = modules.first { it.key == k } } }
                items(list, key = { "c" + it.id }) { i ->
                    Text(i.title, Modifier.fillMaxWidth().clickable { vm.sendCard(chat, module.key, i.id, i.title); onDismiss() }.padding(12.dp))
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}


/** A poll message: vote right in the chat; results update for everyone as votes sync. */
@Composable
private fun PollContent(
    vm: ChatViewModel, viewer: Viewer, poll: com.rimon.smartfamily.data.db.PollEntity?, votes: List<com.rimon.smartfamily.data.db.PollVoteEntity>,
    names: Map<String, String>, title: String, onOpenRoute: (String) -> Unit,
) {
    Column(Modifier.widthIn(min = 220.dp)) {
        Text("📊 " + (poll?.question ?: title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (poll == null) { Text(stringResource(R.string.poll_loading), style = MaterialTheme.typography.bodySmall); return@Column }
        val pv = votes.filter { it.pollId == poll.id && it.deletedAt == null && it.choices.isNotBlank() }
        val closed = poll.closed || (poll.closesAt in 1 until System.currentTimeMillis())
        val my = pv.firstOrNull { it.userId == viewer.userId }?.choices?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        poll.options.split('\n').forEachIndexed { i, o ->
            val who = pv.filter { i.toString() in it.choices.split(',') }
            val frac = if (pv.isEmpty()) 0f else who.size / pv.size.toFloat()
            Column(Modifier.fillMaxWidth().clickable(enabled = !closed) { vm.vote(poll, i) }.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (i in my) "● " else "○ ") + o, Modifier.weight(1f), fontWeight = if (i in my) FontWeight.Bold else null)
                    Text("${who.size}")
                }
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                if (!poll.anonymous && who.isNotEmpty()) Text(who.mapNotNull { names[it.userId] }.joinToString(", "), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (closed) R.string.poll_closed_label else R.string.poll_voted, pv.size), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (!closed && (poll.createdBy == viewer.userId || viewer.canManage)) TextButton(onClick = { vm.closePoll(poll) }) { Text(stringResource(R.string.poll_close)) }
            TextButton(onClick = { onOpenRoute("polls") }) { Text(stringResource(R.string.poll_more)) }
        }
    }
}
