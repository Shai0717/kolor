package io.github.Shai0717.kolor

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class KolorSchemaTest {
    @Test
    fun `typed reads initialize defaults and enforce constraints`() {
        val section = YamlConfiguration()
        val schema = KolorSchema(section)

        assertEquals(5, schema.int("count", 5, 1..10))
        assertEquals(5, section.getInt("count"))
        assertEquals(2.5, schema.double("speed", 2.5, 0.0..4.0))
        assertEquals("BLUE", schema.enum("color", TestColor::class.java, TestColor.BLUE).name)
        assertEquals(listOf("one", "two"), schema.strings("names", listOf("one", "two")))
    }

    @Test
    fun `typed reads reject invalid values and required keys`() {
        val section = YamlConfiguration().apply {
            set("count", "many")
            set("speed", 5.0)
        }
        val schema = KolorSchema(section)

        assertThrows(IllegalArgumentException::class.java) { schema.int("count") }
        assertThrows(IllegalArgumentException::class.java) { schema.double("speed", 1.0, 0.0..4.0) }
        assertThrows(IllegalArgumentException::class.java) { schema.string("missing") }
    }

    private enum class TestColor { BLUE, RED }
}
