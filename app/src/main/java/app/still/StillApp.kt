package app.still

import android.app.Application
import androidx.room.Room
import app.still.data.EventDatabase
import app.still.data.SettingsStore
import app.still.data.UsageRepository

class StillApp : Application() {
    val settings by lazy { SettingsStore(this) }
    val database by lazy {
        Room.databaseBuilder(this, EventDatabase::class.java, "still-events.db").build()
    }
    val usage by lazy { UsageRepository(this) }
}
