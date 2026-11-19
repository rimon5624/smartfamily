package com.rimon.smartfamily.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.ChatEntity
import com.rimon.smartfamily.data.db.TreePersonEntity
import com.rimon.smartfamily.data.repo.ShownMessage
import com.rimon.smartfamily.ui.ChatViewModel
import com.rimon.smartfamily.ui.ExtrasViewModel
import com.rimon.smartfamily.ui.RecordsViewModel
import java.io.File

/** Family hub (2.0): the family tree, shared encrypted photo albums, a phone book and shared gifts. */
@Composable
fun FamilyHubScreen(vm: RecordsViewModel, chat: ChatViewModel, extras: ExtrasViewModel, viewer: Viewer, onBack: () -> Unit, onOpenAlbum: (String) -> Unit, onGifts: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tree by remember(viewer.familyId) { vm.tree(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val chats by remember(viewer.userId) { chat.chats(viewer.userId) }.collectAsState(initial = emptyList())
    val contacts by remember(viewer.familyId) { extras.section(viewer.familyId, "HOME") }.collectAsState(initial = emptyList())
    val albums = chats.filter { it.kind == "ALBUM" && it.familyId == viewer.familyId }
    var edit by remember { mutableStateOf<TreePersonEntity?>(null) }
    var newAlbum by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tabs = listOf(stringResource(R.string.fam_tree), stringResource(R.string.fam_album), stringResource(R.string.fam_phones), stringResource(R.string.money_split_gift))

    ModuleScaffold(stringResource(R.string.mod_family), R.string.help_family, onBack, tabs, tab, { if (it == 3) onGifts() else tab = it },
        fabLabel = if (!viewer.isChild && tab < 2) stringResource(R.string.budget_add) else null,
        onFab = { if (tab == 0) edit = TreePersonEntity(id = vm.id(), familyId = viewer.familyId, name = "") else newAlbum = true }) {
        when (tab) {
            0 -> {
                if (tree.isEmpty()) item { Text(stringResource(R.string.fam_tree_empty)) }
                val byId = tree.associateBy { it.id }
                val roots = tree.filter { p -> p.parents.split(',').none { it.isNotBlank() && byId.containsKey(it) } }
                fun children(id: String) = tree.filter { id in it.parents.split(',') }
                val shown = mutableSetOf<String>()
                fun rows(p: TreePersonEntity, depth: Int, out: MutableList<Pair<TreePersonEntity, Int>>) {
                    if (!shown.add(p.id) || depth > 12) return
                    out += p to depth
                    children(p.id).sortedBy { it.birth }.forEach { rows(it, depth + 1, out) }
                }
                val flat = mutableListOf<Pair<TreePersonEntity, Int>>()
                roots.sortedBy { it.birth }.forEach { if (it.spouse.isBlank() || it.id < it.spouse || !byId.containsKey(it.spouse)) rows(it, 0, flat) }
                tree.filter { it.id !in shown }.forEach { rows(it, 0, flat) }
                items(flat, key = { it.first.id }) { (p, depth) ->
                    val spouse = byId[p.spouse]
                    Row(Modifier.fillMaxWidth().clickable(enabled = !viewer.isChild) { edit = p }.padding(start = (depth * 20).dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (depth == 0) "🌳 " else "└ ")
                        Column {
                            Text(p.name + (spouse?.let { " ❤ " + it.name } ?: ""), fontWeight = FontWeight.SemiBold)
                            val life = listOfNotNull(p.birth.takeIf { it.isNotBlank() }?.take(4), p.death.takeIf { it.isNotBlank() }?.take(4)?.let { "– $it ז״ל" }).joinToString(" ")
                            if (life.isNotBlank()) Text(life, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            1 -> {
                if (albums.isEmpty()) item { Text(stringResource(R.string.fam_album_empty)) }
                items(albums, key = { it.id }) { a -> AlbumCard(chat, a) { onOpenAlbum(a.id) } }
            }
            else -> {
                items(members.filter { it.status == "ACTIVE" }, key = { "m" + it.id }) { m ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("👤 " + m.displayName, Modifier.weight(1f))
                        Text(stringResource(R.string.fam_member), style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(contacts.filter { it.phone.isNotBlank() }, key = { "c" + it.id }) { e ->
                    Row(Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e.phone))) } }.padding(vertical = 6.dp)) {
                        Text("📞 " + e.title, Modifier.weight(1f)); Text(e.phone)
                    }
                }
            }
        }
    }

    edit?.let { p ->
        var name by remember { mutableStateOf(p.name) }; var parents by remember { mutableStateOf(p.parents.split(',').filter { it.isNotBlank() }.toSet()) }
        var spouse by remember { mutableStateOf(p.spouse) }; var birth by remember { mutableStateOf(p.birth) }; var death by remember { mutableStateOf(p.death) }
        var member by remember { mutableStateOf(p.memberId) }
        val others = tree.filter { it.id != p.id }
        EditDialog(stringResource(R.string.fam_tree), name.isNotBlank(), { edit = null }, {
            vm.save(p.copy(name = name.trim(), parents = parents.joinToString(","), spouse = spouse, birth = birth, death = death, memberId = member))
            // keep the couple linked both ways
            others.firstOrNull { it.id == spouse }?.let { s -> if (s.spouse != p.id) vm.save(s.copy(spouse = p.id)) }
            edit = null
        }, if (p.name.isNotBlank()) ({ vm.delete(p); edit = null }) else null) {
            TextIn(stringResource(R.string.fam_name), name) { name = it }
            if (others.isNotEmpty()) {
                Text(stringResource(R.string.fam_parents), style = MaterialTheme.typography.labelLarge)
                others.forEach { o -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(o.id in parents, { on -> parents = if (on) parents + o.id else parents - o.id }); Text(o.name) } }
                ChoiceIn(stringResource(R.string.fam_spouse), listOf("" to "—") + others.map { it.id to it.name }, spouse) { spouse = it }
            }
            DateIn(stringResource(R.string.fam_birth), birth) { birth = it }
            DateIn(stringResource(R.string.fam_death), death) { death = it }
            if (members.isNotEmpty()) ChoiceIn(stringResource(R.string.fam_is_member), listOf("" to "—") + members.map { it.userId to it.displayName }, member) { member = it }
        }
    }
    if (newAlbum) {
        var title by remember { mutableStateOf("") }
        EditDialog(stringResource(R.string.fam_new_album), title.isNotBlank(), { newAlbum = false }, {
            chat.createAlbum(viewer.familyId, title.trim(), members.filter { it.status == "ACTIVE" }.map { it.userId }); newAlbum = false
        }) {
            TextIn(stringResource(R.string.fam_album_name), title) { title = it }
            Text(stringResource(R.string.fam_album_note), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AlbumCard(vm: ChatViewModel, a: ChatEntity, onOpen: () -> Unit) {
    val shown by remember(a.id) { vm.shown(a.id) }.collectAsState(initial = emptyList())
    val photos = shown.filter { it.entity.kind == "image" && it.payload?.deleted != true }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("🖼 " + a.title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.fam_photos, photos.size), style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                photos.takeLast(4).forEach { m -> Thumb(vm, m, Modifier.weight(1f)) }
                repeat((4 - photos.takeLast(4).size).coerceAtLeast(0)) { androidx.compose.foundation.layout.Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Thumb(vm: ChatViewModel, m: ShownMessage, modifier: Modifier) {
    val file by produceState<File?>(null, m.entity.id) { value = vm.media(m) }
    val bmp = remember(file) { file?.let { f -> runCatching { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() } }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
    else androidx.compose.foundation.layout.Box(modifier.aspectRatio(1f))
}
