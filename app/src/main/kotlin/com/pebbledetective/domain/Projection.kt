package com.pebbledetective.domain

/**
 * The pinhole projection behind the journey scene.
 *
 * Camera sits at the origin looking down +Z. Anything at or behind
 * [NEAR_PLANE] is not drawn. Pure maths, no Android types, so the awkward
 * cases are unit testable.
 */
object Projection {

    const val FOCAL = 900f
    const val NEAR_PLANE = 1f

    fun isVisible(z: Float): Boolean = z > NEAR_PLANE

    fun screenX(x: Float, z: Float, centreX: Float, focal: Float = FOCAL): Float =
        centreX + focal * x / z

    fun screenY(y: Float, z: Float, centreY: Float, focal: Float = FOCAL): Float =
        centreY + focal * y / z

    /** Apparent radius of a sphere of [radius] at depth [z]. */
    fun screenRadius(radius: Float, z: Float, focal: Float = FOCAL): Float =
        focal * radius / z

    /**
     * Quadratic Bezier, used for the pebble's arc so it curves through space
     * instead of sliding down a straight line.
     */
    fun bezier(from: Float, control: Float, to: Float, t: Float): Float {
        val inv = 1f - t
        return inv * inv * from + 2f * inv * t * control + t * t * to
    }
}
