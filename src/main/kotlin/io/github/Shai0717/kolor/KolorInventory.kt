package io.github.Shai0717.kolor

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * Small inventory and item helpers for plugin-owned GUIs.
 *
 * This API only creates and decorates inventories/items; click handling and GUI
 * lifecycle remain the responsibility of the calling plugin.
 */
object KolorInventory {
    /**
     * Creates a chest inventory with an Adventure title.
     */
    @JvmStatic
    fun create(size: Int, title: Component): Inventory {
        require(size in 9..54 && size % 9 == 0) { "Chest inventory size must be a multiple of 9 from 9 to 54" }
        return Bukkit.createInventory(null, size, title)
    }

    /**
     * Creates a chest inventory with a holder and an Adventure title.
     */
    @JvmStatic
    fun create(holder: InventoryHolder?, size: Int, title: Component): Inventory {
        require(size in 9..54 && size % 9 == 0) { "Chest inventory size must be a multiple of 9 from 9 to 54" }
        return Bukkit.createInventory(holder, size, title)
    }

    /**
     * Fills the outside edge of a chest-shaped inventory with independent copies
     * of [borderItem], leaving interior slots untouched.
     */
    @JvmStatic
    fun fillBorder(inventory: Inventory, borderItem: ItemStack) {
        val size = inventory.size
        require(size in 9..54 && size % 9 == 0) {
            "Border requires a chest inventory size that is a multiple of 9 from 9 to 54"
        }

        val rows = size / 9
        for (slot in 0 until size) {
            val row = slot / 9
            val column = slot % 9
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                inventory.setItem(slot, borderItem.clone())
            }
        }
    }

    /**
     * Fills the outside edge of a chest-shaped inventory with the given material.
     */
    @JvmStatic
    fun fillBorder(inventory: Inventory, material: Material) {
        fillBorder(inventory, ItemStack(material))
    }

    /**
     * Creates an item and applies its Adventure name and lore.
     */
    @JvmStatic
    fun item(material: Material, name: Component, lore: List<Component>): ItemStack =
        setLore(setName(ItemStack(material), name), lore)

    /**
     * Creates a named item without lore.
     */
    @JvmStatic
    fun item(material: Material, name: Component): ItemStack =
        setName(ItemStack(material), name)

    /**
     * Sets an item's Adventure display name in place and returns the same item.
     */
    @JvmStatic
    fun setName(item: ItemStack, name: Component): ItemStack {
        val meta = item.itemMeta
        meta.displayName(name)
        item.itemMeta = meta
        return item
    }

    /**
     * Sets an item's Adventure lore in place and returns the same item.
     */
    @JvmStatic
    fun setLore(item: ItemStack, lore: List<Component>): ItemStack {
        val meta = item.itemMeta
        meta.lore(lore.toList())
        item.itemMeta = meta
        return item
    }
}
