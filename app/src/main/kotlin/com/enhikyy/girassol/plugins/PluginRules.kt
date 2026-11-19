package com.enhikyy.girassol.plugins

/** Regras independentes da plataforma para nomes, extensões e limites do Coder. */
internal object PluginRules {
    const val MAX_SOURCE_CHARS = 64 * 1024
    const val HISTORY_LIMIT = 20

    private val namePattern = Regex("[A-Za-z][A-Za-z0-9_]{0,39}")
    private val versionPattern = Regex("[0-9]{13}-[0-9]{6}-[a-f0-9]{12}\\.src")
    private val extensions = setOf("lua", "js")

    fun isValidName(name: String): Boolean = namePattern.matches(name)

    fun isValidExtension(extension: String): Boolean = extension in extensions

    fun isValidSource(source: String): Boolean =
        source.length <= MAX_SOURCE_CHARS && '\u0000' !in source

    fun isValidVersionFileName(fileName: String): Boolean = versionPattern.matches(fileName)

    fun pluginId(name: String, extension: String): String = "$name.$extension"
}
