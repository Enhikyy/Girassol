package dev.labforge.app.core.geometry

import java.io.File
import kotlin.math.*

/** Lightweight, dependency-free mesh model for OBJ and STL interchange. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun minus(o: Vec3) = Vec3(x-o.x, y-o.y, z-o.z)
    operator fun plus(o: Vec3) = Vec3(x+o.x, y+o.y, z+o.z)
    operator fun times(s: Float) = Vec3(x*s,y*s,z*s)
    fun normalized(): Vec3 {
        val n=sqrt(x*x+y*y+z*z)
        return if(n<1e-8f) Vec3(0f,0f,1f) else Vec3(x/n,y/n,z/n)
    }
    companion object { fun cross(a:Vec3,b:Vec3)=Vec3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x) }
}
data class Triangle(val a:Int,val b:Int,val c:Int)
data class Mesh(val vertices:List<Vec3>,val triangles:List<Triangle>) {
    init {
        require(vertices.isNotEmpty()) { "A malha não contém vértices" }
        require(triangles.isNotEmpty()) { "A malha não contém faces" }
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
    fun read(file: File): Mesh = when(file.extension.lowercase()) {
        "obj" -> readObj(file.readText())
        "stl" -> readStl(file.readBytes())
        else -> error("Formato não suportado: .${file.extension}; use OBJ ou STL")
    }
    fun readObj(source:String):Mesh {
        val vertices=mutableListOf<Vec3>(); val faces=mutableListOf<Triangle>()
        source.lineSequence().forEachIndexed { lineNo,line ->
            val p=line.trim().split(Regex("\\s+"))
            when(p.firstOrNull()) {
                "v" -> { require(p.size>=4){"Vértice OBJ inválido na linha ${lineNo+1}"}; vertices+=Vec3(p[1].toFloat(),p[2].toFloat(),p[3].toFloat()) }
                "f" -> {
                    require(p.size>=4){"Face OBJ inválida na linha ${lineNo+1}"}
                    val ids=p.drop(1).map { token ->
                        val raw=token.substringBefore('/').toInt()
                        if(raw>0) raw-1 else vertices.size+raw
                    }
                    require(ids.all{it in vertices.indices}){"Índice OBJ fora da faixa na linha ${lineNo+1}"}
                    for(i in 1 until ids.lastIndex) faces+=Triangle(ids[0],ids[i],ids[i+1])
                }
            }
        }
        return Mesh(vertices,faces)
    }
    fun readStl(bytes:ByteArray):Mesh {
        require(bytes.size>=15){"STL vazio ou truncado"}
        val binary=if(bytes.size>=84) {
            val count=java.nio.ByteBuffer.wrap(bytes,80,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            count>=0 && 84L+50L*count==bytes.size.toLong()
        } else false
        val verts=mutableListOf<Vec3>(); val tris=mutableListOf<Triangle>()
        if(binary) {
            val b=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN); b.position(84)
            val map=HashMap<Vec3,Int>()
            repeat((bytes.size-84)/50) {
                b.position(b.position()+12)
                val ids=IntArray(3)
                repeat(3) { val v=Vec3(b.float,b.float,b.float); ids[it]=map.getOrPut(v){verts.add(v);verts.lastIndex} }
                b.short
                tris+=Triangle(ids[0],ids[1],ids[2])
            }
        } else {
            val text=bytes.toString(Charsets.US_ASCII)
            val nums=Regex("(?i)vertex\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)").findAll(text)
            val map=HashMap<Vec3,Int>(); val group=mutableListOf<Int>()
            nums.forEach { m ->
                val v=Vec3(m.groupValues[1].toFloat(),m.groupValues[2].toFloat(),m.groupValues[3].toFloat())
                group+=map.getOrPut(v){verts.add(v);verts.lastIndex}
                if(group.size==3){tris+=Triangle(group[0],group[1],group[2]);group.clear()}
            }
            require(group.isEmpty()){"STL ASCII contém triângulo incompleto"}
        }
        return Mesh(verts,tris)
    }
    fun writeObj(mesh:Mesh):String=buildString {
        append("# Girassol native mesh exporter\n")
        mesh.vertices.forEach{append("v ${it.x} ${it.y} ${it.z}\n")}
        mesh.triangles.forEach{append("f ${it.a+1} ${it.b+1} ${it.c+1}\n")}
    }
    fun writeBinaryStl(mesh:Mesh):ByteArray {
        val b=java.nio.ByteBuffer.allocate(84+mesh.triangles.size*50).order(java.nio.ByteOrder.LITTLE_ENDIAN)
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
