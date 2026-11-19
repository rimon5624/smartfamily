package com.rimon.smartfamily.ui.screens

import androidx.compose.ui.res.stringArrayResource
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rimon.smartfamily.R
import com.rimon.smartfamily.data.db.MealProfileEntity
import com.rimon.smartfamily.data.db.RecipeEntity
import com.rimon.smartfamily.smart.RecipeText
import com.rimon.smartfamily.ui.Idea
import com.rimon.smartfamily.ui.KitchenViewModel
import com.rimon.smartfamily.ui.components.HelpButton
import com.rimon.smartfamily.ui.components.SectionTitle
import kotlinx.coroutines.delay
import java.util.Calendar

private val kashrutOptions = listOf("meat" to R.string.k_meat, "dairy" to R.string.k_dairy, "parve" to R.string.k_parve)
private val mealTypeOptions = listOf(
    "breakfast" to R.string.mt_breakfast, "lunch" to R.string.mt_lunch, "dinner" to R.string.mt_dinner,
    "shabbat" to R.string.mt_shabbat, "holiday" to R.string.mt_holiday, "snack" to R.string.mt_snack,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    vm: KitchenViewModel,
    viewer: Viewer,
    startTab: Int,
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onEditRecipe: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val imported by vm.imported.collectAsStateWithLifecycle()
    LaunchedEffect(imported) { imported?.let { vm.clearImported(); onOpenRecipe(it.id) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kit_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = { HelpButton(R.string.help_kitchen) },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding())) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.kit_recipes)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.kit_week)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.kit_packed)) })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                0 -> RecipesTab(vm, viewer, onOpenRecipe, onEditRecipe)
                1 -> WeekPlanTab(vm, viewer, onOpenRecipe)
                else -> PackedTab(vm, viewer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipesTab(vm: KitchenViewModel, viewer: Viewer, onOpen: (String) -> Unit, onEdit: (String) -> Unit) {
    val recipes by remember(viewer.familyId) { vm.recipes(viewer.familyId) }.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf("") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var ideasDialog by remember { mutableStateOf(false) }
    val photo = rememberPhotoPicker { vm.importPhoto(viewer.familyId, it) }

    val shown = recipes.filter { r ->
        (query.isBlank() || r.title.contains(query, true) || r.ingredients.contains(query, true)) &&
            (meal.isEmpty() || r.mealTypes.split(',').contains(meal)) && (!favOnly || r.favorite)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.rec_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text(stringResource(R.string.rec_favorites)) })
                    mealTypeOptions.forEach { (k, res) -> FilterChip(selected = meal == k, onClick = { meal = if (meal == k) "" else k }, label = { Text(stringResource(res)) }) }
                }
            }
            if (recipes.isEmpty()) item { Text(stringResource(R.string.rec_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
            items(shown, key = { it.id }) { r ->
                Card(onClick = { onOpen(r.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium)
                            val k = kashrutOptions.firstOrNull { it.first == r.kashrut }?.second ?: R.string.k_parve
                            Text(
                                stringResource(k) + " · " + stringResource(R.string.rec_time, r.prepMinutes + r.cookMinutes) + " · " + stringResource(R.string.rec_servings, r.servings),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.favorite) Icon(Icons.Outlined.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (!viewer.isChild) {
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                FloatingActionButton(onClick = { menu = true }) { Icon(Icons.Outlined.Add, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rec_new)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit("new") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rec_from_link)) }, leadingIcon = { Icon(Icons.Outlined.Link, null) },
                        onClick = { menu = false; linkDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rec_from_photo)) }, leadingIcon = { Icon(Icons.Outlined.CameraAlt, null) },
                        onClick = { menu = false; photo() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.rec_ideas)) }, leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                        onClick = { menu = false; ideasDialog = true })
                }
            }
        }
    }
    if (linkDialog) {
        TextInputDialog(
            title = stringResource(R.string.rec_from_link), label = stringResource(R.string.rec_link_hint), initial = "",
            confirmText = stringResource(R.string.shop_add),
            onDismiss = { linkDialog = false }, onConfirm = { vm.importUrl(viewer.familyId, it); linkDialog = false },
        )
    }
    if (ideasDialog) IdeasDialog(vm, viewer) { ideasDialog = false }
}

/** Camera / gallery launcher for a single photo; returns a function that opens a choice menu. */
@Composable
fun rememberPhotoPicker(onPhoto: (android.net.Uri) -> Unit): () -> Unit {
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPhoto(uri) }
    return {
        gallery.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
            )
        )
    }
}

@Composable
private fun IdeasDialog(vm: KitchenViewModel, viewer: Viewer, onDismiss: () -> Unit) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val inventory by remember(viewer.familyId) { vm.inventory(viewer.familyId) }.collectAsState(initial = emptyList())
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    var meal by remember { mutableStateOf(if (hour < 11) "breakfast" else if (hour < 16) "lunch" else "dinner") }
    var kashrut by remember { mutableStateOf("parve") }
    var minutes by remember { mutableStateOf("45") }
    var extra by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.clearIdeas() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rec_ideas)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.rec_ideas_for), style = MaterialTheme.typography.labelLarge); ChipRow(mealTypeOptions, meal) { meal = it } }
                item { Text(stringResource(R.string.rec_kashrut), style = MaterialTheme.typography.labelLarge); ChipRow(kashrutOptions, kashrut) { kashrut = it } }
                item { NumberField(stringResource(R.string.rec_ideas_minutes), minutes) { minutes = it } }
                item { OutlinedTextField(extra, { extra = it }, label = { Text(stringResource(R.string.rec_ideas_extra)) }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Button(enabled = !busy, onClick = {
                        val soon = System.currentTimeMillis() + 3 * 86_400_000L
                        vm.suggest(
                            viewer.familyId, meal, kashrut, minutes.toIntOrNull() ?: 45, extra,
                            inventory.map { it.name }, inventory.filter { (it.expiresAt ?: Long.MAX_VALUE) < soon }.map { it.name },
                        )
                    }) { Text(stringResource(R.string.rec_ideas_go)) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                items(ideas) { idea -> IdeaCard(idea, canSave = idea.draft != null) { vm.saveIdea(viewer.familyId, idea); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun IdeaCard(idea: Idea, canSave: Boolean, onSave: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(idea.title, style = MaterialTheme.typography.titleSmall)
            if (idea.why.isNotBlank()) Text(idea.why, style = MaterialTheme.typography.bodySmall)
            if (canSave) TextButton(onClick = onSave) { Text(stringResource(R.string.rec_save_idea)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(vm: KitchenViewModel, viewer: Viewer, recipeId: String, onBack: () -> Unit, onEdit: () -> Unit, onCook: () -> Unit) {
    val r by remember(recipeId) { vm.recipe(recipeId) }.collectAsState(initial = null)
    val ratings by remember(recipeId) { vm.ratings(recipeId) }.collectAsState(initial = emptyList())
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var servings by remember(r?.id) { mutableIntStateOf(r?.servings ?: 4) }
    var ideasTitle by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val recipe = r ?: return
    val ingredients = vm.ingredientsOf(recipe)
    val steps = vm.stepsOf(recipe)
    val factor = servings.toDouble() / recipe.servings.coerceAtLeast(1)
    val mine = ratings.firstOrNull { it.userId == viewer.userId }?.stars ?: 0
    val avg = if (ratings.isEmpty()) 0.0 else ratings.map { it.stars }.average()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite(recipe) }) {
                        Icon(if (recipe.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, stringResource(R.string.rec_favorites))
                    }
                    CopyToFamilyButton(recipe.familyId, viewer.userId) { fid, fname -> vm.copyRecipeTo(recipe, fid, fname) }
                    if (!viewer.isChild) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.rec_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.rec_delete)) }
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding(), bottom = 32.dp)) {
            item {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                val k = kashrutOptions.firstOrNull { it.first == recipe.kashrut }?.second ?: R.string.k_parve
                Text(
                    stringResource(k) + " · " + stringResource(R.string.rec_time, recipe.prepMinutes + recipe.cookMinutes) +
                        (if (recipe.timesCooked > 0) " · " + stringResource(R.string.rec_cooked_n, recipe.timesCooked) else "") +
                        (if (avg > 0) " · ★ %.1f".format(avg) else ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.rec_rate), Modifier.weight(1f))
                    (1..5).forEach { n ->
                        IconButton(onClick = { vm.rate(recipe, n) }, modifier = Modifier.size(36.dp)) {
                            Icon(if (n <= mine) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.rec_servings, servings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (servings > 1) servings-- }) { Icon(Icons.Outlined.Remove, null) }
                    IconButton(onClick = { servings++ }) { Icon(Icons.Outlined.Add, null) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = onCook, enabled = steps.isNotEmpty()) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.rec_cook)) }
                    FilledTonalButton(onClick = { vm.addMissing(viewer.familyId, ingredients, factor) }) {
                        Icon(Icons.Outlined.AddShoppingCart, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.rec_add_missing))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = { vm.markCooked(recipe.id) }) { Text(stringResource(R.string.rec_cooked)) }
                    OutlinedButton(onClick = { vm.clearIdeas(); ideasTitle = ""; vm.completeMeal(recipe) }) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.rec_complete_meal))
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.rec_ingredients)) }
            item { Text(stringResource(R.string.rec_tap_ingredient), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ingredients) { ing ->
                Text(
                    "• " + RecipeText.ingredientLine(ing, factor),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { vm.clearIdeas(); ideasTitle = ing.name; vm.substitute(recipe, ing.name) }.padding(vertical = 6.dp),
                )
            }
            item { SectionTitle(stringResource(R.string.rec_steps)) }
            items(steps.withIndex().toList()) { (i, s) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (recipe.notes.isNotBlank()) {
                item { SectionTitle(stringResource(R.string.rec_notes)); Text(recipe.notes) }
            }
            if (recipe.source.isNotBlank() && recipe.source != "AI") {
                item { SectionTitle(stringResource(R.string.rec_source)); Text(recipe.source, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    ideasTitle?.let { t ->
        AlertDialog(
            onDismissRequest = { ideasTitle = null },
            title = { Text(if (t.isEmpty()) stringResource(R.string.rec_complete_meal) else stringResource(R.string.rec_substitute_for, t)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    items(ideas) { IdeaCard(it, canSave = false) {} }
                }
            },
            confirmButton = { TextButton(onClick = { ideasTitle = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.rec_delete)) },
            text = { Text(recipe.title) },
            confirmButton = { TextButton(onClick = { vm.delete(recipe.id); confirmDelete = false; onBack() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditScreen(vm: KitchenViewModel, viewer: Viewer, recipeId: String, onDone: () -> Unit) {
    val existing by remember(recipeId) { vm.recipe(recipeId) }.collectAsState(initial = null)
    val base = remember(existing) { existing ?: vm.newRecipe(viewer.familyId, viewer.userId) }
    var loaded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var kashrut by remember { mutableStateOf("parve") }
    var meals by remember { mutableStateOf(setOf<String>()) }
    var servings by remember { mutableStateOf("4") }
    var prep by remember { mutableStateOf("0") }
    var cook by remember { mutableStateOf("0") }
    var ingredients by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(existing) {
        val r = existing
        if (!loaded && (r != null || recipeId == "new")) {
            if (r != null) {
                title = r.title; kashrut = r.kashrut; meals = r.mealTypes.split(',').filter { it.isNotBlank() }.toSet()
                servings = r.servings.toString(); prep = r.prepMinutes.toString(); cook = r.cookMinutes.toString()
                ingredients = vm.ingredientsOf(r).joinToString("\n") { RecipeText.ingredientLine(it) }
                steps = vm.stepsOf(r).joinToString("\n"); notes = r.notes
            }
            loaded = true
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (recipeId == "new") R.string.rec_new else R.string.rec_edit)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(enabled = title.isNotBlank(), onClick = {
                        vm.save(
                            base.copy(
                                title = title.trim(), kashrut = kashrut, mealTypes = meals.joinToString(","),
                                servings = servings.toIntOrNull()?.coerceAtLeast(1) ?: 4,
                                prepMinutes = prep.toIntOrNull() ?: 0, cookMinutes = cook.toIntOrNull() ?: 0,
                                ingredients = RecipeText.ingredientsToJson(RecipeText.parseIngredientLines(ingredients)),
                                steps = RecipeText.stepsToJson(steps.lines().map { it.trim() }.filter { it.isNotEmpty() }),
                                notes = notes,
                            )
                        )
                        onDone()
                    }) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(top = inner.calculateTopPadding()).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.rec_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.rec_kashrut), style = MaterialTheme.typography.labelLarge)
            ChipRow(kashrutOptions, kashrut) { kashrut = it }
            Text(stringResource(R.string.rec_meal_types), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mealTypeOptions.forEach { (k, res) ->
                    FilterChip(selected = k in meals, onClick = { meals = if (k in meals) meals - k else meals + k }, label = { Text(stringResource(res)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { NumberField(stringResource(R.string.rec_servings_label), servings) { servings = it } }
                Box(Modifier.weight(1f)) { NumberField(stringResource(R.string.rec_prep), prep) { prep = it } }
                Box(Modifier.weight(1f)) { NumberField(stringResource(R.string.rec_cook_min), cook) { cook = it } }
            }
            OutlinedTextField(ingredients, { ingredients = it }, label = { Text(stringResource(R.string.rec_ingredients)) },
                placeholder = { Text(stringResource(R.string.rec_ingredients_hint)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(steps, { steps = it }, label = { Text(stringResource(R.string.rec_steps)) },
                placeholder = { Text(stringResource(R.string.rec_steps_hint)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.rec_notes)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** RCP-13/14: full-screen steps, screen kept on, timers found in each step. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CookingScreen(vm: KitchenViewModel, recipeId: String, onBack: () -> Unit) {
    val r by remember(recipeId) { vm.recipe(recipeId) }.collectAsState(initial = null)
    val recipe = r ?: return
    val steps = vm.stepsOf(recipe)
    var index by rememberSaveable { mutableIntStateOf(0) }
    val timers = remember { mutableStateListOf<Pair<String, Long>>() } // label to end time
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    var listening by rememberSaveable { mutableStateOf(false) }
    var converter by remember { mutableStateOf(false) }
    val micOk = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> listening = ok }
    // RCP-13: hands-free – say "next" / "back" (הבא / חזור) while cooking
    VoiceCommands(listening, onNext = { index = (index + 1).coerceAtMost(steps.lastIndex.coerceAtLeast(0)) }, onBack = { index = (index - 1).coerceAtLeast(0) })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = {
                        if (listening) listening = false
                        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listening = true
                        else micOk.launch(android.Manifest.permission.RECORD_AUDIO)
                    }) { Text(if (listening) "🎙️ " + stringResource(R.string.cook_listening) else "🎙️ " + stringResource(R.string.cook_voice)) }
                    TextButton(onClick = { converter = true }) { Text("⚖️") }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(top = inner.calculateTopPadding()).padding(20.dp).fillMaxSize()) {
            if (steps.isEmpty()) return@Column
            val step = steps[index.coerceIn(0, steps.lastIndex)]
            LinearProgressIndicator(progress = { (index + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.rec_step_of, index + 1, steps.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Text(step, fontSize = 26.sp, lineHeight = 36.sp, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
            val found = RecipeText.timersIn(step)
            if (found.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    found.forEach { m ->
                        AssistChip(
                            onClick = {
                                val label = recipe.title + " · " + (index + 1)
                                timers += label to (System.currentTimeMillis() + m * 60_000L)
                                // Also set the phone's own clock timer, so it rings even if the app is closed.
                                runCatching {
                                    context.startActivity(
                                        Intent(AlarmClock.ACTION_SET_TIMER)
                                            .putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
                                            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
                                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                            label = { Text(stringResource(R.string.rec_timer, m)) },
                        )
                    }
                }
            }
            timers.forEach { (label, end) ->
                val left = ((end - now) / 1000).coerceAtLeast(0)
                Text(
                    if (left == 0L) stringResource(R.string.rec_timer_done, label)
                    else label + " · " + stringResource(R.string.rec_timer_running, "%d:%02d".format(left / 60, left % 60)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (left == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.weight(1f).height(56.dp)) { Text(stringResource(R.string.rec_prev)) }
                if (index < steps.lastIndex) {
                    Button(onClick = { index++ }, modifier = Modifier.weight(2f).height(56.dp)) { Text(stringResource(R.string.rec_next), fontSize = 18.sp) }
                } else {
                    Button(onClick = { vm.markCooked(recipe.id); onBack() }, modifier = Modifier.weight(2f).height(56.dp)) { Text(stringResource(R.string.rec_finish_cooking), fontSize = 18.sp) }
                }
            }
        }
    }
    if (converter) UnitConverterDialog(onDismiss = { converter = false })
}

private val slotsWeekday = listOf("breakfast" to R.string.slot_breakfast, "lunch" to R.string.slot_lunch, "dinner" to R.string.slot_dinner)
private val slotsFriday = listOf("breakfast" to R.string.slot_breakfast, "lunch" to R.string.slot_lunch, "shabbat_night" to R.string.slot_shabbat_night)
private val slotsShabbat = listOf("shabbat_lunch" to R.string.slot_shabbat_lunch, "seuda_shlishit" to R.string.slot_seuda)

@Composable
private fun WeekPlanTab(vm: KitchenViewModel, viewer: Viewer, onOpenRecipe: (String) -> Unit) {
    var weekStart by rememberSaveable {
        mutableLongStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0)
        }.timeInMillis)
    }
    val from = vm.ymd.format(weekStart)
    val to = vm.ymd.format(weekStart + 6 * 86_400_000L)
    val plan by remember(viewer.familyId, from) { vm.plan(viewer.familyId, from, to) }.collectAsState(initial = emptyList())
    val recipes by remember(viewer.familyId) { vm.recipes(viewer.familyId) }.collectAsState(initial = emptyList())
    var picking by remember { mutableStateOf<Pair<String, String>?>(null) } // date to slot
    val dayFmt = java.text.SimpleDateFormat("EEEE d/M", java.util.Locale.getDefault())

    LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weekStart -= 7 * 86_400_000L }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null) }
                Text("$from – $to", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { weekStart += 7 * 86_400_000L }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
            }
            if (!viewer.isChild) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    OutlinedButton(onClick = { vm.copyPreviousWeek(viewer.familyId, weekStart) }) { Text(stringResource(R.string.plan_copy_week)) }
                    FilledTonalButton(onClick = { vm.addWeekToList(viewer.familyId, from, to) }) {
                        Icon(Icons.Outlined.AddShoppingCart, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.plan_to_list))
                    }
                }
            }
        }
        (0 until 7).forEach { i ->
            val dayMs = weekStart + i * 86_400_000L
            val date = vm.ymd.format(dayMs)
            val slots = when (i) { 5 -> slotsFriday; 6 -> slotsShabbat; else -> slotsWeekday }
            item(key = "day-$date") {
                SectionTitle(dayFmt.format(java.util.Date(dayMs)) + " · " + com.rimon.smartfamily.smart.Zmanim.hebrewDate(java.util.Date(dayMs)))
                slots.forEach { (slot, res) ->
                    val entry = plan.firstOrNull { it.date == date && it.slot == slot }
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (entry?.recipeId != null) onOpenRecipe(entry.recipeId) else if (!viewer.isChild) picking = date to slot
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(res), Modifier.weight(0.35f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry?.title?.ifBlank { null } ?: stringResource(R.string.plan_empty_slot), Modifier.weight(0.65f), style = MaterialTheme.typography.bodyLarge)
                        if (entry != null && !viewer.isChild) {
                            IconButton(onClick = { picking = date to slot }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) }
                        }
                    }
                }
            }
        }
    }
    picking?.let { (date, slot) ->
        var text by remember { mutableStateOf("") }
        var query by remember { mutableStateOf("") }
        val existing = plan.firstOrNull { it.date == date && it.slot == slot }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(R.string.plan_pick)) },
            text = {
                LazyColumn {
                    item { OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.rec_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    items(recipes.filter { query.isBlank() || it.title.contains(query, true) }.take(30), key = { it.id }) { r ->
                        TextButton(onClick = { vm.setPlan(viewer.familyId, date, slot, r, r.title, r.servings); picking = null }) { Text(r.title) }
                    }
                    item {
                        OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.plan_free_text)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { vm.setPlan(viewer.familyId, date, slot, null, text.trim(), 4); picking = null }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Row {
                    if (existing != null) TextButton(onClick = { vm.clearPlan(existing.id); picking = null }) { Text(stringResource(R.string.plan_clear)) }
                    TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
}

private val places = listOf(
    "kindergarten" to R.string.pl_kindergarten, "school" to R.string.pl_school, "army" to R.string.pl_army,
    "work" to R.string.pl_work, "trip" to R.string.pl_trip,
)
private val weekdayShort = listOf(R.string.wd_sun, R.string.wd_mon, R.string.wd_tue, R.string.wd_wed, R.string.wd_thu, R.string.wd_fri, R.string.wd_sat)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackedTab(vm: KitchenViewModel, viewer: Viewer) {
    val profiles by remember(viewer.familyId) { vm.profiles(viewer.familyId) }.collectAsState(initial = emptyList())
    val members by remember(viewer.familyId) { vm.members(viewer.familyId) }.collectAsState(initial = emptyList())
    val from = remember { vm.ymd.format(System.currentTimeMillis()) }
    val to = remember { vm.ymd.format(System.currentTimeMillis() + 7 * 86_400_000L) }
    val packed by remember(viewer.familyId) { vm.packed(viewer.familyId, from, to) }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<MealProfileEntity?>(null) }
    val dayFmt = java.text.SimpleDateFormat("EEE d/M", java.util.Locale.getDefault())
    val tripDays by remember(viewer.familyId) { vm.tripDays(viewer.familyId) }.collectAsState(initial = emptyMap())
    var choosing by remember { mutableStateOf<Pair<MealProfileEntity, com.rimon.smartfamily.data.db.PackedMealEntity>?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (profiles.isEmpty()) item { Text(stringResource(R.string.pck_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(profiles, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.personName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(places.firstOrNull { it.first == p.place }?.second ?: R.string.pl_school), style = MaterialTheme.typography.bodySmall)
                            }
                            if (!viewer.isChild) IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, stringResource(R.string.pck_edit)) }
                        }
                        packed.filter { it.profileId == p.id }.sortedBy { it.date }.forEach { m ->
                            val d = vm.ymd.parse(m.date)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp).clickable { choosing = p to m }) {
                                Text(d?.let { dayFmt.format(it) } ?: m.date, Modifier.weight(0.3f), style = MaterialTheme.typography.labelLarge)
                                Text(m.items.lines().joinToString(", "), Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Outlined.Edit, stringResource(R.string.pck_choose), Modifier.size(16.dp))
                            }
                            tripDays[m.date]?.let { trip ->
                                Text(stringResource(R.string.pck_trip_day, trip), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("PACKED" to R.string.pck_packed, "EATEN" to R.string.pck_eaten, "UNEATEN" to R.string.pck_uneaten).forEach { (st, res) ->
                                    FilterChip(selected = m.status == st, onClick = { vm.savePacked(m.copy(status = st)) }, label = { Text(stringResource(res)) })
                                }
                            }
                        }
                        if (!viewer.isChild) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                FilledTonalButton(onClick = { vm.planWeek(p) }) { Text(stringResource(R.string.pck_plan)) }
                                OutlinedButton(onClick = { vm.planWeekAi(p) }) {
                                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.pck_plan_ai))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!viewer.isChild) {
            FloatingActionButton(onClick = { editing = vm.newProfile(viewer.familyId) }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Outlined.Add, stringResource(R.string.pck_new))
            }
        }
    }
    // PCK-5: the child picks from what they like (up to four things); PCK-8 adds trip-day extras.
    choosing?.let { (p, m) ->
        val likes = remember(p) { (p.likes + "\n" + p.rules).split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() && it.length < 40 }.distinct() }
        val extras = if (tripDays[m.date] != null) stringArrayResource(R.array.pck_trip_extras).toList() else emptyList()
        var picked by remember(m) { mutableStateOf(m.items.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()) }
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.pck_choose_title, p.personName)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.pck_choose_sub), style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (picked + likes + extras).distinct().forEach { o ->
                            FilterChip(selected = o in picked, onClick = { picked = if (o in picked) picked - o else picked + o }, label = { Text(o) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.savePacked(m.copy(items = picked.joinToString("\n"), feedback = if (viewer.isChild) "chosen" else m.feedback)); choosing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    editing?.let { p ->
        var name by remember { mutableStateOf(p.personName) }
        var personId by remember { mutableStateOf(p.personId) }
        var place by remember { mutableStateOf(p.place) }
        var rules by remember { mutableStateOf(p.rules) }
        var likes by remember { mutableStateOf(p.likes) }
        var dislikes by remember { mutableStateOf(p.dislikes) }
        var kashrut by remember { mutableStateOf(p.kashrut) }
        var days by remember { mutableIntStateOf(p.weekdays) }
        var maxRepeat by remember { mutableStateOf(p.maxRepeat.toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (p.personName.isEmpty()) R.string.pck_new else R.string.pck_edit)) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            members.filter { it.status == "ACTIVE" }.forEach { m ->
                                FilterChip(selected = personId == m.userId, onClick = { personId = m.userId; name = m.displayName }, label = { Text(m.displayName) })
                            }
                        }
                    }
                    item { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.pck_person)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Text(stringResource(R.string.pck_place), style = MaterialTheme.typography.labelLarge); ChipRow(places, place) { place = it } }
                    item { Text(stringResource(R.string.rec_kashrut), style = MaterialTheme.typography.labelLarge); ChipRow(kashrutOptions, kashrut) { kashrut = it } }
                    item {
                        Text(stringResource(R.string.pck_days), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            weekdayShort.forEachIndexed { i, res ->
                                val bit = 1 shl i
                                FilterChip(selected = days and bit != 0, onClick = { days = days xor bit }, label = { Text(stringResource(res)) })
                            }
                        }
                    }
                    item { OutlinedTextField(rules, { rules = it }, label = { Text(stringResource(R.string.pck_rules)) }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(likes, { likes = it }, label = { Text(stringResource(R.string.pck_likes)) }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(dislikes, { dislikes = it }, label = { Text(stringResource(R.string.pck_dislikes)) }, modifier = Modifier.fillMaxWidth()) }
                    item { NumberField(stringResource(R.string.pck_max_repeat), maxRepeat) { maxRepeat = it } }
                    if (p.personName.isNotEmpty()) item {
                        TextButton(onClick = { vm.deleteProfile(p.id); editing = null }) { Text(stringResource(R.string.pck_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveProfile(p.copy(personName = name.trim(), personId = personId, place = place, rules = rules, likes = likes,
                        dislikes = dislikes, kashrut = kashrut, weekdays = days, maxRepeat = maxRepeat.toIntOrNull()?.coerceAtLeast(1) ?: 2))
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Suppress("unused")
private fun RecipeEntity.totalMinutes() = prepMinutes + cookMinutes


/** Listens continuously for "next" / "back" while the cooking screen is open (RCP-13). */
@Composable
private fun VoiceCommands(active: Boolean, onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val next by androidx.compose.runtime.rememberUpdatedState(onNext)
    val back by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(active) {
        if (!active || !android.speech.SpeechRecognizer.isRecognitionAvailable(context)) return@DisposableEffect onDispose { }
        val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        val lang = context.resources.configuration.locales[0].language.let { if (it == "iw" || it == "he") "he-IL" else "en-US" }
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, lang)
        var alive = true
        fun listen() { if (alive) runCatching { sr.startListening(intent) } }
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                val words = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().joinToString(" ").lowercase()
                when {
                    listOf("הבא", "הלאה", "next", "forward").any { it in words } -> next()
                    listOf("חזור", "אחורה", "קודם", "back", "previous").any { it in words } -> back()
                }
                listen()
            }
            override fun onError(error: Int) { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ listen() }, 600) }
            override fun onReadyForSpeech(p: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: android.os.Bundle?) {}
            override fun onEvent(t: Int, p: android.os.Bundle?) {}
        })
        listen()
        onDispose { alive = false; runCatching { sr.destroy() } }
    }
}

/** RCP-15: cups, spoons and grams, and oven temperatures. */
@Composable
fun UnitConverterDialog(onDismiss: () -> Unit) {
    val items = listOf("flour" to 125.0, "sugar" to 200.0, "butter" to 227.0, "rice" to 185.0, "oats" to 90.0, "cocoa" to 100.0, "water" to 240.0, "oil" to 220.0, "honey" to 340.0)
    var what by rememberSaveable { mutableStateOf("flour") }
    var amount by rememberSaveable { mutableStateOf("1") }
    var temp by rememberSaveable { mutableStateOf("180") }
    val a = amount.toDoubleOrNull() ?: 0.0
    val perCup = items.first { it.first == what }.second
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.cook_converter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceIn(null, items.map { it.first to stringResource(when (it.first) {
                    "flour" -> R.string.cv_flour; "sugar" -> R.string.cv_sugar; "butter" -> R.string.cv_butter; "rice" -> R.string.cv_rice; "oats" -> R.string.cv_oats
                    "cocoa" -> R.string.cv_cocoa; "water" -> R.string.cv_water; "oil" -> R.string.cv_oil; else -> R.string.cv_honey }) }, what) { what = it }
                NumIn(stringResource(R.string.cv_cups), amount) { amount = it }
                Text(stringResource(R.string.cv_result, String.format("%.0f", a * perCup), String.format("%.0f", a * 16), String.format("%.0f", a * 48)), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                NumIn(stringResource(R.string.cv_oven_c), temp) { temp = it }
                val c = temp.toDoubleOrNull() ?: 0.0
                Text(stringResource(R.string.cv_oven_result, String.format("%.0f", c * 9 / 5 + 32), (((c - 121) / 14).toInt() + 1).coerceIn(1, 9)), style = MaterialTheme.typography.titleMedium)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } })
}
