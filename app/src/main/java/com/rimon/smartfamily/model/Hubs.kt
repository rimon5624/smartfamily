package com.rimon.smartfamily.model

import androidx.annotation.StringRes
import com.rimon.smartfamily.R

/**
 * 2.1 information architecture: a few areas built around what the family wants to do, instead of one module per table.
 * Each section is still switched on/off by its module, so every feature stays optional.
 */
data class HubSection(val key: String, @StringRes val title: Int, val modules: Set<AppModule>, val tab: Int? = null)

enum class Hub(val key: String, @StringRes val title: Int, @StringRes val short: Int, val sections: List<HubSection>, val extras: List<AppModule> = emptyList()) {
    FOOD("food", R.string.hub_food, R.string.nav_food, listOf(
        HubSection("lists", R.string.sec_lists, setOf(AppModule.SHOPPING)),
        HubSection("pantry", R.string.sec_pantry, setOf(AppModule.INVENTORY)),
        HubSection("menu", R.string.sec_menu, setOf(AppModule.RECIPES), tab = 1),
        HubSection("recipes", R.string.sec_recipes, setOf(AppModule.RECIPES), tab = 0),
        HubSection("school", R.string.sec_school, setOf(AppModule.PACKED_MEALS), tab = 2),
    ), extras = listOf(AppModule.PRICES)),
    CALENDAR("calendar", R.string.hub_calendar, R.string.nav_calendar, listOf(
        HubSection("calendar", R.string.sec_calendar, setOf(AppModule.CALENDAR)),
        HubSection("shabbat", R.string.sec_shabbat, setOf(AppModule.SHABBAT), tab = 0),
        HubSection("holidays", R.string.sec_holidays, setOf(AppModule.SHABBAT), tab = 1),
        HubSection("trips", R.string.sec_trips, setOf(AppModule.TRAVEL)),
    )),
    TASKS("tasks", R.string.hub_tasks, R.string.nav_tasks, listOf(
        HubSection("chores", R.string.sec_chores, setOf(AppModule.CHORES), tab = 0),
        HubSection("points", R.string.sec_points, setOf(AppModule.CHORES), tab = 1),
        HubSection("routines", R.string.sec_routines, setOf(AppModule.KIDS), tab = 0),
    )),
    MONEY("money", R.string.hub_money, R.string.nav_money, listOf(
        HubSection("budget", R.string.sec_budget, setOf(AppModule.BUDGET)),
        HubSection("bills", R.string.sec_bills, setOf(AppModule.BUDGET), tab = 0),
        HubSection("pocket", R.string.sec_pocket, setOf(AppModule.BUDGET, AppModule.KIDS), tab = 1),
        HubSection("goals", R.string.sec_goals, setOf(AppModule.BUDGET), tab = 2),
        HubSection("shared", R.string.sec_shared_costs, setOf(AppModule.BUDGET), tab = 3),
    )),
    HOME("home", R.string.hub_home, R.string.nav_home_area, listOf(
        HubSection("appliances", R.string.sec_appliances, setOf(AppModule.APPLIANCES)),
        HubSection("documents", R.string.sec_documents, setOf(AppModule.HOME_BOOK), tab = 0),
        HubSection("repairs", R.string.sec_repairs, setOf(AppModule.HOME_BOOK), tab = 0),
        HubSection("meters", R.string.sec_meters, setOf(AppModule.HOME_BOOK), tab = 1),
        HubSection("kits", R.string.sec_kits, setOf(AppModule.HOME_BOOK, AppModule.TRAVEL), tab = 2),
        HubSection("secrets", R.string.sec_secrets, setOf(AppModule.HOME_BOOK), tab = 3),
        HubSection("knowledge", R.string.sec_knowledge, setOf(AppModule.KNOWLEDGE), tab = 1),
    )),
    FAMILY("family", R.string.hub_family, R.string.nav_family, listOf(
        HubSection("tree", R.string.sec_tree, setOf(AppModule.FAMILY), tab = 0),
        HubSection("albums", R.string.sec_albums, setOf(AppModule.FAMILY), tab = 1),
        HubSection("phones", R.string.sec_phones, setOf(AppModule.FAMILY), tab = 2),
        HubSection("growth", R.string.sec_growth, setOf(AppModule.KIDS), tab = 1),
    ), extras = listOf(AppModule.CHAT, AppModule.POLLS));

    fun visibleSections(on: Set<AppModule>) = sections.filter { s -> s.modules.any { it in on } }

    /** The switchable features of this area: each module once, named after the sections it opens here. */
    val features: List<AppModule> get() = (sections.flatMap { it.modules } + extras).distinct()
    fun sectionsOf(m: AppModule) = sections.filter { m in it.modules }

    companion object {
        fun byKey(k: String) = entries.firstOrNull { it.key == k }
    }
}

/** Items that can sit in the bottom bar (the user orders them in Settings; the first four show, the rest go under "More"). */
object NavItems {
    const val TODAY = "today"
    const val CHAT = "chat"
    // 2.2: the bar shows Home, Food, Money and Tasks by default; Today, Calendar, Family and the chat are under "More"
    val defaultOrder = listOf("home", "food", "money", "tasks", TODAY, "calendar", "family", CHAT)
    fun route(item: String) = when (item) { TODAY -> "today"; CHAT -> "chat"; "families" -> "families"; "settings" -> "settings"; else -> "hub_$item" }

    fun ordered(csv: String): List<String> {
        val saved = csv.split(',').map { it.trim() }.filter { it in defaultOrder }.distinct()
        return saved + defaultOrder.filter { it !in saved }
    }

    /** Old screens (shortcuts, widgets, notifications) open inside their area. */
    fun areaFor(route: String): Pair<String, String>? = when (route.substringBefore('/')) {
        "shopping" -> "food" to "lists"; "inventory" -> "food" to "pantry"; "kitchen" -> "food" to when (route.substringAfter('/', "0")) { "1" -> "menu"; "2" -> "school"; else -> "recipes" }
        "calendar" -> "calendar" to "calendar"; "shabbat" -> "calendar" to "shabbat"; "travel" -> "calendar" to "trips"
        "chores" -> "tasks" to "chores"; "kids" -> "tasks" to "routines"
        "budget" -> "money" to "budget"; "money" -> "money" to when (route.substringAfter('/', "0")) { "1" -> "pocket"; "2" -> "goals"; "3" -> "shared"; else -> "bills" }
        "appliances" -> "home" to "appliances"; "homebook" -> "home" to (if (route.endsWith("/1")) "knowledge" else "documents")
        "homex" -> "home" to when (route.substringAfter('/', "0")) { "1" -> "meters"; "2" -> "kits"; "3" -> "secrets"; else -> "repairs" }
        "family" -> if (route == "family") "family" to "tree" else null  // "family/{id}" is a family's settings page
        else -> null
    }
}
