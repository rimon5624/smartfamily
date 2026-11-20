package com.rimon.smartfamily.ui.screens

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.TreePersonEntity

/** Positions (in dp, top-left of each card) of the tree layout, plus the lines to draw. */
internal data class TreeLayout(val pos: Map<String, Pair<Float, Float>>, val couples: List<Pair<String, String>>, val links: List<Pair<List<String>, String>>, val width: Float, val height: Float)

internal object TreeGeometry {
    const val CARD_W = 128f; const val CARD_H = 60f; const val COUPLE_GAP = 14f; const val SIB_GAP = 28f; const val LEVEL_H = 120f; const val MARGIN = 24f

    /**
     * A simple tidy layout: couples sit side by side, children are centred under their parents, every person appears once.
     * A person who married into the family is drawn next to their spouse, with lines to their own parents too.
     */
    fun layout(people: List<TreePersonEntity>): TreeLayout {
        val byId = people.associateBy { it.id }
        fun parentsOf(p: TreePersonEntity) = p.parents.split(',').filter { it.isNotBlank() && byId.containsKey(it) }
        fun spouseOf(p: TreePersonEntity) = byId[p.spouse]?.takeIf { it.id != p.id }
        val placed = mutableSetOf<String>()
        val pos = mutableMapOf<String, Pair<Float, Float>>()
        val couples = mutableListOf<Pair<String, String>>()
        // a unit is a person with (maybe) their spouse; its children are the people whose parents include either
        fun unitOf(p: TreePersonEntity): List<TreePersonEntity> = listOfNotNull(p, spouseOf(p)?.takeIf { it.id !in placed && it.id != p.id }).distinctBy { it.id }
        fun childrenOf(unit: List<TreePersonEntity>): List<TreePersonEntity> {
            val ids = unit.map { it.id }.toSet()
            return people.filter { c -> parentsOf(c).any { it in ids } }.sortedBy { it.birth.ifBlank { "9999" } }
        }
        val widthCache = mutableMapOf<String, Float>()
        fun unitWidth(unit: List<TreePersonEntity>) = unit.size * CARD_W + (unit.size - 1) * COUPLE_GAP
        fun subtreeWidth(p: TreePersonEntity, seen: MutableSet<String>): Float {
            widthCache[p.id]?.let { return it }
            val unit = listOfNotNull(p, spouseOf(p)).distinctBy { it.id }.filter { it.id !in seen }
            seen += unit.map { it.id }
            val kids = childrenOf(unit).filter { it.id !in seen }
            val kidsW = kids.sumOf { subtreeWidth(it, seen).toDouble() }.toFloat() + (kids.size - 1).coerceAtLeast(0) * SIB_GAP
            return maxOf(unitWidth(unit), kidsW).also { widthCache[p.id] = it }
        }
        var maxDepth = 0
        fun place(p: TreePersonEntity, left: Float, depth: Int) {
            if (p.id in placed) return
            val unit = unitOf(p)
            unit.forEach { placed += it.id }
            maxDepth = maxOf(maxDepth, depth)
            val kids = childrenOf(unit).filter { it.id !in placed }
            val w = subtreeWidth(p, mutableSetOf())
            val y = MARGIN + depth * LEVEL_H
            val uw = unitWidth(unit)
            var x = left + (w - uw) / 2
            unit.forEach { m -> pos[m.id] = x to y; x += CARD_W + COUPLE_GAP }
            if (unit.size == 2) couples += unit[0].id to unit[1].id
            val kidsW = kids.sumOf { subtreeWidth(it, mutableSetOf()).toDouble() }.toFloat() + (kids.size - 1).coerceAtLeast(0) * SIB_GAP
            var cx = left + (w - kidsW) / 2
            kids.forEach { k -> val kw = subtreeWidth(k, mutableSetOf()); place(k, cx, depth + 1); cx += kw + SIB_GAP }
        }
        // roots: people with no parents in the tree whose spouse also has none (so in-laws stay with their spouse)
        val roots = people.filter { p -> parentsOf(p).isEmpty() && (spouseOf(p)?.let { parentsOf(it).isEmpty() } ?: true) }
            .sortedBy { it.birth.ifBlank { "9999" } }
        var left = MARGIN
        (roots + people).forEach { r -> if (r.id !in placed) { val w = subtreeWidth(r, mutableSetOf()); place(r, left, 0); left += w + SIB_GAP * 2 } }
        // parent → child links (every parent, also when the child sits in another branch)
        val links = people.mapNotNull { c -> parentsOf(c).takeIf { it.isNotEmpty() }?.let { it to c.id } }
        val width = (pos.values.maxOfOrNull { it.first } ?: 0f) + CARD_W + MARGIN
        val height = (pos.values.maxOfOrNull { it.second } ?: 0f) + CARD_H + MARGIN
        return TreeLayout(pos, couples, links, width, height)
    }
}

/**
 * 2.2 graphic family tree: cards joined by lines (couples side by side, children below), pinch to zoom, drag in any
 * direction, "fit" and "find me". Tap a card to edit; long-press to add a parent, a spouse or a child.
 */
@Composable
fun FamilyTreeGraph(
    people: List<TreePersonEntity>, me: String, canEdit: Boolean,
    onOpen: (TreePersonEntity) -> Unit, onAdd: (TreePersonEntity, String) -> Unit,
) {
    val lay = remember(people) { TreeGeometry.layout(people) }
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var scale by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val lineColor = MaterialTheme.colorScheme.outline
    val heartColor = MaterialTheme.colorScheme.error
    // The tree is laid out with absolute coordinates (mirrored by hand for Hebrew), so the canvas runs left-to-right.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(520.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(16.dp)).testTag("tree_graph")) {
        val viewW = with(density) { maxWidth.toPx() }; val viewH = with(density) { maxHeight.toPx() }
        val contentW = with(density) { lay.width.dp.toPx() }; val contentH = with(density) { lay.height.dp.toPx() }
        fun fit() {
            scale = minOf(viewW / contentW, (viewH - 60f) / contentH, 1.4f).coerceAtLeast(0.25f)
            tx = (viewW - contentW * scale) / 2f; ty = 8f
        }
        fun findMe() {
            val p = people.firstOrNull { it.memberId == me } ?: return
            val (x, y) = lay.pos[p.id] ?: return
            scale = 1.1f
            val px = with(density) { (x + TreeGeometry.CARD_W / 2).dp.toPx() }; val py = with(density) { (y + TreeGeometry.CARD_H / 2).dp.toPx() }
            val vx = if (rtl) contentW - px else px
            tx = viewW / 2 - vx * scale; ty = viewH / 2 - py * scale
        }
        LaunchedEffect(lay) { fit() }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(0.25f, 3f)
                    tx = centroid.x - (centroid.x - tx) * (ns / scale) + pan.x
                    ty = centroid.y - (centroid.y - ty) * (ns / scale) + pan.y
                    scale = ns
                }
            },
        ) {
            // unbounded: the tree keeps its full size even when it is wider than the screen (the canvas is mirrored by its width)
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(lay.width.dp, lay.height.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f); scaleX = scale; scaleY = scale; translationX = tx; translationY = ty
            }) {
                Canvas(Modifier.requiredSize(lay.width.dp, lay.height.dp)) {
                    fun pt(id: String, dx: Float, dy: Float): Offset? = lay.pos[id]?.let { (x, y) ->
                        val px = (x + dx).dp.toPx(); Offset(if (rtl) size.width - px else px, (y + dy).dp.toPx())
                    }
                    val sw = 2.dp.toPx()
                    lay.couples.forEach { (a, b) ->
                        val pa = pt(a, TreeGeometry.CARD_W / 2, TreeGeometry.CARD_H / 2); val pb = pt(b, TreeGeometry.CARD_W / 2, TreeGeometry.CARD_H / 2)
                        if (pa != null && pb != null) drawLine(heartColor, pa, pb, sw, StrokeCap.Round)
                    }
                    lay.links.forEach { (parents, child) ->
                        val tops = parents.mapNotNull { pt(it, TreeGeometry.CARD_W / 2, TreeGeometry.CARD_H) }
                        val c = pt(child, TreeGeometry.CARD_W / 2, 0f) ?: return@forEach
                        if (tops.isEmpty()) return@forEach
                        val from = Offset(tops.map { it.x }.average().toFloat(), tops.maxOf { it.y } + (if (tops.size > 1) 0f else 0f))
                        val midY = (from.y + c.y) / 2
                        drawLine(lineColor, from, Offset(from.x, midY), sw)
                        drawLine(lineColor, Offset(from.x, midY), Offset(c.x, midY), sw)
                        drawLine(lineColor, Offset(c.x, midY), c, sw)
                    }
                }
                people.forEach { p ->
                    val (x, y) = lay.pos[p.id] ?: return@forEach
                    val left = if (rtl) lay.width - x - TreeGeometry.CARD_W else x
                    Box(Modifier.offset(left.dp, y.dp)) {
                        Column(
                            Modifier.size(TreeGeometry.CARD_W.dp, TreeGeometry.CARD_H.dp)
                                .background(if (p.memberId == me && me.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .pointerInput(p.id) { detectTapGestures(onTap = { if (canEdit) onOpen(p) }, onLongPress = { if (canEdit) menuFor = p.id }) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                            val life = listOfNotNull(p.birth.takeIf { it.isNotBlank() }?.take(4), p.death.takeIf { it.isNotBlank() }?.take(4)?.let { "– $it ז״ל" }).joinToString(" ")
                            if (life.isNotBlank()) Text(life, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        DropdownMenu(menuFor == p.id, { menuFor = null }) {
                            listOf("parent" to R.string.tree_add_parent, "spouse" to R.string.tree_add_spouse, "child" to R.string.tree_add_child).forEach { (k, l) ->
                                DropdownMenuItem(text = { Text(stringResource(l)) }, onClick = { menuFor = null; onAdd(p, k) })
                            }
                        }
                    }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { fit() }) { Text(stringResource(R.string.tree_fit)) }
                if (people.any { it.memberId == me && me.isNotBlank() }) OutlinedButton(onClick = { findMe() }) { Text(stringResource(R.string.tree_find_me)) }
            }
        }
    }
    }
}
