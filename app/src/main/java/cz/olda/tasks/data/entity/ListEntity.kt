package cz.olda.tasks.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lists", indices = [Index("groupId")])
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** `#RRGGBB`. */
    val colorHex: String,
    val icon: String? = null,
    val groupId: Long? = null,
    val position: Int = 0,
    val isInbox: Boolean = false,
    /** Reserved for the Google Tasks sync phase; nothing writes it yet. */
    val googleListId: String? = null,
)
