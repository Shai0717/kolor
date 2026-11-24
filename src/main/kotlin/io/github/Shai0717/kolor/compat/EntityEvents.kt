package io.github.Shai0717.kolor.compat

import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import java.util.concurrent.atomic.AtomicBoolean

class EntityEvents(private val plugin: Plugin) {
    fun <E : Event> listen(
        entity: Entity,
        eventType: Class<E>,
        priority: EventPriority = EventPriority.NORMAL,
        ignoreCancelled: Boolean = false,
        target: (E) -> Entity?,
        action: (E) -> Unit
    ): AutoCloseable {
        val entityId = entity.uniqueId
        val closed = AtomicBoolean(false)
        val listener = object : Listener {}
        val executor = EventExecutor { _, event ->
            if (!closed.get() && eventType.isInstance(event)) {
                val typed = eventType.cast(event)
                if (target(typed)?.uniqueId == entityId) action(typed)
            }
        }
        plugin.server.pluginManager.registerEvent(eventType, listener, priority, executor, plugin, ignoreCancelled)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) HandlerList.unregisterAll(listener)
        }
    }
}
