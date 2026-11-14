package app.still.protection

import kotlinx.coroutines.flow.MutableStateFlow

data class LiveStatus(
    val connected: Boolean = false,
    val packageName: String? = null,
    val masks: Int = 0,
    val viewer: Boolean = false,
    val note: String = "Совместимость с вашим устройством ещё не проверена",
)
object ProtectionStatus { val live = MutableStateFlow(LiveStatus()) }
