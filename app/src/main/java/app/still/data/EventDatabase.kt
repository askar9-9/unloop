package app.still.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "interruptions")
data class Interruption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
)

@Dao
interface EventDao {
    @Insert suspend fun insert(event: Interruption)
    @Query("SELECT COUNT(*) FROM interruptions WHERE timestamp >= :start AND timestamp < :end")
    fun count(start: Long, end: Long): Flow<Int>
    @Query("DELETE FROM interruptions") suspend fun clear()
    @Query("DELETE FROM interruptions WHERE timestamp < :before") suspend fun prune(before: Long)
}

@Database(entities = [Interruption::class], version = 1, exportSchema = true)
abstract class EventDatabase : RoomDatabase() { abstract fun events(): EventDao }
