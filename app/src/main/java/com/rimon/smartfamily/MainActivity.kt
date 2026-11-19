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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
                    when {
                        s == null -> Box(Modifier.fillMaxSize()) // settings are loading (a few ms)
                        !s.onboardingDone -> OnboardingScreen(vm)
                        else -> MainNav(vm)
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
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val family by vm.activeFamily.collectAsStateWithLifecycle()
    val uid = settings?.userId.orEmpty()
    val famId = family?.id.orEmpty()
    val me by remember(famId, uid) { vm.myMembership(famId, uid) }.collectAsState(initial = null)
    val modules by remember(famId, uid) { vm.modules(famId, uid) }.collectAsState(initial = emptyList())
    val role = me?.let { Role.parse(it.role) }
    val viewer = Viewer(
        familyId = famId,
        userId = uid,
        isChild = role == Role.CHILD || role == Role.GUEST,
        canManage = role?.canManage == true,
        inventoryOn = modules.any { it.module == AppModule.INVENTORY && it.effective },
    )
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    LaunchedEffect(Unit) {
        merge(vm.messages, shopVm.messages, lifeVm.messages, kitchenVm.messages, applianceVm.messages, chatVm.messages, extrasVm.messages, priceVm.messages).collect { msg ->
            val text = when (msg) {
                is UiMessage.Res -> if (msg.arg != null) context.getString(msg.id, msg.arg) else context.getString(msg.id)
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
                )
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
                if (famId.isNotEmpty()) CalendarScreen(lifeVm, viewer, onBack = { nav.popBackStack() })
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
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.ChatScreen(chatVm, viewer, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("travel") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.TravelScreen(extrasVm, viewer, onBack = { nav.popBackStack() }, onOpen = { nav.navigate("trip/$it") })
            }
            composable("trip/{id}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.TripScreen(extrasVm, viewer, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("budget") {
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.BudgetScreen(extrasVm, viewer, onBack = { nav.popBackStack() })
            }
            composable("homebook/{tab}") { back ->
                if (famId.isNotEmpty()) com.rimon.smartfamily.ui.screens.HomeBookScreen(extrasVm, viewer, back.arguments?.getString("tab")?.toIntOrNull() ?: 0, onBack = { nav.popBackStack() })
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
}

