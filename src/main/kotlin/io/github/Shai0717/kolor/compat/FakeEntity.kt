package io.github.Shai0717.kolor.compat

import io.github.Shai0717.kolor.KolorDisplay
import io.github.Shai0717.kolor.KolorTask
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.block.data.BlockData
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

class FakeEntityServer(plugin: Plugin) {
    private val displays = KolorDisplay(plugin)

    fun item(viewer: Player, location: Location, item: ItemStack): CompletableFuture<FakeEntity<ItemDisplay>> =
        displays.item(viewer, location, item).thenApply { FakeEntity(displays, it) }

    fun text(viewer: Player, location: Location, text: Component): CompletableFuture<FakeEntity<TextDisplay>> =
        displays.text(viewer, location, text).thenApply { FakeEntity(displays, it) }

    fun block(viewer: Player, location: Location, block: BlockData): CompletableFuture<FakeEntity<BlockDisplay>> =
        displays.block(viewer, location, block).thenApply { FakeEntity(displays, it) }
}

class FakeEntity<T : Display> internal constructor(
    private val displays: KolorDisplay,
    val entity: T
) {
    fun update(retiredCallback: () -> Unit = {}, action: (T) -> Unit): KolorTask? =
        displays.update(entity, retiredCallback) { action(entity) }

    fun show(viewer: Player, retiredCallback: () -> Unit = {}): KolorTask? =
        displays.show(viewer, entity, retiredCallback)

    fun hide(viewer: Player, retiredCallback: () -> Unit = {}): KolorTask? =
        displays.hide(viewer, entity, retiredCallback)

    fun teleport(location: Location, retiredCallback: () -> Unit = {}): KolorTask? =
        displays.teleport(entity, location, retiredCallback)

    fun remove(retiredCallback: () -> Unit = {}): KolorTask? = displays.remove(entity, retiredCallback)
}
