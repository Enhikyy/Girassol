package dev.labforge.app.core.geometry

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

private const val MAX_MESH_INPUT_BYTES = 32 * 1024 * 1024
private const val MAX_MESH_VERTICES = 100_000
private const val MAX_MESH_TRIANGLES = 100_000
private const val MAX_FACE_VERTICES = 256

/** Lightweight, bounded mesh model for OBJ and STL interchange. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x-o.x, y-o.y, z-o.z)
    operator fun plus(o: Vec3) = Vec3(x+o.x, y+o.y, z+o.z)
    operator fun times(s: Float) = Vec3(x*s,y*s,z*s)
    fun normalized(): Vec3 {
        val n=sqrt(x*x+y*y+z*z)
        return if(!n.isFinite() || n<1e-8f) Vec3(0f,0f,1f) else Vec3(x/n,y/n,z/n)
    }
    companion object { fun cross(a:Vec3,b:Vec3)=Vec3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x) }
}
data class Triangle(val a:Int,val b:Int,val c:Int)
data class Mesh(val vertices:List<Vec3>,val triangles:List<Triangle>) {
    init {
        require(vertices.isNotEmpty()) { "A malha não contém vértices" }
        require(vertices.size <= MAX_MESH_VERTICES) { "A malha excede o limite de $MAX_MESH_VERTICES vértices" }
        require(triangles.isNotEmpty()) { "A malha não contém faces" }
        require(triangles.size <= MAX_MESH_TRIANGLES) { "A malha excede o limite de $MAX_MESH_TRIANGLES triângulos" }
        require(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) { "A malha contém coordenadas não finitas" }
        require(triangles.all { it.a in vertices.indices && it.b in vertices.indices && it.c in vertices.indices }) { "Índice de face inválido" }
    }
    fun bounds(): Pair<Vec3,Vec3> {
        val lo=Vec3(vertices.minOf{it.x},vertices.minOf{it.y},vertices.minOf{it.z})
        val hi=Vec3(vertices.maxOf{it.x},vertices.maxOf{it.y},vertices.maxOf{it.z})
        return lo to hi
    }
    fun normalized(): Mesh {
        val (lo,hi)=bounds(); val center=(lo+hi)*0.5f
        val extent=max(hi.x-lo.x,max(hi.y-lo.y,hi.z-lo.z)).coerceAtLeast(1e-6f)
        return copy(vertices=vertices.map{(it-center)*(2f/extent)})
    }
}

object MeshIO {
    fun read(file: File): Mesh {
        require(file.isFile) { "Arquivo de malha não encontrado" }
        require(file.length() in 1..MAX_MESH_INPUT_BYTES.toLong()) {
            "Arquivo de malha inválido ou maior que ${MAX_MESH_INPUT_BYTES / (1024 * 1024)} MiB"
        }
        return when(file.extension.lowercase()) {
            "obj" -> readObj(file.readText())
            "stl" -> readStl(file.readBytes())
            else -> error("Formato não suportado: .${file.extension}; use OBJ ou STL")
        }
    }

    fun readObj(source:String):Mesh {
        require(source.length <= MAX_MESH_INPUT_BYTES) { "OBJ excede o limite de entrada" }
        val vertices=mutableListOf<Vec3>(); val faces=mutableListOf<Triangle>()
        source.lineSequence().forEachIndexed { lineNo,line ->
            val trimmed=line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachIndexed
            val p=trimmed.split(Regex("\\s+"))
            when(p.firstOrNull()) {
                "v" -> {
                    require(p.size>=4) { "Vértice OBJ inválido na linha ${lineNo+1}" }
                    require(vertices.size < MAX_MESH_VERTICES) { "OBJ excede o limite de vértices" }
                    val v=Vec3(p[1].toFloat(),p[2].toFloat(),p[3].toFloat())
                    require(v.x.isFinite() && v.y.isFinite() && v.z.isFinite()) { "Coordenada OBJ não finita na linha ${lineNo+1}" }
                    vertices+=v
                }
                "f" -> {
                    require(p.size>=4 && p.size<=MAX_FACE_VERTICES+1) { "Face OBJ inválida ou grande demais na linha ${lineNo+1}" }
                    val ids=p.drop(1).map { token ->
                        val raw=token.substringBefore('/').toInt()
                        require(raw != 0) { "Índice OBJ zero na linha ${lineNo+1}" }
                        if(raw>0) raw-1 else vertices.size+raw
                    }
                    require(ids.all{it in vertices.indices}) { "Índice OBJ fora da faixa na linha ${lineNo+1}" }
                    val generated=ids.size-2
                    require(faces.size.toLong()+generated <= MAX_MESH_TRIANGLES) { "OBJ excede o limite de triângulos" }
                    for(i in 1 until ids.lastIndex) faces+=Triangle(ids[0],ids[i],ids[i+1])
                }
            }
        }
        return Mesh(vertices,faces)
    }

    fun readStl(bytes:ByteArray):Mesh {
        require(bytes.size in 15..MAX_MESH_INPUT_BYTES) {
            "STL inválido ou maior que ${MAX_MESH_INPUT_BYTES / (1024 * 1024)} MiB"
        }
        val binary=if(bytes.size>=84) {
            val count=ByteBuffer.wrap(bytes,80,4).order(ByteOrder.LITTLE_ENDIAN).int
            count>=0 && 84L+50L*count==bytes.size.toLong()
        } else false
        val verts=mutableListOf<Vec3>(); val tris=mutableListOf<Triangle>()
        if(binary) {
            val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); b.position(84)
            val map=HashMap<Vec3,Int>()
            val count=(bytes.size-84)/50
            require(count<=MAX_MESH_TRIANGLES) { "STL excede o limite de triângulos" }
            repeat(count) {
                b.position(b.position()+12)
                val ids=IntArray(3)
                repeat(3) {
                    val v=Vec3(b.float,b.float,b.float)
                    require(v.x.isFinite() && v.y.isFinite() && v.z.isFinite()) { "STL contém coordenada não finita" }
                    ids[it]=map.getOrPut(v) {
                        require(verts.size < MAX_MESH_VERTICES) { "STL excede o limite de vértices únicos" }
                        verts.add(v); verts.lastIndex
                    }
                }
                b.short
                tris+=Triangle(ids[0],ids[1],ids[2])
            }
        } else {
            val text=bytes.toString(Charsets.US_ASCII)
            val nums=Regex("(?i)vertex\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)").findAll(text)
            val map=HashMap<Vec3,Int>(); val group=mutableListOf<Int>()
            nums.forEach { m ->
                val v=Vec3(m.groupValues[1].toFloat(),m.groupValues[2].toFloat(),m.groupValues[3].toFloat())
                require(v.x.isFinite() && v.y.isFinite() && v.z.isFinite()) { "STL contém coordenada não finita" }
                group+=map.getOrPut(v) {
                    require(verts.size < MAX_MESH_VERTICES) { "STL excede o limite de vértices únicos" }
                    verts.add(v); verts.lastIndex
                }
                if(group.size==3) {
                    require(tris.size < MAX_MESH_TRIANGLES) { "STL excede o limite de triângulos" }
                    tris+=Triangle(group[0],group[1],group[2]); group.clear()
                }
            }
            require(group.isEmpty()) { "STL ASCII contém triângulo incompleto" }
        }
        return Mesh(verts,tris)
    }

    fun writeObj(mesh:Mesh):String=buildString {
        append("# Girassol native mesh exporter\n")
        mesh.vertices.forEach{append("v ${it.x} ${it.y} ${it.z}\n")}
        mesh.triangles.forEach{append("f ${it.a+1} ${it.b+1} ${it.c+1}\n")}
    }

    fun writeBinaryStl(mesh:Mesh):ByteArray {
        val capacity=Math.addExact(84,Math.multiplyExact(mesh.triangles.size,50))
        val b=ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)
        b.put("Girassol native STL".toByteArray(Charsets.US_ASCII)); while(b.position()<80)b.put(0)
        b.putInt(mesh.triangles.size)
        mesh.triangles.forEach { t ->
            val a=mesh.vertices[t.a]; val c=mesh.vertices[t.b]; val d=mesh.vertices[t.c]
            val n=Vec3.cross(c-a,d-a).normalized(); b.putFloat(n.x).putFloat(n.y).putFloat(n.z)
            listOf(a,c,d).forEach{b.putFloat(it.x).putFloat(it.y).putFloat(it.z)}
            b.putShort(0)
        }
        return b.array()
    }
}
