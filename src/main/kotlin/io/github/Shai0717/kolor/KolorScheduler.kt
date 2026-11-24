package io.github.Shai0717.kolor

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class KolorTask internal constructor(private val task: ScheduledTask) {
    fun cancel() { task.cancel() }
    val isCancelled: Boolean get() = task.isCancelled
}

private fun validateTiming(delayTicks: Long, periodTicks: Long) {
    require(delayTicks >= 0) { "Delay must be non-negative" }
    require(periodTicks >= 0) { "Period must be non-negative" }
}

class KolorScheduler(val plugin: Plugin) {
    fun runGlobal(delayTicks: Long = 0, periodTicks: Long = 0, task: () -> Unit): KolorTask {
        validateTiming(delayTicks, periodTicks)
        val scheduler = plugin.server.globalRegionScheduler
        val action = Consumer<ScheduledTask> { task() }
        val scheduled = when {
            periodTicks > 0 -> scheduler.runAtFixedRate(plugin, action, maxOf(1, delayTicks), periodTicks)
            delayTicks > 0 -> scheduler.runDelayed(plugin, action, delayTicks)
            else -> scheduler.run(plugin, action)
        }
        return KolorTask(scheduled)
    }

    fun runAsync(delayTicks: Long = 0, periodTicks: Long = 0, task: () -> Unit): KolorTask {
        validateTiming(delayTicks, periodTicks)
        val scheduler = plugin.server.asyncScheduler
        val action = Consumer<ScheduledTask> { task() }
        val scheduled = when {
            periodTicks > 0 -> scheduler.runAtFixedRate(
                plugin, action, Math.multiplyExact(delayTicks, 50), Math.multiplyExact(periodTicks, 50), TimeUnit.MILLISECONDS
            )
            delayTicks > 0 -> scheduler.runDelayed(plugin, action, Math.multiplyExact(delayTicks, 50), TimeUnit.MILLISECONDS)
            else -> scheduler.runNow(plugin, action)
        }
        return KolorTask(scheduled)
    }

    fun Location.runAt(delayTicks: Long = 0, periodTicks: Long = 0, task: () -> Unit): KolorTask {
        validateTiming(delayTicks, periodTicks)
        val scheduler = plugin.server.regionScheduler
        val location = clone()
        val action = Consumer<ScheduledTask> { task() }
        val scheduled = when {
            periodTicks > 0 -> scheduler.runAtFixedRate(plugin, location, action, maxOf(1, delayTicks), periodTicks)
            delayTicks > 0 -> scheduler.runDelayed(plugin, location, action, delayTicks)
            else -> scheduler.run(plugin, location, action)
        }
        return KolorTask(scheduled)
    }

    fun Entity.runOn(
        delayTicks: Long = 0,
        periodTicks: Long = 0,
        retiredCallback: () -> Unit = {},
        task: () -> Unit
    ): KolorTask? {
        validateTiming(delayTicks, periodTicks)
        val action = Consumer<ScheduledTask> { task() }
        val retired = Runnable { retiredCallback() }
        val scheduled = when {
            periodTicks > 0 -> scheduler.runAtFixedRate(plugin, action, retired, maxOf(1, delayTicks), periodTicks)
            delayTicks > 0 -> scheduler.runDelayed(plugin, action, retired, delayTicks)
            else -> scheduler.run(plugin, action, retired)
        }
        return scheduled?.let(::KolorTask)
    }
}
