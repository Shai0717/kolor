package io.github.Shai0717.kolor

import io.github.Shai0717.kolor.compat.EntityEvents
import io.github.Shai0717.kolor.compat.Ticker
import io.github.Shai0717.kolor.compat.get
import io.github.Shai0717.kolor.compat.has
import io.github.Shai0717.kolor.compat.persistentKey
import io.github.Shai0717.kolor.compat.remove
import io.github.Shai0717.kolor.compat.require
import io.github.Shai0717.kolor.compat.set
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Server
import org.bukkit.entity.Entity
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.UUID
import java.util.function.Consumer

class CompatibilityUtilitiesTest {
    @Test
    fun `ticker cancellation before native handle binding still cancels native task`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val scheduler = Mockito.mock(GlobalRegionScheduler::class.java)
        val nativeTask = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.globalRegionScheduler).thenReturn(scheduler)
        Mockito.doAnswer {
            it.getArgument<Consumer<ScheduledTask>>(1).accept(nativeTask)
            nativeTask
        }.`when`(scheduler).runAtFixedRate(
            ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.eq(1L), ArgumentMatchers.eq(1L)
        )
        val ticker = Ticker.global(plugin)
        val handle = ticker.runTaskTimer(periodTicks = 1) { ticker.close() }!!
        assertTrue(handle.isCancelled)
        Mockito.verify(nativeTask).cancel()
    }

    @Test
    fun `ticker executes one-shot and cancels repeating tasks on close`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val scheduler = Mockito.mock(GlobalRegionScheduler::class.java)
        val nativeTask = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.globalRegionScheduler).thenReturn(scheduler)
        val actions = mutableListOf<Consumer<ScheduledTask>>()
        Mockito.doAnswer {
            actions.add(it.getArgument(1))
            nativeTask
        }.`when`(scheduler).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any())
        Mockito.doAnswer {
            actions.add(it.getArgument(1))
            nativeTask
        }.`when`(scheduler).runAtFixedRate(
            ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.eq(1L), ArgumentMatchers.eq(2L)
        )
        val ticker = Ticker.global(plugin)
        var calls = 0
        val once = ticker.runTask { calls++ }!!
        val repeat = ticker.runTaskTimer(periodTicks = 2) { calls++ }!!
        actions[0].accept(nativeTask)
        assertTrue(once.isDone)
        assertFalse(once.isScheduled)
        actions[1].accept(nativeTask)
        assertEquals(2, calls)
        ticker.close()
        assertTrue(repeat.isCancelled)
        Mockito.verify(nativeTask).cancel()
        actions[1].accept(nativeTask)
        assertEquals(2, calls)
        assertThrows(IllegalStateException::class.java) { ticker.runTask {} }
        assertThrows(IllegalArgumentException::class.java) { Ticker.global(plugin).runTaskTimer(periodTicks = 0) {} }
    }

    @Test
    fun `ticker reports retired entity and refuses already retired scheduling`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val entity = Mockito.mock(Entity::class.java)
        val scheduler = Mockito.mock(EntityScheduler::class.java)
        val nativeTask = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(entity.scheduler).thenReturn(scheduler)
        var retired: Runnable? = null
        Mockito.doAnswer {
            retired = it.getArgument(2)
            nativeTask
        }.`when`(scheduler).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.any())
        val ticker = Ticker.on(plugin, entity)
        var called = false
        val task = ticker.runTask(retiredCallback = { called = true }) {}!!
        retired!!.run()
        assertTrue(called)
        assertTrue(task.isCancelled)
        ticker.close()
        Mockito.verify(nativeTask, Mockito.never()).cancel()

        Mockito.reset(scheduler)
        assertNull(Ticker.on(plugin, entity).runTask {})
    }

    @Test
    fun `typed persistence key returns value or fails explicitly`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        Mockito.`when`(plugin.namespace()).thenReturn("kolortest")
        val key = plugin.persistentKey("count", PersistentDataType.INTEGER)
        val data = Mockito.mock(PersistentDataContainer::class.java)
        data[key] = 8
        Mockito.verify(data).set(key.key, key.type, 8)
        Mockito.`when`(data.get(key.key, key.type)).thenReturn(8)
        Mockito.`when`(data.has(key.key, key.type)).thenReturn(true)
        assertEquals(8, data[key])
        assertTrue(data.has(key))
        assertEquals(8, data.require(key))
        data.remove(key)
        Mockito.verify(data).remove(key.key)
        Mockito.`when`(data.get(key.key, key.type)).thenReturn(null)
        assertThrows(NoSuchElementException::class.java) { data.require(key) }
    }

    @Test
    fun `entity event filters identity and stops after subscription closes`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val manager = Mockito.mock(PluginManager::class.java)
        val target = Mockito.mock(Entity::class.java)
        val other = Mockito.mock(Entity::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.pluginManager).thenReturn(manager)
        Mockito.`when`(target.uniqueId).thenReturn(UUID.randomUUID())
        Mockito.`when`(other.uniqueId).thenReturn(UUID.randomUUID())
        var executor: EventExecutor? = null
        var listener: Listener? = null
        Mockito.doAnswer {
            listener = it.getArgument(1)
            executor = it.getArgument(3)
            null
        }.`when`(manager).registerEvent(
            ArgumentMatchers.eq(EntityDamageEvent::class.java),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(EventPriority.NORMAL),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.eq(false)
        )
        var calls = 0
        val subscription = EntityEvents(plugin).listen(
            target, EntityDamageEvent::class.java, target = { it.entity }
        ) { calls++ }
        val event = Mockito.mock(EntityDamageEvent::class.java)
        Mockito.`when`(event.entity).thenReturn(other, target)
        executor!!.execute(listener!!, event)
        executor!!.execute(listener!!, event)
        assertEquals(1, calls)
        subscription.close()
        subscription.close()
        executor!!.execute(listener!!, event)
        assertEquals(1, calls)
    }
}
