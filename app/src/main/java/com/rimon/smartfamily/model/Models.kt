package com.rimon.smartfamily.model

import androidx.annotation.StringRes
import com.rimon.smartfamily.R

/** Every module of the app. [phase] is the delivery phase in the spec (section 21). */
enum class AppModule(
    val key: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val phase: Int,
    val defaultOn: Boolean,
) {
    SHOPPING("shopping", R.string.mod_shopping, R.string.mod_shopping_sub, 1, true),
    PRICES("prices", R.string.mod_prices, R.string.mod_prices_sub, 6, false),
    INVENTORY("inventory", R.string.mod_inventory, R.string.mod_inventory_sub, 1, false),
    APPLIANCES("appliances", R.string.mod_appliances, R.string.mod_appliances_sub, 4, false),
    RECIPES("recipes", R.string.mod_recipes, R.string.mod_recipes_sub, 3, false),
    PACKED_MEALS("packed_meals", R.string.mod_packed, R.string.mod_packed_sub, 3, false),
    CHORES("chores", R.string.mod_chores, R.string.mod_chores_sub, 2, true),
    TRAVEL("travel", R.string.mod_travel, R.string.mod_travel_sub, 6, false),
    CHAT("chat", R.string.mod_chat, R.string.mod_chat_sub, 5, false),
    CALENDAR("calendar", R.string.mod_calendar, R.string.mod_calendar_sub, 2, false),
    BUDGET("budget", R.string.mod_budget, R.string.mod_budget_sub, 6, false),
    HOME_BOOK("home_book", R.string.mod_homebook, R.string.mod_homebook_sub, 6, false),
    KNOWLEDGE("knowledge", R.string.mod_knowledge, R.string.mod_knowledge_sub, 6, false),
    POLLS("polls", R.string.mod_polls, R.string.mod_polls_sub, 6, false);

    /** Modules that already have screens in this version. */
    val available: Boolean get() = this in setOf(SHOPPING, INVENTORY, CHORES, CALENDAR)

    companion object {
        fun fromKey(key: String): AppModule? = entries.firstOrNull { it.key == key }

        /** The user's home-screen order (UX-4); modules missing from [csv] keep their default place at the end. */
        fun ordered(csv: String): List<AppModule> {
            val saved = csv.split(',').mapNotNull { fromKey(it.trim()) }.distinct()
            return saved + entries.filter { it !in saved }
        }
    }
}

/** Roles as defined in spec section 4. */
enum class Role(@StringRes val title: Int) {
    OWNER(R.string.role_owner),
    ADMIN(R.string.role_admin),
    MEMBER(R.string.role_member),
    CHILD(R.string.role_child),
    GUEST(R.string.role_guest);

    val canManage: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun parse(value: String): Role = entries.firstOrNull { it.name == value } ?: MEMBER
    }
}

enum class MemberStatus { ACTIVE, PENDING }

/** The current version of the backup file format and database schema. */
object SchemaInfo {
    const val DB_VERSION = 3
    const val BACKUP_FORMAT = "smartfamily-backup"
    const val BACKUP_FORMAT_VERSION = 1
}
