package io.github.Shai0717.kolor

import io.github.Shai0717.kolor.compat.FakeEntityServer
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.function.Consumer
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletableFuture

class DisplayTest {
    @Test
    fun `fake block display shows and teleports through entity scheduler`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val world = Mockito.mock(World::class.java)
        val region = Mockito.mock(RegionScheduler::class.java)
        val viewer = Mockito.mock(Player::class.java)
        val display = Mockito.mock(BlockDisplay::class.java)
        val data = Mockito.mock(BlockData::class.java)
        val snapshot = Mockito.mock(BlockData::class.java)
        val viewerScheduler = Mockito.mock(EntityScheduler::class.java)
        val displayScheduler = Mockito.mock(EntityScheduler::class.java)
        val task = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.regionScheduler).thenReturn(region)
        Mockito.`when`(viewer.scheduler).thenReturn(viewerScheduler)
        Mockito.`when`(display.scheduler).thenReturn(displayScheduler)
        Mockito.`when`(data.clone()).thenReturn(snapshot)
        Mockito.`when`(display.teleportAsync(ArgumentMatchers.any())).thenReturn(CompletableFuture.completedFuture(true))
        Mockito.doAnswer {
            it.getArgument<Consumer<BlockDisplay>>(2).accept(display)
            display
        }.`when`(world).spawn(
            ArgumentMatchers.any(), ArgumentMatchers.eq(BlockDisplay::class.java), ArgumentMatchers.any<Consumer<BlockDisplay>>()
        )
        Mockito.doAnswer {
            it.getArgument<Consumer<ScheduledTask>>(2).accept(task)
            task
        }.`when`(region).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any<Location>(), ArgumentMatchers.any())
        listOf(viewerScheduler, displayScheduler).forEach { scheduler ->
            Mockito.doAnswer {
                it.getArgument<Consumer<ScheduledTask>>(1).accept(task)
                task
            }.`when`(scheduler).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.any())
        }

        val location = Location(world, 1.0, 2.0, 3.0)
        val fake = FakeEntityServer(plugin).block(viewer, location, data).join()
        Mockito.verify(display).setBlock(snapshot)
        val order = Mockito.inOrder(data, region)
        order.verify(data).clone()
        order.verify(region).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any<Location>(), ArgumentMatchers.any())
        Mockito.verify(viewer).showEntity(plugin, display)
        val destination = Location(world, 4.0, 5.0, 6.0)
        fake.teleport(destination)
        Mockito.verify(display).teleportAsync(destination)
    }

    @Test
    fun `fake text display is hidden by default and managed on entity schedulers`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val world = Mockito.mock(World::class.java)
        val region = Mockito.mock(RegionScheduler::class.java)
        val viewer = Mockito.mock(Player::class.java)
        val display = Mockito.mock(TextDisplay::class.java)
        val viewerScheduler = Mockito.mock(EntityScheduler::class.java)
        val displayScheduler = Mockito.mock(EntityScheduler::class.java)
        val task = Mockito.mock(ScheduledTask::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.regionScheduler).thenReturn(region)
        Mockito.`when`(viewer.scheduler).thenReturn(viewerScheduler)
        Mockito.`when`(display.scheduler).thenReturn(displayScheduler)
        Mockito.doAnswer {
            it.getArgument<Consumer<TextDisplay>>(2).accept(display)
            display
        }.`when`(world).spawn(
            ArgumentMatchers.any(), ArgumentMatchers.eq(TextDisplay::class.java), ArgumentMatchers.any<Consumer<TextDisplay>>()
        )
        Mockito.doAnswer {
            it.getArgument<Consumer<ScheduledTask>>(2).accept(task)
            task
        }.`when`(region).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any<Location>(), ArgumentMatchers.any())
        listOf(viewerScheduler, displayScheduler).forEach { scheduler ->
            Mockito.doAnswer {
                it.getArgument<Consumer<ScheduledTask>>(1).accept(task)
                task
            }.`when`(scheduler).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.any())
        }

        val message = Component.text("test")
        val fake = FakeEntityServer(plugin).text(viewer, Location(world, 1.0, 2.0, 3.0), message).join()
        assertSame(display, fake.entity)
        Mockito.verify(display).isVisibleByDefault = false
        Mockito.verify(display).isPersistent = false
        Mockito.verify(display).text(message)
        Mockito.verify(viewer).showEntity(plugin, display)
        fake.update { it.text(Component.text("updated")) }
        fake.remove()
        Mockito.verify(display).text(Component.text("updated"))
        Mockito.verify(display).remove()

        Mockito.reset(viewerScheduler)
        val failed = FakeEntityServer(plugin).text(viewer, Location(world, 1.0, 2.0, 3.0), message)
        assertThrows(CompletionException::class.java) { failed.join() }
        Mockito.verify(display, Mockito.times(2)).remove()
    }
}
