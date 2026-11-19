package app.still.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "interruptions")
data class Interruption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
)

/** Finished days copied from Android usage events, which the system keeps only for about a week. */
@Entity(tableName = "usage_days")
data class StoredDay(@PrimaryKey val date: String, val totalMs: Long)

@Entity(tableName = "usage_apps", primaryKeys = ["date", "packageName"])
data class StoredApp(val date: String, val packageName: String, val ms: Long)

@Dao
interface EventDao {
    @Insert suspend fun insert(event: Interruption)
    @Query("SELECT COUNT(*) FROM interruptions WHERE timestamp >= :start AND timestamp < :end")
    fun count(start: Long, end: Long): Flow<Int>
    @Query("DELETE FROM interruptions") suspend fun clear()
    @Query("DELETE FROM interruptions WHERE timestamp < :before") suspend fun prune(before: Long)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_days WHERE date >= :from") suspend fun days(from: String): List<StoredDay>
    @Query("SELECT * FROM usage_apps WHERE date >= :from") suspend fun apps(from: String): List<StoredApp>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDay(day: StoredDay)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putApps(apps: List<StoredApp>)
    @Query("DELETE FROM usage_apps WHERE date = :date") suspend fun clearApps(date: String)
    @Query("DELETE FROM usage_days WHERE date < :before") suspend fun pruneDays(before: String)
    @Query("DELETE FROM usage_apps WHERE date < :before") suspend fun pruneApps(before: String)
    @Query("DELETE FROM usage_days") suspend fun clearDays()
    @Query("DELETE FROM usage_apps") suspend fun clearAllApps()

    /** Replaces a stored day only with a fuller one: Android may already have dropped the start of its oldest day. */
    @Transaction
    suspend fun keepFuller(day: StoredDay, apps: List<StoredApp>, current: StoredDay?) {
        if (current != null && current.totalMs >= day.totalMs) return
        putDay(day)
        clearApps(day.date)
        putApps(apps)
    }

    @Transaction
    suspend fun clear() { clearDays(); clearAllApps() }
}

@Database(
    entities = [Interruption::class, StoredDay::class, StoredApp::class], version = 2, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class EventDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun usage(): UsageDao
}
