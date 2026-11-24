package io.github.Shai0717.kolor

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * Typed access to a configuration section. Missing values are initialized with
 * the supplied defaults; invalid values fail at the point where they are read.
 */
class KolorSchema(private val section: ConfigurationSection) {
    fun contains(path: String): Boolean = section.contains(path)

    fun raw(path: String): Any? = section.get(path)

    fun putDefault(path: String, value: Any) {
        if (!section.contains(path)) section[path] = value
    }

    fun <T : Any> read(path: String, default: T, convert: (Any) -> T): T =
        value(path, default, convert)

    fun section(path: String): KolorSchema {
        val child = section.getConfigurationSection(path) ?: section.createSection(path)
        return KolorSchema(child)
    }

    fun string(path: String, default: String? = null): String =
        value(path, default) { it as? String ?: invalid(path, "a string", it) }

    fun boolean(path: String, default: Boolean? = null): Boolean =
        value(path, default) { it as? Boolean ?: invalid(path, "a boolean", it) }

    fun int(path: String, default: Int? = null, range: IntRange? = null): Int =
        value(path, default) {
            val number = it as? Number ?: invalid(path, "an integer", it)
            val converted = number.toInt()
            require(number.toDouble() == converted.toDouble()) { "Configuration '$path' must be an integer" }
            converted
        }.also { range?.let { bounds -> require(it in bounds) { "Configuration '$path' must be in $bounds" } } }

    fun long(path: String, default: Long? = null, range: LongRange? = null): Long =
        value(path, default) {
            val number = it as? Number ?: invalid(path, "an integer", it)
            val converted = number.toLong()
            require(number.toDouble() == converted.toDouble()) { "Configuration '$path' must be an integer" }
            converted
        }.also { range?.let { bounds -> require(it in bounds) { "Configuration '$path' must be in $bounds" } } }

    fun double(path: String, default: Double? = null, range: ClosedFloatingPointRange<Double>? = null): Double =
        value(path, default) {
            (it as? Number)?.toDouble()?.takeIf(Double::isFinite) ?: invalid(path, "a finite number", it)
        }.also { range?.let { bounds -> require(it in bounds) { "Configuration '$path' must be in $bounds" } } }

    fun <T : Enum<T>> enum(path: String, type: Class<T>, default: T? = null): T =
        value(path, default) {
            val name = it as? String ?: invalid(path, "a ${type.simpleName} name", it)
            type.enumConstants.firstOrNull { value -> value.name.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ${type.simpleName} value '$name' at '$path'")
        }

    fun item(path: String, default: ItemStack? = null): ItemStack? =
        value(path, default) {
            deserializeItem(path, it)
        }

    fun strings(path: String, default: List<String> = emptyList()): List<String> =
        value(path, default) {
            val values = it as? List<*> ?: invalid(path, "a list of strings", it)
            values.mapIndexed { index, item ->
                item as? String ?: invalid("$path[$index]", "a string", item ?: "null")
            }
        }

    fun items(path: String, default: List<ItemStack> = emptyList()): List<ItemStack> =
        value(path, default) {
            val values = it as? List<*> ?: invalid(path, "a list of item stacks", it)
            values.mapIndexed { index, item ->
                deserializeItem("$path[$index]", item ?: "null")
            }
        }

    private fun <T> value(path: String, default: T?, convert: (Any) -> T): T {
        if (!section.contains(path)) {
            require(default != null) { "Required configuration '$path' is missing" }
            section[path] = default
            return default
        }
        val raw = section.get(path) ?: throw IllegalArgumentException("Configuration '$path' cannot be null")
        return convert(raw)
    }

    private fun invalid(path: String, expected: String, actual: Any): Nothing =
        throw IllegalArgumentException("Configuration '$path' must be $expected, found ${actual::class.simpleName}")

    private fun deserializeItem(path: String, value: Any): ItemStack = when (value) {
        is ItemStack -> value.clone()
        is ConfigurationSection -> deserializeItem(path, value.getValues(false))
        is Map<*, *> -> {
            val serialized = LinkedHashMap<String, Any>()
            for ((key, item) in value) {
                val name = key as? String ?: invalid(path, "an item stack map with string keys", value)
                serialized[name] = item ?: invalid("$path.$name", "a non-null value", "null")
            }
            try {
                ItemStack.deserialize(serialized)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Configuration '$path' contains an invalid item stack", exception)
            }
        }
        else -> invalid(path, "an item stack", value)
    }
}

fun String.toKolorKey(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase(Locale.ROOT)
