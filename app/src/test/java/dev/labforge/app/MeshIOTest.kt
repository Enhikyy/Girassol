package dev.labforge.app

import dev.labforge.app.core.geometry.MeshIO
import org.junit.Assert.*
import org.junit.Test

class MeshIOTest {
    private val cube = """
        v -1 -1 -1
        v 1 -1 -1
        v 1 1 -1
        v -1 1 -1
        v -1 -1 1
        v 1 -1 1
        v 1 1 1
        v -1 1 1
        f 1 2 3 4
        f 5 8 7 6
        f 1 5 6 2
        f 2 6 7 3
        f 3 7 8 4
        f 5 1 4 8
    """.trimIndent()

    @Test fun objPolygonsAreTriangulatedAndRoundTrip() {
        val mesh=MeshIO.readObj(cube)
        assertEquals(8,mesh.vertices.size)
        assertEquals(12,mesh.triangles.size)
        val again=MeshIO.readObj(MeshIO.writeObj(mesh))
        assertEquals(mesh.vertices,again.vertices)
        assertEquals(mesh.triangles,again.triangles)
    }

    @Test fun binaryStlRoundTrip() {
        val mesh=MeshIO.readObj(cube)
        val parsed=MeshIO.readStl(MeshIO.writeBinaryStl(mesh))
        assertEquals(mesh.triangles.size,parsed.triangles.size)
        assertTrue(parsed.vertices.isNotEmpty())
    }

    @Test fun negativeObjIndicesResolveFromEnd() {
        val mesh=MeshIO.readObj("v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3 -2 -1")
        assertEquals(1,mesh.triangles.size)
        assertEquals(2,mesh.triangles.single().c)
    }
}
