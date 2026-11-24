package io.github.Shai0717.kolor

import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

interface KolorModule {
    val id: String
    fun onAttach(plugin: KolorPlugin)
    fun onDetach(plugin: KolorPlugin)
}

abstract class KolorPlugin : JavaPlugin() {
    private val modules = ConcurrentHashMap<String, KolorModule>()
    private val moduleLock = Any()
    private val attaching = mutableSetOf<String>()
    private var disabled = false

    fun module(id: String): KolorModule? = modules[id]

    fun attach(module: KolorModule) = synchronized(moduleLock) {
        val id = module.id
        require(id.isNotBlank()) { "Module id must not be blank" }
        check(!disabled) { "Plugin is disabled" }
        check(!modules.containsKey(id) && attaching.add(id)) { "Module already attached: $id" }
        try {
            module.onAttach(this)
            modules[id] = module
        } finally {
            attaching.remove(id)
        }
    }

    fun detach(id: String): Boolean = synchronized(moduleLock) {
        val module = modules[id] ?: return@synchronized false
        module.onDetach(this)
        modules.remove(id, module)
        true
    }

    final override fun onDisable() {
        synchronized(moduleLock) {
            disabled = true
            modules.values.toList().forEach { module ->
                try {
                    module.onDetach(this)
                } catch (exception: Exception) {
                    logger.log(java.util.logging.Level.SEVERE, "Could not detach module ${module.id}", exception)
                } finally {
                    modules.remove(module.id, module)
                }
            }
        }
        onKolorDisable()
    }

    protected open fun onKolorDisable() {}
}

class Kolor : KolorPlugin()
