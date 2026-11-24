package io.github.Shai0717.kolor

import com.mojang.brigadier.CommandDispatcher
import io.github.Shai0717.kolor.compat.div
import io.github.Shai0717.kolor.compat.minus
import io.github.Shai0717.kolor.compat.plus
import io.github.Shai0717.kolor.compat.KommandNode
import io.github.Shai0717.kolor.compat.times
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.function.Consumer
import java.util.concurrent.TimeUnit

class KolorFrameworkTest {
    @Test
    fun `legacy argument pair and delegated context support nested literals`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val source = Mockito.mock(CommandSourceStack::class.java)
        Mockito.`when`(source.sender).thenReturn(Mockito.mock(CommandSender::class.java))
        val root = Commands.literal("root")
        val node = KommandNode(KolorCommand(plugin, root))
        var result = ""
        node.apply {
            "set"("count" to int(1, 4), "name" to string()) {
                executes { context ->
                    val count: Int by context
                    val name: String = context["name"]
                    result = "$count:$name"
                }
            }
        }
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(root)
        assertEquals(1, dispatcher.execute("root set 2 \"red blue\"", source))
        assertEquals("2:red blue", result)
        assertThrows(Exception::class.java) { dispatcher.execute("root set 5 test", source) }
    }

    @Test
    fun `dynamic arguments parse with context and report invalid input`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val source = Mockito.mock(CommandSourceStack::class.java)
        Mockito.`when`(source.sender).thenReturn(Mockito.mock(CommandSender::class.java))
        val root = Commands.literal("root")
        var result = 0
        KommandNode(KolorCommand(plugin, root)).apply {
            then("factor" to int(1, 4)) {
                then("value" to dynamic { ctx, raw ->
                    require(raw == "ok") { "bad value" }
                    ctx.int("factor") * 3
                }) {
                    executes { result = it.parseArgument<Int>("value") }
                }
            }
        }
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(root)
        assertEquals(1, dispatcher.execute("root 3 ok", source))
        assertEquals(9, result)
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException::class.java) {
            dispatcher.execute("root 3 wrong", source)
        }
    }

    @Test
    fun `native and compatibility command branches parse and execute`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val sender = Mockito.mock(CommandSender::class.java)
        val source = Mockito.mock(CommandSourceStack::class.java)
        Mockito.`when`(source.sender).thenReturn(sender)
        val root = Commands.literal("root")
        val command = KolorCommand(plugin, root)
        command.literal("native") {
            intArg("amount", 1, 10) { executes { it.int("amount") } }
        }
        var legacyValue = ""
        KommandNode(command).then("legacy") {
            stringArg("value") { executes { legacyValue = it.string("value") } }
        }
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(root)
        assertEquals(7, dispatcher.execute("root native 7", source))
        assertEquals(1, dispatcher.execute("root legacy abc", source))
        assertEquals("abc", legacyValue)
        assertThrows(Exception::class.java) { dispatcher.execute("root native 11", source) }
    }

    @Test
    fun `requires rejects callers and suggestions use the argument context`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val sender = Mockito.mock(CommandSender::class.java)
        val source = Mockito.mock(CommandSourceStack::class.java)
        Mockito.`when`(source.sender).thenReturn(sender)
        val root = Commands.literal("root")
        KolorCommand(plugin, root).apply {
            literal("hidden") {
                requires { false }
                executes { 1 }
            }
            literal("open") {
                stringArg("value") {
                    suggests { _, builder -> builder.suggest("hello") }
                    executes { 1 }
                }
            }
        }
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(root)
        val suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("root open he", source)).join()
        assertEquals(listOf("hello"), suggestions.list.map { it.text })
        assertThrows(Exception::class.java) { dispatcher.execute("root hidden", source) }
    }

    @Test
    fun `global timing cancellation and entity retirement follow native scheduler`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val global = Mockito.mock(GlobalRegionScheduler::class.java)
        val scheduled = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.globalRegionScheduler).thenReturn(global)
        Mockito.`when`(global.runAtFixedRate(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(1L),
            ArgumentMatchers.eq(5L)
        )).thenReturn(scheduled)
        val scheduler = KolorScheduler(plugin)
        val task = scheduler.runGlobal(periodTicks = 5) {}
        task.cancel()
        Mockito.verify(scheduled).cancel()
        assertThrows(IllegalArgumentException::class.java) { scheduler.runGlobal(delayTicks = -1) {} }

        val entity = Mockito.mock(Entity::class.java)
        val entityScheduler = Mockito.mock(EntityScheduler::class.java)
        Mockito.`when`(entity.scheduler).thenReturn(entityScheduler)
        assertNull(with(scheduler) { entity.runOn {} })
        Mockito.verify(entityScheduler).run(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.any<Consumer<ScheduledTask>>(),
            ArgumentMatchers.any()
        )
    }

    @Test
    fun `interop scheduler executes a java runnable and returns a cancellable task`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val global = Mockito.mock(GlobalRegionScheduler::class.java)
        val scheduled = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.globalRegionScheduler).thenReturn(global)
        @Suppress("UNCHECKED_CAST")
        val action = org.mockito.ArgumentCaptor.forClass(Consumer::class.java) as org.mockito.ArgumentCaptor<Consumer<ScheduledTask>>
        Mockito.`when`(global.runDelayed(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(8L)
        )).thenReturn(scheduled)
        var ran = false
        val task = KolorInterop.runGlobal(KolorScheduler(plugin), 8, 0, Runnable { ran = true })
        Mockito.verify(global).runDelayed(ArgumentMatchers.eq(plugin), action.capture(), ArgumentMatchers.eq(8L))
        action.value.accept(scheduled)
        assertTrue(ran)
        task.cancel()
        Mockito.verify(scheduled).cancel()
    }

    @Test
    fun `async interop executes a java runnable in wall clock units`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val async = Mockito.mock(AsyncScheduler::class.java)
        val scheduled = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.asyncScheduler).thenReturn(async)
        @Suppress("UNCHECKED_CAST")
        val action = org.mockito.ArgumentCaptor.forClass(Consumer::class.java) as org.mockito.ArgumentCaptor<Consumer<ScheduledTask>>
        Mockito.`when`(async.runDelayed(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq(400L),
            ArgumentMatchers.eq(TimeUnit.MILLISECONDS)
        )).thenReturn(scheduled)

        var ran = false
        val task = KolorInterop.runAsync(KolorScheduler(plugin), 8, 0, Runnable { ran = true })

        Mockito.verify(async).runDelayed(
            ArgumentMatchers.eq(plugin),
            action.capture(),
            ArgumentMatchers.eq(400L),
            ArgumentMatchers.eq(TimeUnit.MILLISECONDS)
        )
        action.value.accept(scheduled)
        assertTrue(ran)
        task.cancel()
        Mockito.verify(scheduled).cancel()
    }

    @Test
    fun `location and entity interop preserve runnable callbacks`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val region = Mockito.mock(RegionScheduler::class.java)
        val entityScheduler = Mockito.mock(EntityScheduler::class.java)
        val locationTask = Mockito.mock(ScheduledTask::class.java)
        val entityTask = Mockito.mock(ScheduledTask::class.java)
        val location = Mockito.mock(Location::class.java)
        val entity = Mockito.mock(Entity::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.regionScheduler).thenReturn(region)
        Mockito.`when`(location.clone()).thenReturn(location)
        Mockito.`when`(entity.scheduler).thenReturn(entityScheduler)

        @Suppress("UNCHECKED_CAST")
        val locationAction = org.mockito.ArgumentCaptor.forClass(Consumer::class.java) as org.mockito.ArgumentCaptor<Consumer<ScheduledTask>>
        Mockito.`when`(region.run(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.eq(location),
            ArgumentMatchers.any()
        )).thenReturn(locationTask)
        var locationRan = false
        val scheduler = KolorScheduler(plugin)
        val locationHandle = KolorInterop.runAt(scheduler, location, 0, 0, Runnable { locationRan = true })
        Mockito.verify(region).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.eq(location), locationAction.capture())
        locationAction.value.accept(locationTask)
        assertTrue(locationRan)
        locationHandle.cancel()

        @Suppress("UNCHECKED_CAST")
        val entityAction = org.mockito.ArgumentCaptor.forClass(Consumer::class.java) as org.mockito.ArgumentCaptor<Consumer<ScheduledTask>>
        val retired = org.mockito.ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.`when`(entityScheduler.run(
            ArgumentMatchers.eq(plugin),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
        )).thenReturn(entityTask)
        var entityRan = false
        var retiredRan = false
        val entityHandle = KolorInterop.runOn(
            scheduler,
            entity,
            0,
            0,
            Runnable { retiredRan = true },
            Runnable { entityRan = true }
        )
        assertNotNull(entityHandle)
        Mockito.verify(entityScheduler).run(
            ArgumentMatchers.eq(plugin),
            entityAction.capture(),
            retired.capture()
        )
        entityAction.value.accept(entityTask)
        retired.value.run()
        assertTrue(entityRan)
        assertTrue(retiredRan)
        entityHandle!!.cancel()
    }

    @Test
    fun `compat vector operators do not mutate inputs`() {
        val a = Vector(1, 2, 3)
        val b = Vector(2, 3, 4)
        assertEquals(Vector(3, 5, 7), a + b)
        assertEquals(Vector(-1, -1, -1), a - b)
        assertEquals(Vector(2, 4, 6), a * 2.0)
        assertEquals(Vector(1, 2, 3), (a * 2.0) / 2.0)
        assertEquals(Vector(1, 2, 3), a)
        assertThrows(IllegalArgumentException::class.java) { a / 0.0 }
    }
}
