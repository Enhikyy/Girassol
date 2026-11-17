package com.enhikyy.girassol.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFont

/** Uma das 35 famílias minimalistas oferecidas pelo FontManager. */
data class GirassolFont(
    val id: String,
    val displayName: String,
    /** Nome da família no catálogo do Google Fonts. */
    val googleName: String,
    /** Família nativa usada enquanto a fonte remota ainda não chegou. */
    val fallback: FontFamily = FontFamily.SansSerif,
)

/**
 * Catálogo e estado da fonte da interface.
 *
 * As fontes vêm do Google Fonts Provider (transferível, com cache do sistema).
 * Enquanto uma fonte não estiver disponível, o Compose recorre à [fallback] —
 * não há crash nem texto invisível.
 */
object FontManager {

    /** As 35 fontes minimalistas pedidas no design. */
    val ALL: List<GirassolFont> = listOf(
        GirassolFont("manrope", "Manrope", "Manrope"),
        GirassolFont("poppins", "Poppins", "Poppins"),
        GirassolFont("work_sans", "Work Sans", "Work Sans"),
        GirassolFont("karla", "Karla", "Karla"),
        GirassolFont("dongle", "Dongle", "Dongle"),
        GirassolFont("raleway", "Raleway", "Raleway"),
        GirassolFont("inconsolata", "Inconsolata", "Inconsolata", FontFamily.Monospace),
        GirassolFont("comfortaa", "Comfortaa", "Comfortaa"),
        GirassolFont("antic", "Antic", "Antic"),
        GirassolFont("actor", "Actor", "Actor"),
        GirassolFont("agadasima", "Agadasima", "Agadasima"),
        GirassolFont("hanken_grotesk", "Hanken Grotesk", "Hanken Grotesk"),
        GirassolFont("basic", "Basic", "Basic"),
        GirassolFont("cabin", "Cabin", "Cabin"),
        GirassolFont("cantarell", "Cantarell", "Cantarell"),
        GirassolFont("inter", "Inter", "Inter"),
        GirassolFont("hind", "Hind", "Hind"),
        GirassolFont("gowun_batang", "Gowun Batang", "Gowun Batang"),
        GirassolFont("jost", "Jost", "Jost"),
        GirassolFont("krub", "Krub", "Krub"),
        GirassolFont("lato", "Lato", "Lato"),
        GirassolFont("nunito", "Nunito", "Nunito"),
        GirassolFont("epilogue", "Epilogue", "Epilogue"),
        GirassolFont("open_sans", "Open Sans", "Open Sans"),
        GirassolFont("sintony", "Sintony", "Sintony"),
        GirassolFont("montserrat", "Montserrat", "Montserrat"),
        GirassolFont("rethink_sans", "Rethink Sans", "Rethink Sans"),
        GirassolFont("space_grotesk", "Space Grotesk", "Space Grotesk"),
        GirassolFont("geist", "Geist", "Geist"),
        GirassolFont("sofia_sans", "Sofia Sans", "Sofia Sans"),
        GirassolFont("source_serif_4", "Source Serif 4", "Source Serif 4", FontFamily.Serif),
        GirassolFont("scheherazade_new", "Scheherazade New", "Scheherazade New", FontFamily.Serif),
        GirassolFont("dm_sans", "DM Sans", "DM Sans"),
        GirassolFont("roboto", "Roboto", "Roboto"),
        GirassolFont("mulish", "Mulish", "Mulish"),
        GirassolFont("cormorant_garamond", "Cormorant Garamond", "Cormorant Garamond", FontFamily.Serif),
    )

    /** Fonte inicial da interface. */
    val DEFAULT: GirassolFont = ALL.first { it.id == "inter" }

    private val _current = MutableStateFlow(DEFAULT)
    val current: StateFlow<GirassolFont> = _current.asStateFlow()

    /** Seleciona a fonte pelo id; ids desconhecidos são ignorados. */
    fun select(fontId: String) {
        ALL.firstOrNull { it.id == fontId }?.let { _current.value = it }
    }

    /** Cache de FontFamily já resolvidas, por id. */
    private val cache = HashMap<String, FontFamily>()

    /** Provider do Google Fonts Provider, configurado com os certificados da app. */
    private val provider: GoogleFont.Provider by lazy {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = emptyList(),
        )
    }

    /**
     * Constrói a [FontFamily] da fonte indicada.
     *
     * Se o provider não estiver disponível no aparelho, o Compose usa a
     * família de fallback — o texto nunca desaparece.
     */
    fun family(font: GirassolFont): FontFamily = cache.getOrPut(font.id) {
        runCatching {
            FontFamily(
                GoogleFontFont(GoogleFont(font.googleName), provider, FontWeight.Normal, FontStyle.Normal),
                GoogleFontFont(GoogleFont(font.googleName), provider, FontWeight.Medium, FontStyle.Normal),
                GoogleFontFont(GoogleFont(font.googleName), provider, FontWeight.Bold, FontStyle.Normal),
            )
        }.getOrDefault(font.fallback)
    }
}

/** Fonte atual como State para Compose. */
@Composable
fun currentFont(): GirassolFont = FontManager.current.collectAsState().value

/** A [FontFamily] da fonte atual, pronta a usar em `TextStyle`. */
@Composable
fun currentFontFamily(): FontFamily = FontManager.family(currentFont())
