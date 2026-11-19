package com.rimon.smartfamily.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations (BAK-6). Each one must produce exactly the tables Room expects for the new
 * version; Room checks this when the database opens.
 */
object Migrations {

    /** v1 -> v2 (app 0.2.0): shopping lists, list items, purchase history, home inventory. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "CREATE TABLE IF NOT EXISTS `shopping_lists` (`id` TEXT NOT NULL, `familyId` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `isTemplate` INTEGER NOT NULL, `shopperId` TEXT, `shoppingSince` INTEGER, `createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_shopping_lists_familyId` ON `shopping_lists` (`familyId`)",
                "CREATE TABLE IF NOT EXISTS `list_items` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, `familyId` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `category` TEXT NOT NULL, `brand` TEXT NOT NULL, `anyBrand` INTEGER NOT NULL, `note` TEXT NOT NULL, `kashrut` TEXT NOT NULL, `barcode` TEXT NOT NULL, `status` TEXT NOT NULL, `recurring` INTEGER NOT NULL, `checked` INTEGER NOT NULL, `checkedBy` TEXT, `checkedAt` INTEGER, `addedBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_list_items_listId` ON `list_items` (`listId`)",
                "CREATE INDEX IF NOT EXISTS `index_list_items_familyId` ON `list_items` (`familyId`)",
                "CREATE TABLE IF NOT EXISTS `purchase_history` (`id` TEXT NOT NULL, `familyId` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `barcode` TEXT NOT NULL, `boughtAt` INTEGER NOT NULL, `boughtBy` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_purchase_history_familyId` ON `purchase_history` (`familyId`)",
                "CREATE INDEX IF NOT EXISTS `index_purchase_history_name` ON `purchase_history` (`name`)",
                "CREATE TABLE IF NOT EXISTS `inventory_items` (`id` TEXT NOT NULL, `familyId` TEXT NOT NULL, `name` TEXT NOT NULL, `location` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `minQuantity` REAL NOT NULL, `expiresAt` INTEGER, `openedAt` INTEGER, `kashrut` TEXT NOT NULL, `category` TEXT NOT NULL, `barcode` TEXT NOT NULL, `addedBy` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `dirty` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_inventory_items_familyId` ON `inventory_items` (`familyId`)",
            ).forEach { db.execSQL(it) }
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
