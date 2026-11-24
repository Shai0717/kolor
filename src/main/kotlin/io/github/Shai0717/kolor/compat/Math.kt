package io.github.Shai0717.kolor.compat

import io.github.Shai0717.kolor.lookTarget
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector

operator fun Vector.plus(other: Vector): Vector = clone().add(other)
operator fun Vector.minus(other: Vector): Vector = clone().subtract(other)
operator fun Vector.times(scale: Double): Vector = clone().multiply(scale)
operator fun Vector.div(scale: Double): Vector {
    require(scale != 0.0) { "Cannot divide a vector by zero" }
    return clone().multiply(1.0 / scale)
}
operator fun Vector.unaryMinus(): Vector = clone().multiply(-1)
operator fun Location.plus(offset: Vector): Location = clone().add(offset)
operator fun Location.minus(offset: Vector): Location = clone().subtract(offset)
operator fun Location.minus(other: Location): Vector {
    require(world == other.world) { "Locations must be in the same world" }
    return toVector().subtract(other.toVector())
}

fun Player.raycast(maxDistance: Double): RayTraceResult? = lookTarget(maxDistance)
