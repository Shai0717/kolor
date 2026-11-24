package io.github.Shai0717.kolor

import org.bukkit.command.CommandSender
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.BiFunction
import java.util.function.Predicate

object KolorInterop {
    @JvmStatic
    fun command(
        plugin: JavaPlugin,
        name: String,
        aliases: List<String>,
        permission: Predicate<CommandSender>,
        handler: BiFunction<CommandSender, Array<String>, Int>
    ) {
        plugin.kolorCommand(name, *aliases.toTypedArray()) {
            requires { permission.test(it) }
            executes { handler.apply(it.sender, emptyArray()) }
            greedyStringArg("args") {
                executes { handler.apply(it.sender, it.string("args").split(" ").toTypedArray()) }
            }
        }
    }

    @JvmStatic
    fun runGlobal(scheduler: KolorScheduler, delay: Long, period: Long, task: Runnable): KolorTask =
        scheduler.runGlobal(delay, period) { task.run() }

    @JvmStatic
    fun runAsync(scheduler: KolorScheduler, delay: Long, period: Long, task: Runnable): KolorTask =
        scheduler.runAsync(delay, period) { task.run() }

    @JvmStatic
    fun runAt(
        scheduler: KolorScheduler,
        location: Location,
        delay: Long,
        period: Long,
        task: Runnable
    ): KolorTask = with(scheduler) {
        location.runAt(delay, period) { task.run() }
    }

    @JvmStatic
    fun runOn(
        scheduler: KolorScheduler,
        entity: Entity,
        delay: Long,
        period: Long,
        retired: Runnable,
        task: Runnable
    ): KolorTask? = with(scheduler) {
        entity.runOn(delay, period, { retired.run() }) { task.run() }
    }
}
