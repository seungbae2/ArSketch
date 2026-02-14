package com.sb.arsketch.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    fun distanceTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val ZERO = Point3D(0f, 0f, 0f)
    }
}
