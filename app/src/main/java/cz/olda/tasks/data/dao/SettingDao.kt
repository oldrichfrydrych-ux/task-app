package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.olda.tasks.data.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

/**
 * The settings table's two queries and its one write.
 *
 * An `abstract class` and not an `interface`, exactly like [TaskDao]: the JVM tests in
 * `app/src/test/` subclass a DAO to serve queries without a database (`FakeTaskDao`), and an
 * interface leaves nothing to subclass once Room's generated implementation is out of reach.
 *
 * `key` is quoted because it reads as a keyword in some SQL dialects; Room quotes it the same way
 * in the table it generates.
 */
@Dao
abstract class SettingDao {
    /** Emits the stored text, or null while the key has never been written. */
    @Query("SELECT value FROM settings WHERE `key` = :key")
    abstract fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    abstract suspend fun get(key: String): String?

    /** Writing a key that already exists replaces it: a setting has one value, not a history. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(setting: SettingEntity)
}
