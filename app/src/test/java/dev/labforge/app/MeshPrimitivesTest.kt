package dev.labforge.app

import dev.labforge.app.core.geometry.MeshPrimitives
import org.junit.Assert.*
import org.junit.Test

class MeshPrimitivesTest {
    @Test fun cubeHasEightVerticesAndTwelveTriangles() {
        val cube = MeshPrimitives.cube(2f)
        assertEquals(8,cube.vertices.size)
        assertEquals(12,cube.triangles.size)
        val (lo,hi) = cube.bounds()
        assertEquals(-1f,lo.x,0.0001f)
        assertEquals(1f,hi.z,0.0001f)
    }

    @Test fun sphereHasExpectedClosedGridTopology() {
        val rings=8
        val segments=16
        val sphere=MeshPrimitives.uvSphere(2f,rings,segments)
        assertEquals(2+(rings-1)*segments,sphere.vertices.size)
        assertEquals(2*segments*(rings-1),sphere.triangles.size)
        assertTrue(sphere.vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        assertTrue(sphere.vertices.all { kotlin.math.abs(kotlin.math.sqrt(it.x*it.x+it.y*it.y+it.z*it.z)-2f)<0.0001f })
    }

    @Test fun rejectsInvalidOrExcessiveGeometry() {
        assertTrue(runCatching { MeshPrimitives.cube(0f) }.isFailure)
        assertTrue(runCatching { MeshPrimitives.uvSphere(rings=3,segments=2) }.isFailure)
        assertTrue(runCatching { MeshPrimitives.uvSphere(rings=256,segments=512) }.isFailure)
    }
}
