package io.github.Shai0717.kolor

import io.github.Shai0717.kolor.compat.kommand
import io.github.Shai0717.kolor.compat.EntityEvents
import io.github.Shai0717.kolor.compat.Ticker
import io.github.Shai0717.kolor.compat.persistentKey
import io.github.Shai0717.kolor.compat.require
import io.github.Shai0717.kolor.compat.set
import org.bukkit.Location
import org.bukkit.entity.TextDisplay
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class IntegrationProbe : JavaPlugin() {
    override fun onEnable() {
        kolorCommand("kolor-native-check") {
            intArg("value") { executes { logger.info("KOLOR_NATIVE_${it.int("value")}"); 1 } }
        }
        kommand {
            register("kolor-compat-check") {
                then("ping") { executes { logger.info("KOLOR_COMPAT_OK") } }
                then("repeat") {
                    then("amount" to int(1, 10), "message" to string()) {
                        executes { ctx ->
                            val amount: Int by ctx
                            val message: String = ctx["message"]
                            logger.info("KOLOR_COMPAT_ARGUMENT_${amount}_$message")
                        }
                    }
                }
            }
        }
        val scheduler = KolorScheduler(this)
        Ticker.global(this).runTask { logger.info("KOLOR_TICKER_OK") }
        scheduler.runGlobal { logger.info("KOLOR_GLOBAL_OK") }
        scheduler.runAsync { logger.info("KOLOR_ASYNC_OK") }
        val world = server.worlds.first()
        val location = Location(world, 0.0, 100.0, 0.0)
        with(scheduler) {
            location.runAt {
                logger.info("KOLOR_REGION_OK")
                val entity = world.spawn(location, TextDisplay::class.java)
                entity.runOn {
                    val key = persistentKey("probe", PersistentDataType.INTEGER)
                    entity.persistentDataContainer.set(key, 7)
                    check(entity.persistentDataContainer.require(key) == 7)
                    logger.info("KOLOR_PDC_OK")
                    val subscription = EntityEvents(this@IntegrationProbe).listen(
                        entity, EntitySpawnEvent::class.java, target = { it.entity }
                    ) { logger.info("KOLOR_EVENT_OK") }
                    server.pluginManager.callEvent(EntitySpawnEvent(entity))
                    subscription.close()
                    entity.transform()
                    entity.remove()
                    logger.info("KOLOR_ENTITY_OK")
                } ?: logger.severe("KOLOR_ENTITY_REJECTED")
            }
        }
    }
}
