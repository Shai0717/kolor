package io.github.Shai0717.kolor

import net.kyori.adventure.text.Component
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.block.data.BlockData
import org.bukkit.plugin.Plugin
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class KolorDisplay(private val plugin: Plugin) {
    private val scheduler = KolorScheduler(plugin)

    fun item(viewer: Player, location: Location, item: ItemStack): CompletableFuture<ItemDisplay> {
        val snapshot = item.clone()
        return spawn(viewer, location, ItemDisplay::class.java) { it.setItemStack(snapshot) }
    }

    fun item(viewer: Player, location: Location, item: ItemStack, scale: Vector3f): CompletableFuture<ItemDisplay> {
        val snapshot = item.clone()
        val displayScale = Vector3f(scale)
        validateScale(displayScale)
        return spawn(viewer, location, ItemDisplay::class.java) {
            it.setItemStack(snapshot)
            it.transform(scale = displayScale)
        }
    }

    fun item(location: Location, item: ItemStack): CompletableFuture<ItemDisplay> {
        val snapshot = item.clone()
        return spawn(null, location, ItemDisplay::class.java) { it.setItemStack(snapshot) }
    }

    fun item(location: Location, item: ItemStack, scale: Vector3f): CompletableFuture<ItemDisplay> {
        val snapshot = item.clone()
        val displayScale = Vector3f(scale)
        validateScale(displayScale)
        return spawn(null, location, ItemDisplay::class.java) {
            it.setItemStack(snapshot)
            it.transform(scale = displayScale)
        }
    }

    fun text(viewer: Player, location: Location, text: Component): CompletableFuture<TextDisplay> =
        spawn(viewer, location, TextDisplay::class.java) { it.text(text) }

    fun block(location: Location, block: BlockData): CompletableFuture<BlockDisplay> {
        val snapshot = block.clone()
        return spawn(null, location, BlockDisplay::class.java) { it.setBlock(snapshot) }
    }

    fun block(viewer: Player, location: Location, block: BlockData): CompletableFuture<BlockDisplay> {
        val snapshot = block.clone()
        return spawn(viewer, location, BlockDisplay::class.java) { it.setBlock(snapshot) }
    }

    fun text(location: Location, text: Component): CompletableFuture<TextDisplay> =
        spawn(null, location, TextDisplay::class.java) { it.text(text) }

    private fun <T : Display> spawn(
        viewer: Player?,
        location: Location,
        type: Class<T>,
        configure: (T) -> Unit
    ): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val spawnLocation = location.clone()
        val world = requireNotNull(spawnLocation.world) { "Location must have a world" }
        with(scheduler) {
            spawnLocation.runAt {
                var spawned: T? = null
                fun cleanup(entity: T) {
                    try {
                        val cleanup = entity.runOn { entity.remove() }
                        if (cleanup == null) plugin.logger.warning("Display cleanup rejected: entity retired")
                    } catch (exception: Exception) {
                        plugin.logger.log(Level.WARNING, "Could not schedule display cleanup", exception)
                    }
                }
                try {
                    val entity = world.spawn(spawnLocation, type) {
                        it.isVisibleByDefault = viewer == null
                        it.isPersistent = false
                        configure(it)
                    }
                    spawned = entity
                    fun fail(cause: Throwable) {
                        if (result.completeExceptionally(cause)) cleanup(entity)
                    }
                    if (viewer == null) {
                        result.complete(entity)
                        return@runAt
                    }
                    val showTask = viewer.runOn(retiredCallback = {
                        scheduler.runAsync { fail(IllegalStateException("Viewer retired before display was shown")) }
                    }) {
                        try {
                            viewer.showEntity(plugin, entity)
                            result.complete(entity)
                        } catch (exception: Exception) {
                            fail(exception)
                        }
                    }
                    if (showTask == null) fail(IllegalStateException("Viewer already retired"))
                } catch (exception: Exception) {
                    result.completeExceptionally(exception)
                    spawned?.let(::cleanup)
                    plugin.logger.log(Level.WARNING, "Could not spawn display", exception)
                }
            }
        }
        return result
    }

    fun update(entity: Display, retiredCallback: () -> Unit = {}, action: (Display) -> Unit): KolorTask? =
        with(scheduler) { entity.runOn(retiredCallback = retiredCallback) { action(entity) } }

    fun remove(entity: Display, retiredCallback: () -> Unit = {}): KolorTask? =
        with(scheduler) { entity.runOn(retiredCallback = retiredCallback) { entity.remove() } }

    fun teleport(entity: Display, location: Location, retiredCallback: () -> Unit = {}): KolorTask? {
        val destination = location.clone()
        return with(scheduler) {
            entity.runOn(retiredCallback = retiredCallback) {
                entity.teleportAsync(destination).whenComplete { success, error ->
                    when {
                        error != null -> plugin.logger.log(Level.WARNING, "Could not move display entity", error)
                        success != true -> plugin.logger.warning("Display teleport was rejected")
                    }
                }
            }
        }
    }

    fun show(viewer: Player, entity: Entity, retiredCallback: () -> Unit = {}): KolorTask? =
        with(scheduler) { viewer.runOn(retiredCallback = retiredCallback) { viewer.showEntity(plugin, entity) } }

    fun hide(viewer: Player, entity: Entity, retiredCallback: () -> Unit = {}): KolorTask? =
        with(scheduler) { viewer.runOn(retiredCallback = retiredCallback) { viewer.hideEntity(plugin, entity) } }

    private fun validateScale(scale: Vector3f) {
        require(
            scale.x.isFinite() && scale.y.isFinite() && scale.z.isFinite() &&
                scale.x > 0f && scale.y > 0f && scale.z > 0f
        ) { "Display scale must contain finite positive values" }
    }
}

fun Display.transform(
    translation: Vector3f = Vector3f(),
    scale: Vector3f = Vector3f(1f, 1f, 1f),
    leftRotation: Quaternionf = Quaternionf(),
    rightRotation: Quaternionf = Quaternionf()
) {
    transformation = Transformation(Vector3f(translation), Quaternionf(leftRotation), Vector3f(scale), Quaternionf(rightRotation))
}

fun Player.lookTarget(maxDistance: Double): RayTraceResult? {
    require(maxDistance > 0 && maxDistance.isFinite()) { "Distance must be finite and positive" }
    val eye = eyeLocation
    return world.rayTrace(eye, eye.direction, maxDistance, FluidCollisionMode.NEVER, true, 0.0) { it != this }
}
