package com.enhikyy.girassol.core

import com.enhikyy.girassol.AppLogger
import java.io.File
import java.security.MessageDigest

/** SHA-256 de um ficheiro, lido em blocos de 1 MiB. */
fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().buffered(1 shl 20).use { ins ->
        val buf = ByteArray(1 shl 20)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Cofre de snapshots de pesos (adapters LoRA, deltas).
 *
 * Antes de qualquer auto-modificação dos pesos guarda-se uma cópia com o
 * respetivo hash. O rollback verifica o hash antes de repor, para nunca
 * restaurar um snapshot corrompido.
 */
class WeightVault(private val root: File) {
    data class Snapshot(val dir: File, val sha: String, val tag: String)

    init {
        root.mkdirs()
    }

    fun snapshot(adapter: File, tag: String): Snapshot {
        val sha = adapter.sha256()
        val dest = File(root, "${System.currentTimeMillis()}_${tag}_${sha.take(12)}/${adapter.name}")
        dest.parentFile!!.mkdirs()
        adapter.copyTo(dest, overwrite = false)
        AppLogger.log("INFO", "VAULT", "snapshot '$tag' criado: ${dest.parentFile!!.name}")
        return Snapshot(dest.parentFile!!, sha, tag)
    }

    fun rollback(target: File, snap: Snapshot) {
        val src = File(snap.dir, target.name)
        require(src.sha256() == snap.sha) { "snapshot corrompido: ${snap.dir}" }
        src.copyTo(target, overwrite = true)
        AppLogger.log("WARN", "VAULT", "rollback '$snap.tag' aplicado em ${target.name}")
    }
}
