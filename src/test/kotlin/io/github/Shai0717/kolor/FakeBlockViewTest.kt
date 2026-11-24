package io.github.Shai0717.kolor

import io.github.Shai0717.kolor.compat.FakeBlockView
import io.papermc.paper.threadedregions.scheduler.EntityScheduler
import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.concurrent.CompletionException
import java.util.function.Consumer

class FakeBlockViewTest {
    @Test
    fun `fake block sends snapshot and restores region-owned block state`() {
        val plugin = Mockito.mock(JavaPlugin::class.java)
        val server = Mockito.mock(Server::class.java)
        val world = Mockito.mock(World::class.java)
        val viewer = Mockito.mock(Player::class.java)
        val region = Mockito.mock(RegionScheduler::class.java)
        val entityScheduler = Mockito.mock(EntityScheduler::class.java)
        val task = Mockito.mock(ScheduledTask::class.java)
        val fake = Mockito.mock(BlockData::class.java)
        val fakeSnapshot = Mockito.mock(BlockData::class.java)
        val real = Mockito.mock(BlockData::class.java)
        val realSnapshot = Mockito.mock(BlockData::class.java)
        val block = Mockito.mock(Block::class.java)
        Mockito.`when`(plugin.server).thenReturn(server)
        Mockito.`when`(server.regionScheduler).thenReturn(region)
        Mockito.`when`(viewer.scheduler).thenReturn(entityScheduler)
        Mockito.`when`(viewer.world).thenReturn(world)
        Mockito.`when`(fake.clone()).thenReturn(fakeSnapshot)
        Mockito.`when`(real.clone()).thenReturn(realSnapshot)
        Mockito.`when`(world.isChunkLoaded(0, 0)).thenReturn(true)
        Mockito.`when`(world.getBlockAt(ArgumentMatchers.any<Location>())).thenReturn(block)
        Mockito.`when`(block.blockData).thenReturn(real)
        Mockito.doAnswer {
            it.getArgument<Consumer<ScheduledTask>>(1).accept(task)
            task
        }.`when`(entityScheduler).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any(), ArgumentMatchers.any())
        var regionAction: Consumer<ScheduledTask>? = null
        Mockito.doAnswer {
            regionAction = it.getArgument(2)
            task
        }.`when`(region).run(ArgumentMatchers.eq(plugin), ArgumentMatchers.any<Location>(), ArgumentMatchers.any())

        val location = Location(world, 1.0, 2.0, 3.0)
        val visual = FakeBlockView(plugin)
        visual.show(viewer, location, fake).join()
        Mockito.verify(viewer).sendBlockChange(location, fakeSnapshot)
        val restored = visual.restore(viewer, location)
        assertFalse(restored.isDone)
        Mockito.verify(world, Mockito.never()).getBlockAt(ArgumentMatchers.any<Location>())
        regionAction!!.accept(task)
        restored.join()
        Mockito.verify(viewer).sendBlockChange(location, realSnapshot)

        Mockito.`when`(world.isChunkLoaded(0, 0)).thenReturn(false)
        val failure = visual.restore(viewer, location)
        regionAction!!.accept(task)
        assertThrows(CompletionException::class.java) { failure.join() }
        Mockito.verify(world, Mockito.times(1)).getBlockAt(ArgumentMatchers.any<Location>())
        Mockito.reset(entityScheduler)
        assertThrows(CompletionException::class.java) { visual.show(viewer, location, fake).join() }
    }
}
