package com.rimon.smartfamily

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rimon.smartfamily.model.AppModule
import com.rimon.smartfamily.model.Role
import com.rimon.smartfamily.ui.AppViewModel
import com.rimon.smartfamily.ui.HomeLifeViewModel
import com.rimon.smartfamily.ui.ApplianceViewModel
import com.rimon.smartfamily.ui.KitchenViewModel
import com.rimon.smartfamily.ui.screens.ApplianceDetailScreen
import com.rimon.smartfamily.ui.screens.AppliancesScreen
import com.rimon.smartfamily.ui.screens.SmartHomeScreen
import com.rimon.smartfamily.ui.screens.CookingScreen
import com.rimon.smartfamily.ui.screens.KitchenScreen
import com.rimon.smartfamily.ui.screens.RecipeDetailScreen
import com.rimon.smartfamily.ui.screens.RecipeEditScreen
import com.rimon.smartfamily.ui.ShoppingViewModel
import com.rimon.smartfamily.ui.screens.CalendarScreen
import com.rimon.smartfamily.ui.screens.ChoresScreen
import com.rimon.smartfamily.ui.screens.AiSettingsScreen
import com.rimon.smartfamily.ui.screens.InventoryScreen
import com.rimon.smartfamily.ui.screens.ShoppingListScreen
import com.rimon.smartfamily.ui.screens.ShoppingListsScreen
import com.rimon.smartfamily.ui.screens.Viewer
import kotlinx.coroutines.flow.merge
import com.rimon.smartfamily.ui.UiMessage
import com.rimon.smartfamily.ui.screens.BackupScreen
import com.rimon.smartfamily.ui.screens.CloudScreen
import com.rimon.smartfamily.ui.screens.FamiliesScreen
import com.rimon.smartfamily.ui.screens.FamilyDetailScreen
import com.rimon.smartfamily.ui.screens.HomeScreen
import com.rimon.smartfamily.ui.screens.ModulesScreen
import com.rimon.smartfamily.ui.screens.OnboardingScreen
import com.rimon.smartfamily.ui.screens.SettingsScreen
import com.rimon.smartfamily.ui.theme.SmartFamilyTheme

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private val locked = androidx.compose.runtime.mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val s = vm.settings.first { it != null }!!
            locked.value = com.rimon.smartfamily.security.AppLock.needsUnlock(s.appLock, s.lockMinutes) &&
                com.rimon.smartfamily.security.AppLock.canAuthenticate(this@MainActivity)
            if (locked.value) unlock()
        }
    }

    override fun onStop() {
        super.onStop()
        com.rimon.smartfamily.security.AppLock.onBackground()
    }

    private fun unlock() = com.rimon.smartfamily.security.AppLock.prompt(this, getString(R.string.lock_title)) { ok -> if (ok) locked.value = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings
            SmartFamilyTheme(
                themeMode = s?.themeMode ?: com.rimon.smartfamily.data.prefs.ThemeMode.SYSTEM,
                dynamicColor = s?.dynamicColor == true,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var crash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(CrashLog.take(this@MainActivity)) }
                    crash?.let { report ->
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { crash = null },
                            title = { Text(androidx.compose.ui.res.stringResource(R.string.crash_title)) },
                            text = {
                                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.crash_body))
                                    Text(report.take(1500), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(report)); crash = null }) {
                                    Text(androidx.compose.ui.res.stringResource(R.string.crash_copy))
                                }
                            },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = { crash = null }) { Text(androidx.compose.ui.res.stringResource(R.string.cancel)) } },
                        )
                    }
                    // Screenshots and the recent-apps preview are blocked when the user asks for it.
                    LaunchedEffect(s?.secureScreens) {
                        if (s?.secureScreens == true) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    when {
                        s == null -> Box(Modifier.fillMaxSize()) // settings are loading (a few ms)
                        locked.value -> com.rimon.smartfamily.ui.screens.LockScreen(onUnlock = { unlock() })
                        !s.onboardingDone -> OnboardingScreen(vm)
                        else -> {
                            MainNav(vm)
                            com.rimon.smartfamily.ui.screens.WhatsNewAndUpdates(vm, s)
                        }
                    }
                }
            }
        }
    }
}

private enum class Tab(val route: String, val label: Int, val on: ImageVector, val off: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    FAMILIES("families", R.string.nav_families, Icons.Filled.Groups, Icons.Outlined.Groups),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
private fun MainNav(vm: AppViewModel) {
    val shopVm: ShoppingViewModel = viewModel()
    val lifeVm: HomeLifeViewModel = viewModel()
    val kitchenVm: KitchenViewModel = viewModel()
    val applianceVm: ApplianceViewModel = viewModel()
    val chatVm: com.rimon.smartfamily.ui.ChatViewModel = viewModel()
    val extrasVm: com.rimon.smartfamily.ui.ExtrasViewModel = viewModel()
    val priceVm: com.rimon.smartfamily.ui.PriceViewModel = viewModel()
    val shareVm: com.rimon.smartfamily.ui.screens.ShareViewModel = viewModel()
    val assistantVm: com.rimon.smartfamily.ui.screens.AssistantViewModel = viewModel()
    val recordsVm: com.rimon.smartfamily.ui.RecordsViewModel = viewModel()
    var quickAdd by remember { mutableStateOf(false) }
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val family by vm.activeFamily.collectAsStateWithLifecycle()
    val uid = settings?.userId.orEmpty()
    val famId = family?.id.orEmpty()
    val me by remember(famId, uid) { vm.myMembership(famId, uid) }.collectAsState(initial = null)
    val modules by remember(famId, uid) { vm.modules(famId, uid) }.collectAsState(initial = emptyList())
    val role = me?.let { Role.parse(it.role) }
    // 0.9: a family that only shares with us: editable only where it allowed editing.
    val sharedIn by remember(famId, uid) { vm.sharedToMe(famId, uid) }.collectAsState(initial = emptyList())
    val sharedView = me == null && sharedIn.isNotEmpty()
    val viewer = Viewer(
        familyId = famId,
        userId = uid,
        isChild = role == Role.CHILD || role == Role.GUEST || (sharedView && sharedIn.none { it.canWrite }),
        canManage = role?.canManage == true,
        inventoryOn = modules.any { it.module == AppModule.INVENTORY && it.effective },
    )
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    LaunchedEffect(Unit) {
        merge(vm.messages, shopVm.messages, lifeVm.messages, kitchenVm.messages, applianceVm.messages, chatVm.messages, extrasVm.messages, priceVm.messages, shareVm.messages, assistantVm.messages, recordsVm.messages).collect { msg ->
            val text = when (msg) {
                // Numbers are passed as numbers so both %1$s and %1$d work; a bad format never crashes the app.
                is UiMessage.Res -> runCatching {
                    val a = msg.arg
                    if (a == null) context.getString(msg.id) else context.getString(msg.id, a.toLongOrNull() ?: a)
                }.getOrElse { msg.arg.orEmpty() }
                is UiMessage.Text -> context.getString(R.string.error_generic, msg.text)
            }
            snackbar.showSnackbar(text)
        }
    }

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // UX-8 kid mode: everything bigger (buttons, text, touch targets) for children.
    val kid = when (settings?.kidMode) { "ON" -> true; "OFF" -> false; else -> role == Role.CHILD }
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides if (kid) androidx.compose.ui.unit.Density(baseDensity.density * 1.15f, baseDensity.fontScale * 1.1f) else baseDensity,
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { go(tab) },
                            icon = { Icon(if (selected) tab.on else tab.off, null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        val barPadding = PaddingValues(bottom = inner.calculateBottomPadding())
        NavHost(nav, startDestination = Tab.HOME.route) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    vm, barPadding,
                    onCreateFamily = { go(Tab.FAMILIES) },
                    onOpenModules = { nav.navigate("modules/$it") },
                    onOpenCloud = { nav.navigate("cloud") },
                    extra = {
                        val city = settings?.zmanimCity ?: "jerusalem"
                        if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.NowPanel(viewer, city, lifeVm, kitchenVm, shopVm,
                            onQuickAdd = { quickAdd = true }, onSearch = { nav.navigate("search") }, onOpen = { nav.navigate(it) })
                        val shabbat by produceState<com.rimon.smartfamily.smart.ShabbatTimes?>(null, city) {
                            value = runCatching { lifeVm.shabbat(city) }.getOrNull()
                        }
                        com.rimon.smartfamily.ui.screens.ShabbatCard(shabbat, System.currentTimeMillis(), emptyList())
                    },
                    onOpenModule = { m ->
                        when (m) {
                            AppModule.SHOPPING -> nav.navigate("shopping")
                            AppModule.INVENTORY -> nav.navigate("inventory")
                            AppModule.CHORES -> nav.navigate("chores")
                            AppModule.CALENDAR -> nav.navigate("calendar")
                            AppModule.RECIPES -> nav.navigate("kitchen/0")
                            AppModule.PACKED_MEALS -> nav.navigate("kitchen/2")
                            AppModule.APPLIANCES -> nav.navigate("appliances")
                            AppModule.CHAT -> nav.navigate("chat")
                            AppModule.TRAVEL -> nav.navigate("travel")
                            AppModule.BUDGET -> nav.navigate("budget")
                            AppModule.HOME_BOOK -> nav.navigate("homebook/0")
                            AppModule.KNOWLEDGE -> nav.navigate("homebook/1")
                            AppModule.POLLS -> nav.navigate("polls")
                            AppModule.PRICES -> nav.navigate("prices")
                            AppModule.KIDS -> nav.navigate("kids")
                            AppModule.FAMILY -> nav.navigate("family")
                            AppModule.SHABBAT -> nav.navigate("shabbat")
                            else -> Unit
                        }
                    },
                )
            }
            composable(Tab.FAMILIES.route) {
                FamiliesScreen(vm, barPadding, onOpen = { nav.navigate("family/$it") })
            }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    vm, barPadding,
                    onCloud = { nav.navigate("cloud") },
                    onBackup = { nav.navigate("backup") },
                    onModules = { nav.navigate("modules/$it") },
                    onAi = { nav.navigate("ai") },
                    onSmartHome = { nav.navigate("smarthome") },
                )
            }
            composable("family/{id}") { back ->
                FamilyDetailScreen(
                    vm, back.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenModules = { nav.navigate("modules/$it") },
                    onOpenSharing = { nav.navigate("sharing/$it") },
                )
            }
            composable("sharing/{id}") { back ->
                val id = back.arguments?.getString("id").orEmpty()
                val mine by remember(id, uid) { vm.myMembership(id, uid) }.collectAsState(initial = null)
                com.rimon.smartfamily.ui.screens.SharingScreen(shareVm, id, isAdmin = mine?.let { Role.parse(it.role).canManage } == true, onBack = { nav.popBackStack() })
            }
            composable("modules/{id}") { back ->
                ModulesScreen(vm, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("cloud") { CloudScreen(vm, onBack = { nav.popBackStack() }) }
            composable("backup") { BackupScreen(vm, onBack = { nav.popBackStack() }) }
            composable("ai") {
                AiSettingsScreen(
                    shopVm,
                    currentModel = settings?.geminiModel.orEmpty(),
                    privacyMode = settings?.privacyMode == true,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("shopping") {
                if (famId.isNotEmpty()) {
                    ShoppingListsScreen(shopVm, viewer, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("shopping/list/$it") })
                }
            }
            composable("shopping/list/{id}") { back ->
                ShoppingListScreen(shopVm, viewer, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("chores") {
                if (famId.isNotEmpty()) ChoresScreen(lifeVm, viewer, settings?.zmanimCity ?: "jerusalem", onBack = { nav.popBackStack() })
            }
            composable("calendar") {
                if (famId.isNotEmpty()) {
                    val trips by remember(famId) { extrasVm.trips(famId) }.collectAsState(initial = emptyList())
                    CalendarScreen(lifeVm, viewer, settings, trips, onBack = { nav.popBackStack() })
                }
            }
            composable("kitchen/{tab}") { back ->
                if (famId.isNotEmpty()) KitchenScreen(
                    kitchenVm, viewer, back.arguments?.getString("tab")?.toIntOrNull() ?: 0,
                    onBack = { nav.popBackStack() },
                    onOpenRecipe = { nav.navigate("recipe/$it") },
                    onEditRecipe = { nav.navigate("recipe/edit/$it") },
                )
            }
            composable("recipe/{id}") { back ->
                val id = back.arguments?.getString("id").orEmpty()
                RecipeDetailScreen(
                    kitchenVm, viewer, id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("recipe/edit/$id") },
                    onCook = { nav.navigate("cook/$id") },
                )
            }
            composable("recipe/edit/{id}") { back ->
                RecipeEditScreen(kitchenVm, viewer, back.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() })
            }
            composable("cook/{id}") { back ->
                CookingScreen(kitchenVm, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("appliances") {
                if (famId.isNotEmpty()) AppliancesScreen(applianceVm, viewer, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("appliance/$it") })
            }
            composable("appliance/{id}") { back ->
                ApplianceDetailScreen(applianceVm, viewer, back.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() }, onSmartHome = { nav.navigate("smarthome") })
            }
            composable("smarthome") {
                SmartHomeScreen(applianceVm, settings?.homeConnectClientId.orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("chat") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.ChatListScreen(chatVm, viewer, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("chat/$it") })
            }
            composable("chat/{id}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.ChatScreen(chatVm, viewer, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onOpenRoute = { nav.navigate(it) })
            }
            composable("travel") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.TravelScreen(extrasVm, viewer, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("trip/$it") })
            }
            composable("trip/{id}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.TripScreen(extrasVm, viewer, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onLeaveHome = { nav.navigate("homex/2") })
            }
            composable("budget") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.BudgetScreen(extrasVm, viewer, onBack = { nav.popBackStack() }, onMoney = { nav.navigate("money/$it") })
            }
            composable("homebook/{tab}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.HomeBookScreen(extrasVm, viewer, back.arguments?.getString("tab")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() }, onExtras = { nav.navigate("homex/$it") })
            }
            composable("money/{tab}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.MoneyScreen(recordsVm, viewer, back.arguments?.getString("tab")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() })
            }
            composable("kids") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.KidsScreen(recordsVm, viewer, hebrew = isHebrew(), onBack = { nav.popBackStack() }, onPocket = { nav.navigate("money/1") })
            }
            composable("homex/{tab}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.HomeExtrasScreen(recordsVm, extrasVm, viewer, isHebrew(), back.arguments?.getString("tab")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() })
            }
            composable("family") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.FamilyHubScreen(recordsVm, chatVm, extrasVm, viewer, onBack = { nav.popBackStack() },
                    onOpenAlbum = { nav.navigate("chat/$it") }, onGifts = { nav.navigate("money/3") })
            }
            composable("shabbat") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.ShabbatScreen(recordsVm, lifeVm, kitchenVm, extrasVm, viewer, settings?.zmanimCity ?: "jerusalem", isHebrew(),
                    onBack = { nav.popBackStack() }, onKitchen = { nav.navigate("kitchen/1") }, onPolls = { nav.navigate("polls") })
            }
            composable("search") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.SearchScreen(assistantVm, viewer, onBack = { nav.popBackStack() }) { h ->
                    nav.navigate(when (h.kind) {
                        "item" -> "shopping/list/${h.id}"; "recipe" -> "recipe/${h.id}"; "home" -> if (h.sub == "KNOWLEDGE") "homebook/1" else "homebook/0"
                        "trip" -> "trip/${h.id}"; "poll" -> "polls"; "chore" -> "chores"; "pantry" -> "inventory"; "appliance" -> "appliance/${h.id}"
                        else -> "calendar"
                    })
                }
            }
            composable("prices") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.PricesScreen(priceVm, viewer, onBack = { nav.popBackStack() })
            }
            composable("polls") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.PollsScreen(extrasVm, viewer, onBack = { nav.popBackStack() })
            }
            composable("inventory") {
                if (famId.isNotEmpty()) InventoryScreen(shopVm, viewer, onBack = { nav.popBackStack() })
            }
        }
    }
    if (quickAdd) com.rimon.smartfamily.ui.screens.QuickAddDialog(assistantVm, viewer, onDismiss = { quickAdd = false })
    }
}



@Composable
private fun isHebrew(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language.let { it == "iw" || it == "he" }
