package app.still.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventDatabaseTest {
    @Test fun countsOnlyRequestedDayThenPrunesAndClears() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, EventDatabase::class.java).build()
        try {
            val dao = database.events()
            dao.insert(Interruption(packageName = "com.instagram.android", timestamp = 999))
            dao.insert(Interruption(packageName = "com.google.android.youtube", timestamp = 1000))
            dao.insert(Interruption(packageName = "com.google.android.youtube", timestamp = 1999))
            dao.insert(Interruption(packageName = "com.instagram.android", timestamp = 2000))
            assertEquals(2, dao.count(1000, 2000).first())
            dao.prune(1000)
            assertEquals(0, dao.count(0, 1000).first())
            assertEquals(3, dao.count(0, 3000).first())
            dao.clear()
            assertEquals(0, dao.count(0, 3000).first())
        } finally { database.close() }
    }
}
