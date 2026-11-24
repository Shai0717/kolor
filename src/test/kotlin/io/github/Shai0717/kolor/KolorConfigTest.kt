package io.github.Shai0717.kolor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class KolorConfigTest {
    @Test
    fun `missing file is created from relative resource path`(@TempDir dir: Path) {
        val file = dir.resolve("created.yml").toFile()

        val config = KolorConfig.load(file, javaClass, "config-defaults-test.yml")

        assertTrue(file.isFile)
        assertEquals("기본", config.getString("nested.name"))
        assertEquals(3, config.getInt("nested.amount"))
    }

    @Test
    fun `user values override UTF-8 defaults and absent values use defaults`(@TempDir dir: Path) {
        val file = dir.resolve("config.yml").toFile()
        file.writeText("nested:\n  amount: 5\n")
        val config = KolorConfig.load(file, javaClass, "/config-defaults-test.yml")
        assertEquals("기본", config.getString("nested.name"))
        assertEquals(5, config.getInt("nested.amount"))
        file.writeText("nested:\n  name: 변경\n")
        val reloaded = KolorConfig.load(file, javaClass, "/config-defaults-test.yml")
        assertEquals("변경", reloaded.getString("nested.name"))
        assertEquals(3, reloaded.getInt("nested.amount"))
    }

    @Test
    fun `malformed files and missing resources fail explicitly`(@TempDir dir: Path) {
        val file = dir.resolve("config.yml").toFile()
        file.writeText("value: [invalid\n")
        assertThrows(IllegalArgumentException::class.java) {
            KolorConfig.load(file, javaClass, "/config-defaults-test.yml")
        }
        file.writeText("value: true\n")
        assertThrows(IllegalStateException::class.java) {
            KolorConfig.load(file, javaClass, "/missing.yml")
        }
    }
}
