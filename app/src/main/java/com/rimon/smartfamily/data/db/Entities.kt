package com.rimon.smartfamily.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Every synced row carries:
 *  - updatedAt: milliseconds; set locally on edit, replaced by the server value after sync
 *  - deletedAt: soft delete, so deletions sync to other phones
 *  - dirty: true while the row has local changes not yet pushed to Supabase (never sent to the server)
 * The @SerialName values match the Supabase column names (supabase/migrations).
 */

@Serializable
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_color") val avatarColor: Int = 0,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @kotlinx.serialization.Transient val dirty: Boolean = true,
)

@Serializable
@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    val kashrut: String = "kosher",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @kotlinx.serialization.Transient val dirty: Boolean = true,
)

@Serializable
@Entity(
    tableName = "family_members",
    indices = [Index(value = ["familyId", "userId"], unique = true), Index("userId")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
    val status: String = "ACTIVE",
    @SerialName("joined_at") val joinedAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @kotlinx.serialization.Transient val dirty: Boolean = true,
)

@Serializable
@Entity(tableName = "invites", indices = [Index("familyId")])
data class InviteEntity(
    @PrimaryKey val code: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("created_by") val createdBy: String,
    val role: String = "MEMBER",
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("used_by") val usedBy: String? = null,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @kotlinx.serialization.Transient val dirty: Boolean = true,
)

/**
 * A module switch. userId == "" means the family-level setting (set by an admin);
 * a non-empty userId is that user's personal setting, which can only narrow the family one (TOG-1).
 */
@Serializable
@Entity(tableName = "module_settings", indices = [Index("familyId")])
data class ModuleSettingEntity(
    @PrimaryKey val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    val module: String,
    val enabled: Boolean,
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @kotlinx.serialization.Transient val dirty: Boolean = true,
) {
    companion object {
        fun idFor(familyId: String, userId: String, module: String) = "$familyId|$userId|$module"
    }
}
