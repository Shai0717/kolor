package io.github.Shai0717.kolor.compat

import io.github.Shai0717.kolor.KolorScheduler
import io.github.Shai0717.kolor.KolorTask
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val ACTIVE = 0
private const val CANCELLED = 1
private const val DONE = 2

class Ticker private constructor(
    private val schedule: (Long, Long, () -> Unit, () -> Unit) -> KolorTask?
) : AutoCloseable {
    private val tasks = ConcurrentHashMap.newKeySet<TickerTask>()
    private val lock = Any()
    private var closed = false

    fun runTask(
        delayTicks: Long = 0,
        retiredCallback: () -> Unit = {},
        task: () -> Unit
    ): TickerTask? = register(delayTicks, 0, retiredCallback, task)

    fun runTaskTimer(
        delayTicks: Long = 0,
        periodTicks: Long,
        retiredCallback: () -> Unit = {},
        task: () -> Unit
    ): TickerTask? {
        require(periodTicks > 0) { "Repeating period must be positive" }
        return register(delayTicks, periodTicks, retiredCallback, task)
    }

    private fun register(delay: Long, period: Long, retired: () -> Unit, action: () -> Unit): TickerTask? =
        synchronized(lock) {
            check(!closed) { "Ticker is closed" }
            require(delay >= 0) { "Delay must be non-negative" }
            val handle = TickerTask(period > 0) { tasks.remove(it) }
            tasks.add(handle)
            try {
                val delegate = schedule(delay, period, { handle.retire(retired) }) { handle.execute(action) }
                if (delegate == null) {
                    handle.reject()
                    null
                } else {
                    handle.bind(delegate)
                    handle
                }
            } catch (exception: Exception) {
                handle.cancel()
                throw exception
            }
        }

    fun cancelAll() = synchronized(lock) {
        tasks.toList().forEach { it.cancel() }
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            cancelAll()
        }
    }

    companion object {
        fun global(plugin: Plugin): Ticker {
            val scheduler = KolorScheduler(plugin)
            return Ticker { delay, period, _, action -> scheduler.runGlobal(delay, period, action) }
        }

        fun at(plugin: Plugin, location: Location): Ticker {
            val scheduler = KolorScheduler(plugin)
            val target = location.clone()
            return Ticker { delay, period, _, action ->
                with(scheduler) { target.runAt(delay, period, action) }
            }
        }

        fun on(plugin: Plugin, entity: Entity): Ticker {
            val scheduler = KolorScheduler(plugin)
            return Ticker { delay, period, retired, action ->
                with(scheduler) { entity.runOn(delay, period, retired, action) }
            }
        }
    }
}

class TickerTask internal constructor(
    private val repeating: Boolean,
    private val remove: (TickerTask) -> Unit
) {
    private val state = AtomicInteger(ACTIVE)
    private val delegate = AtomicReference<KolorTask?>()

    val isScheduled: Boolean get() = state.get() == ACTIVE
    val isCancelled: Boolean get() = state.get() == CANCELLED
    val isDone: Boolean get() = state.get() == DONE

    internal fun bind(task: KolorTask) {
        check(delegate.compareAndSet(null, task))
        if (isCancelled) task.cancel()
    }

    internal fun execute(action: () -> Unit) {
        if (!isScheduled) return
        try {
            action()
            if (!repeating && state.compareAndSet(ACTIVE, DONE)) remove(this)
        } catch (exception: Exception) {
            cancel()
            throw exception
        }
    }

    internal fun retire(callback: () -> Unit) {
        if (state.compareAndSet(ACTIVE, CANCELLED)) {
            remove(this)
            callback()
        }
    }

    internal fun reject() {
        if (state.compareAndSet(ACTIVE, CANCELLED)) remove(this)
    }

    fun cancel() {
        if (state.compareAndSet(ACTIVE, CANCELLED)) {
            delegate.get()?.cancel()
            remove(this)
        }
    }
}
