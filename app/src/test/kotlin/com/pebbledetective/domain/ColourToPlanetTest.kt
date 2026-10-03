package com.pebbledetective.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DominantColourTest {

    private fun solid(argb: Int, size: Int = 32) = IntArray(size * size) { argb }

    @Test
    fun `reads a solid red patch as red`() {
        val colour = dominantColour(solid(0xFFE02020.toInt()), 32, 32, 16, 16, 12)
        assertTrue("hue was ${colour.hue}", colour.hue < 15f || colour.hue > 345f)
        assertTrue(colour.saturation > 0.7f)
    }

    @Test
    fun `reads a grey patch as unsaturated`() {
        val colour = dominantColour(solid(0xFF888888.toInt()), 32, 32, 16, 16, 12)
        assertTrue("saturation was ${colour.saturation}", colour.saturation < 0.05f)
    }

    /**
     * The headline trap: hue is circular. A patch of 350 and 10 degree pixels
     * is red, but a naive mean of the two gives 180 - cyan.
     */
    @Test
    fun `averages hues across the wrap point without landing on cyan`() {
        val pixels = IntArray(32 * 32) { i ->
            if (i % 2 == 0) 0xFFE02040.toInt() else 0xFFE04020.toInt()
        }
        val colour = dominantColour(pixels, 32, 32, 16, 16, 15)
        assertTrue(
            "hue ${colour.hue} drifted toward cyan",
            colour.hue < 40f || colour.hue > 320f,
        )
    }

    /**
     * Wet pebbles blow out to specular white. Those pixels must not decide
     * the colour, or every shiny rock reads the same.
     */
    @Test
    fun `ignores blown-out highlights and deep shadow`() {
        val pixels = IntArray(32 * 32) { i ->
            when {
                i % 3 == 0 -> 0xFFFFFFFF.toInt()   // specular highlight
                i % 3 == 1 -> 0xFF050505.toInt()   // shadow
                else -> 0xFF2060D0.toInt()         // the actual pebble
            }
        }
        val colour = dominantColour(pixels, 32, 32, 16, 16, 15)
        assertTrue("hue was ${colour.hue}", colour.hue in 200f..240f)
        assertTrue("saturation was ${colour.saturation}", colour.saturation > 0.5f)
    }

    @Test
    fun `only samples inside the disc around the tap`() {
        // Red everywhere except a blue square in the top-left corner.
        val pixels = IntArray(64 * 64) { i ->
            val x = i % 64
            val y = i / 64
            if (x < 10 && y < 10) 0xFF2060D0.toInt() else 0xFFE02020.toInt()
        }
        // Tap bottom-right: the blue corner must not reach the result.
        val colour = dominantColour(pixels, 64, 64, 50, 50, 8)
        assertTrue("hue was ${colour.hue}", colour.hue < 15f || colour.hue > 345f)
    }
}

class PlanetPickerTest {

    private fun hsv(h: Float, s: Float = 0.7f, v: Float = 0.7f) = Hsv(h, s, v)

    @Test
    fun `red goes to Mars and orange to Jupiter`() {
        assertEquals(Planet.MARS, pickPlanet(hsv(5f)))
        assertEquals(Planet.MARS, pickPlanet(hsv(355f)))
        assertEquals(Planet.JUPITER, pickPlanet(hsv(25f)))
    }

    @Test
    fun `bright yellow is the Sun but dim yellow is Venus`() {
        assertEquals(Planet.SUN, pickPlanet(hsv(50f, v = 0.9f)))
        assertEquals(Planet.VENUS, pickPlanet(hsv(50f, v = 0.6f)))
    }

    @Test
    fun `cool hues split between Uranus and Neptune`() {
        assertEquals(Planet.URANUS, pickPlanet(hsv(120f)))
        assertEquals(Planet.URANUS, pickPlanet(hsv(190f)))
        assertEquals(Planet.NEPTUNE, pickPlanet(hsv(230f)))
    }

    @Test
    fun `very dark rock is Mercury regardless of hue`() {
        assertEquals(Planet.MERCURY, pickPlanet(hsv(200f, v = 0.1f)))
        assertEquals(Planet.MERCURY, pickPlanet(hsv(10f, v = 0.05f)))
    }

    @Test
    fun `pale gold is Saturn`() {
        assertEquals(Planet.SATURN, pickPlanet(hsv(40f, s = 0.15f, v = 0.7f)))
    }

    /** The brief: grey or unknown pebbles pick a planet at random. */
    @Test
    fun `grey picks at random and never lands on Earth`() {
        val grey = hsv(0f, s = 0.01f, v = 0.5f)
        val seen = (0 until 400).map { pickPlanet(grey, Random(it)) }.toSet()
        assertTrue("random picked only $seen", seen.size > 3)
        assertTrue("Earth must never be a source", Planet.EARTH !in seen)
    }

    /** Earth is the destination, so nothing may select it. */
    @Test
    fun `Earth is never chosen for any colour`() {
        for (hue in 0 until 360 step 5) {
            for (saturation in listOf(0.0f, 0.15f, 0.5f, 1.0f)) {
                for (value in listOf(0.05f, 0.5f, 0.95f)) {
                    assertNotEquals(
                        "hue=$hue s=$saturation v=$value chose Earth",
                        Planet.EARTH,
                        pickPlanet(Hsv(hue.toFloat(), saturation, value), Random(1)),
                    )
                }
            }
        }
    }

    /**
     * A grey pebble's planet must be stable: the logbook reopens entries, and
     * a different answer each time would read as the app being broken.
     */
    @Test
    fun `the same seed always yields the same planet`() {
        val grey = hsv(0f, s = 0.01f, v = 0.5f)
        val first = pickPlanet(grey, Random(42))
        repeat(20) { assertEquals(first, pickPlanet(grey, Random(42))) }
    }

    @Test
    fun `every source body is reachable`() {
        val reached = mutableSetOf<Planet>()
        for (hue in 0 until 360) {
            for (saturation in listOf(0.05f, 0.15f, 0.6f)) {
                for (value in listOf(0.1f, 0.6f, 0.9f)) {
                    reached += pickPlanet(Hsv(hue.toFloat(), saturation, value), Random(7))
                }
            }
        }
        assertEquals(Planet.sources.toSet(), reached)
    }
}
