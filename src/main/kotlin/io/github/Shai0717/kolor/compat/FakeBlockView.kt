package io.github.Shai0717.kolor.compat

import io.github.Shai0717.kolor.KolorScheduler
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

class FakeBlockView(plugin: Plugin) {
    private val scheduler = KolorScheduler(plugin)

    fun show(viewer: Player, location: Location, data: BlockData): CompletableFuture<Unit> {
        val target = location.clone()
        requireNotNull(target.world) { "Location must have a world" }
        val snapshot = data.clone()
        val result = CompletableFuture<Unit>()
        send(viewer, target, snapshot, result)
        return result
    }

    fun restore(viewer: Player, location: Location): CompletableFuture<Unit> {
        val target = location.clone()
        val world = requireNotNull(target.world) { "Location must have a world" }
        val result = CompletableFuture<Unit>()
        with(scheduler) {
            target.runAt {
                try {
                    check(world.isChunkLoaded(target.blockX shr 4, target.blockZ shr 4)) {
                        "Cannot restore a block in an unloaded chunk"
                    }
                    send(viewer, target, target.block.blockData.clone(), result)
                } catch (exception: Exception) {
                    result.completeExceptionally(exception)
                }
            }
        }
        return result
    }

    private fun send(viewer: Player, location: Location, data: BlockData, result: CompletableFuture<Unit>) {
        val task = with(scheduler) {
            viewer.runOn(retiredCallback = {
                scheduler.runAsync {
                    result.completeExceptionally(IllegalStateException("Viewer retired before block update"))
                }
            }) {
                try {
                    check(viewer.world == location.world) { "Viewer and block must be in the same world" }
                    viewer.sendBlockChange(location, data)
                    result.complete(Unit)
                } catch (exception: Exception) {
                    result.completeExceptionally(exception)
                }
            }
        }
        if (task == null) result.completeExceptionally(IllegalStateException("Viewer already retired"))
    }
}
