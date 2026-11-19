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
        // 0.6.0
        UserKeyEntity::class,
        ChatEntity::class,
        ChatKeyEntity::class,
        ChatMessageEntity::class,
        ChatReactionEntity::class,
        ChatReadEntity::class,
        TripEntity::class,
        TripItemEntity::class,
        BudgetEntryEntity::class,
        BudgetLimitEntity::class,
        HomeEntryEntity::class,
        PollEntity::class,
        PollVoteEntity::class,
        PriceStoreEntity::class,
        PriceItemEntity::class,
        FamilyShareEntity::class,
        RecurringPaymentEntity::class,
        PocketEntryEntity::class,
        GoalEntity::class,
        SplitExpenseEntity::class,
        GrowthEntryEntity::class,
        RepairEntity::class,
        MeterReadingEntity::class,
        KitItemEntity::class,
        RoutineStepEntity::class,
        RoutineCheckEntity::class,
        TreePersonEntity::class,
        PriceHistoryEntity::class,
        PriceWatchEntity::class,
        PromoEntity::class,
        ScheduledMessageEntity::class,
    ],
    version = SchemaInfo.DB_VERSION,
    exportSchema = true,
    // New tables only: Room writes these migrations itself from the exported schemas in app/schemas.
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
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
    abstract fun userKeys(): UserKeyDao
    abstract fun chats(): ChatDao
    abstract fun chatKeys(): ChatKeyDao
    abstract fun chatMessages(): ChatMessageDao
    abstract fun chatReactions(): ChatReactionDao
    abstract fun chatReads(): ChatReadDao
    abstract fun trips(): TripDao
    abstract fun tripItems(): TripItemDao
    abstract fun budgetEntrys(): BudgetEntryDao
    abstract fun budgetLimits(): BudgetLimitDao
    abstract fun homeEntrys(): HomeEntryDao
    abstract fun polls(): PollDao
    abstract fun pollVotes(): PollVoteDao
    abstract fun prices(): PriceDao
    abstract fun familyShares(): FamilyShareDao
    abstract fun people(): PeopleDao
    abstract fun search(): SearchDao
    abstract fun recurringPayments(): RecurringPaymentDao
    abstract fun pocketEntrys(): PocketEntryDao
    abstract fun goals(): GoalDao
    abstract fun splitExpenses(): SplitExpenseDao
    abstract fun growthEntrys(): GrowthEntryDao
    abstract fun repairs(): RepairDao
    abstract fun meterReadings(): MeterReadingDao
    abstract fun kitItems(): KitItemDao
    abstract fun routineSteps(): RoutineStepDao
    abstract fun routineChecks(): RoutineCheckDao
    abstract fun treePersons(): TreePersonDao
    abstract fun scheduledMessages(): ScheduledMessageDao
    abstract fun priceExtras(): PriceExtrasDao

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
