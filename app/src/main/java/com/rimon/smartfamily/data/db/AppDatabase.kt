package com.rimon.smartfamily.data.db

import android.content.Context
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
    ],
    version = SchemaInfo.DB_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun families(): FamilyDao
    abstract fun members(): MemberDao
    abstract fun invites(): InviteDao
    abstract fun moduleSettings(): ModuleSettingDao

    companion object {
        /**
         * Opens the SQLCipher-encrypted database (AES-256). Every future schema change must add a
         * Room Migration here (BAK-6); destructive fallback is deliberately not enabled.
         */
        fun open(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, "smartfamily.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
