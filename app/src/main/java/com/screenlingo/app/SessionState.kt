package com.screenlingo.app

import java.util.concurrent.CopyOnWriteArraySet

data class SessionSnapshot(
    val running: Boolean = false,
    val paused: Boolean = false,
    val message: String = "Sẵn sàng khi bạn cần",
    val latencyMs: Long? = null,
    val blockCount: Int = 0
)

object SessionState {
    @Volatile var settingsVisible = false
    @Volatile var current = SessionSnapshot()
        private set
    private val listeners = CopyOnWriteArraySet<(SessionSnapshot) -> Unit>()
    fun publish(value: SessionSnapshot) {
        current = value
        listeners.forEach { it(value) }
    }
    fun subscribe(listener: (SessionSnapshot) -> Unit) { listeners.add(listener); listener(current) }
    fun unsubscribe(listener: (SessionSnapshot) -> Unit) { listeners.remove(listener) }
}
