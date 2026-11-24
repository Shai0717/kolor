package io.github.Shai0717.kolor.compat

import io.papermc.paper.persistence.PersistentDataContainerView
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

class PersistentKey<P : Any, C : Any>(val key: NamespacedKey, val type: PersistentDataType<P, C>)

fun <P : Any, C : Any> Plugin.persistentKey(name: String, type: PersistentDataType<P, C>): PersistentKey<P, C> =
    PersistentKey(NamespacedKey(this, name), type)

operator fun <P : Any, C : Any> PersistentDataContainerView.get(key: PersistentKey<P, C>): C? =
    get(key.key, key.type)

operator fun <P : Any, C : Any> PersistentDataContainer.set(key: PersistentKey<P, C>, value: C) {
    set(key.key, key.type, value)
}

fun <P : Any, C : Any> PersistentDataContainerView.has(key: PersistentKey<P, C>): Boolean = has(key.key, key.type)

fun <P : Any, C : Any> PersistentDataContainer.remove(key: PersistentKey<P, C>) = remove(key.key)

fun <P : Any, C : Any> PersistentDataContainerView.require(key: PersistentKey<P, C>): C =
    get(key.key, key.type) ?: throw NoSuchElementException("Missing or incompatible persistent data: ${key.key}")
