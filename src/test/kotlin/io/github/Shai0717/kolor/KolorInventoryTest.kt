package io.github.Shai0717.kolor

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class KolorInventoryTest {
    @Test
    fun `creates titled chest inventories with optional holder`() {
        val inventory = Mockito.mock(Inventory::class.java)
        val holder = Mockito.mock(InventoryHolder::class.java)
        val title = Component.text("Enhancement")

        Mockito.mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Inventory> { Bukkit.createInventory(null, 27, title) }.thenReturn(inventory)
            bukkit.`when`<Inventory> { Bukkit.createInventory(holder, 54, title) }.thenReturn(inventory)

            assertSame(inventory, KolorInventory.create(27, title))
            assertSame(inventory, KolorInventory.create(holder, 54, title))
        }
    }

    @Test
    fun `rejects invalid chest sizes before calling Bukkit`() {
        assertThrows(IllegalArgumentException::class.java) {
            KolorInventory.create(10, Component.text("Invalid"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KolorInventory.create(63, Component.text("Invalid"))
        }
    }

    @Test
    fun `fills only the outside edge with separate item copies`() {
        val inventory = Mockito.mock(Inventory::class.java)
        val border = Mockito.mock(ItemStack::class.java)
        val copy = Mockito.mock(ItemStack::class.java)
        Mockito.`when`(inventory.size).thenReturn(27)
        Mockito.`when`(border.clone()).thenReturn(copy)

        KolorInventory.fillBorder(inventory, border)

        val borderSlots = (0..8).toList() + (9..17).filter { it % 9 == 0 || it % 9 == 8 } + (18..26).toList()
        Mockito.verify(border, Mockito.times(borderSlots.size)).clone()
        borderSlots.forEach { Mockito.verify(inventory).setItem(it, copy) }
        Mockito.verify(inventory, Mockito.never()).setItem(13, copy)
    }

    @Test
    fun `sets Adventure name and lore in place`() {
        val item = Mockito.mock(ItemStack::class.java)
        val meta = Mockito.mock(ItemMeta::class.java)
        val name = Component.text("§6Starforge")
        val lore = listOf(Component.text("§7Upgrade cost: 5"))
        Mockito.`when`(item.itemMeta).thenReturn(meta)

        assertSame(item, KolorInventory.setName(item, name))
        assertSame(item, KolorInventory.setLore(item, lore))

        Mockito.verify(meta).displayName(name)
        Mockito.verify(meta).lore(lore)
        Mockito.verify(item, Mockito.times(2)).itemMeta = meta
    }
}
