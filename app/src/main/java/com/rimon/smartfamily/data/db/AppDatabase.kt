package com.rimon.smartfamily.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rimon.smartfamily.model.SchemaInfo
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ProfileEntity::class,
        FamilyEntity::class,
        MemberEntity::class,
        InviteEntity::class,
        ModuleSettingEntity::class,
        ShoppingListEntity::class,
        ListItemEntity::class,
        PurchaseEntity::class,
        InventoryItemEntity::class,
        // 0.3.0
        ChoreEntity::class,
        ChoreCompletionEntity::class,
        PointsEntryEntity::class,
        RewardEntity::class,
        FamilyDateEntity::class,
        EventEntity::class,
        // 0.4.0
        RecipeEntity::class,
        RecipeRatingEntity::class,
        MealPlanEntryEntity::class,
        MealProfileEntity::class,
        PackedMealEntity::class,
        // 0.5.0
        ApplianceEntity::class,
        LaundryPresetEntity::class,
    ],
    version = SchemaInfo.DB_VERSION,
    exportSchema = true,
    // New tables only: Room writes these migrations itself from the exported schemas in app/schemas.
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun families(): FamilyDao
    abstract fun members(): MemberDao
    abstract fun invites(): InviteDao
    abstract fun moduleSettings(): ModuleSettingDao
    abstract fun lists(): ShoppingListDao
    abstract fun listItems(): ListItemDao
    abstract fun purchases(): PurchaseDao
    abstract fun inventory(): InventoryDao
    abstract fun chores(): ChoreDao
    abstract fun choreCompletions(): ChoreCompletionDao
    abstract fun pointsEntrys(): PointsEntryDao
    abstract fun rewards(): RewardDao
    abstract fun familyDates(): FamilyDateDao
    abstract fun events(): EventDao
    abstract fun recipes(): RecipeDao
    abstract fun recipeRatings(): RecipeRatingDao
    abstract fun mealPlanEntrys(): MealPlanEntryDao
    abstract fun mealProfiles(): MealProfileDao
    abstract fun packedMeals(): PackedMealDao
    abstract fun appliances(): ApplianceDao
    abstract fun laundryPresets(): LaundryPresetDao

    companion object {
        /**
         * Opens the SQLCipher-encrypted database (AES-256). Every future schema change must add a
         * Room Migration here (BAK-6); destructive fallback is deliberately not enabled.
         */
        fun open(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, "smartfamily.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}
