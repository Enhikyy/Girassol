package dev.labforge.app.core.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic, bounded procedural meshes for local 3D authoring. */
object MeshPrimitives {
    fun cube(size: Float = 1f): Mesh {
        require(size.isFinite() && size > 0f && size <= 10_000f) { "Tamanho do cubo inválido" }
        val h = size / 2f
        val vertices = listOf(
            Vec3(-h,-h,-h), Vec3(h,-h,-h), Vec3(h,h,-h), Vec3(-h,h,-h),
            Vec3(-h,-h,h), Vec3(h,-h,h), Vec3(h,h,h), Vec3(-h,h,h)
        )
        val triangles = listOf(
            Triangle(0,2,1), Triangle(0,3,2),
            Triangle(4,5,6), Triangle(4,6,7),
            Triangle(0,1,5), Triangle(0,5,4),
            Triangle(1,2,6), Triangle(1,6,5),
            Triangle(2,3,7), Triangle(2,7,6),
            Triangle(3,0,4), Triangle(3,4,7)
        )
        return Mesh(vertices,triangles)
    }

    /**
     * UV sphere with two unique poles and (rings-1) latitude rings.
     * rings is the number of vertical bands; segments is the number of longitude sectors.
     */
    fun uvSphere(radius: Float = 1f, rings: Int = 12, segments: Int = 24): Mesh {
        require(radius.isFinite() && radius > 0f && radius <= 10_000f) { "Raio da esfera inválido" }
        require(rings in 3..256) { "Rings deve estar entre 3 e 256" }
        require(segments in 3..512) { "Segments deve estar entre 3 e 512" }
        val vertexCount = 2L + (rings - 1L) * segments
        val triangleCount = 2L * segments * (rings - 1L)
        require(vertexCount <= 100_000 && triangleCount <= 100_000) { "Esfera excede o orçamento de geometria" }

        val vertices = ArrayList<Vec3>(vertexCount.toInt())
        vertices += Vec3(0f,radius,0f)
        for (ring in 1 until rings) {
            val phi = PI * ring / rings
            val y = (cos(phi) * radius).toFloat()
            val ringRadius = (sin(phi) * radius).toFloat()
            for (segment in 0 until segments) {
                val theta = 2.0 * PI * segment / segments
                vertices += Vec3(
                    (ringRadius * cos(theta)).toFloat(),
                    y,
                    (ringRadius * sin(theta)).toFloat()
                )
            }
        }
        val bottom = vertices.size
        vertices += Vec3(0f,-radius,0f)

        fun index(ring: Int, segment: Int) = 1 + (ring - 1) * segments + (segment % segments)
        val triangles = ArrayList<Triangle>(triangleCount.toInt())

        for (segment in 0 until segments) {
            val next = (segment + 1) % segments
            triangles += Triangle(0,index(1,segment),index(1,next))
        }
        for (ring in 1 until rings - 1) {
            for (segment in 0 until segments) {
                val next = (segment + 1) % segments
                val a = index(ring,segment)
                val b = index(ring,next)
                val c = index(ring + 1,segment)
                val d = index(ring + 1,next)
                triangles += Triangle(a,c,b)
                triangles += Triangle(b,c,d)
            }
        }
        val lastRing = rings - 1
        for (segment in 0 until segments) {
            val next = (segment + 1) % segments
            triangles += Triangle(index(lastRing,segment),bottom,index(lastRing,next))
        }
        return Mesh(vertices,triangles)
    }
}
