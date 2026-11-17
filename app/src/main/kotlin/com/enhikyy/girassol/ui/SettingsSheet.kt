package com.enhikyy.girassol.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enhikyy.girassol.MyApplication
import com.enhikyy.girassol.ai.ModelManager
import com.enhikyy.girassol.ai.ModelStatus
import com.enhikyy.girassol.theme.FilmThemes
import com.enhikyy.girassol.theme.ThemeManager

/** Qual painel o bottom sheet está a mostrar. */
private enum class SettingsPanel { MENU, THEMES, FONTS, MODELS }

/**
 * Botão de engrenagem da AppBar e o seu ModalBottomSheet.
 *
 * Dois painéis: film stocks Kodak (+ OLED) e as 35 fontes. Cada nome de fonte é
 * renderizado na própria fonte asiática — preview real, não uma lista de texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(SettingsPanel.MENU) }

    // ActivityResult para escolher arquivo .gguf (OpenDocument: o usuário escolhe no seletor do sistema)
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { ModelManager.importAndLoad(MyApplication.getInstance(), it) }
    }

    Box(modifier) {
        IconButton(onClick = { panel = SettingsPanel.MENU; open = true }) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Configurações",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(horizontal = 16.dp),
            ) {
                when (panel) {
                    SettingsPanel.MENU -> MenuPanel(
                        onThemes = { panel = SettingsPanel.THEMES },
                        onFonts = { panel = SettingsPanel.FONTS },
                        onModels = { panel = SettingsPanel.MODELS },
                        onClose = { open = false },
                    )
                    SettingsPanel.THEMES -> ThemePanel(onBack = { panel = SettingsPanel.MENU })
                    SettingsPanel.FONTS -> FontPanel(onBack = { panel = SettingsPanel.MENU })
                    SettingsPanel.MODELS -> ModelsPanel(
                        onPickFile = { pickFile.launch(arrayOf("*/*")) },
                        onBack = { panel = SettingsPanel.MENU },
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Voltar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuPanel(onThemes: () -> Unit, onFonts: () -> Unit, onModels: () -> Unit, onClose: () -> Unit) {
    PanelHeader("CONFIGURAÇÕES", "Aparência e modelos do Girassol", onClose)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuRow("Film Stocks", "${FilmThemes.ALL.size} paletas Kodak + OLED", onThemes)
        MenuRow("Tipografia", "${FontManager.ALL.size} fontes minimalistas", onFonts)
        MenuRow("Modelos", "Carregar modelo .gguf (llama.cpp)", onModels)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
    HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
}

@Composable
private fun ThemePanel(onBack: () -> Unit) {
    PanelHeader("FILM STOCKS", "Escolha a paleta de cores", onBack)

    val currentTheme by ThemeManager.currentPalette.collectAsState()

    LazyColumn(Modifier.fillMaxWidth()) {
        items(FilmThemes.ALL) { theme ->
            val selected = theme.id == currentTheme.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ThemeManager.setPalette(theme.id) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Amostra: fundo + primária + secundária.
                    Row {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(theme.background))
                        Box(
                            Modifier
                                .size(18.dp)
                                .padding(start = 2.dp)
                                .clip(CircleShape)
                                .background(theme.primary),
                        )
                    }
                    Text(
                        text = theme.label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Ativo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }
    }
}

@Composable
private fun FontPanel(onBack: () -> Unit) {
    val current by FontManager.current.collectAsState()

    PanelHeader("TIPOGRAFIA", "${FontManager.ALL.size} fontes · preview em tempo real", onBack)

    LazyColumn(Modifier.fillMaxWidth()) {
        items(FontManager.ALL) { font ->
            val selected = font.id == current.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { FontManager.select(font.id) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = font.displayName,
                    fontSize = 14.sp,
                    fontFamily = safeFamily(font),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Ativa",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
        }
    }
}

/** Painel de Modelos: observa [ModelManager.status] e permite carregar .gguf. */
@Composable
private fun ModelsPanel(
    onPickFile: () -> Unit,
    onBack: () -> Unit,
) {
    val engine = MyApplication.getInstance().llmEngine
    val status by ModelManager.status.collectAsState()

    val (statusText, statusColor) = when (val s = status) {
        is ModelStatus.Idle -> if (engine.isLoaded) {
            "Pronto: ${engine.modelPath?.substringAfterLast('/') ?: "modelo"}" to MaterialTheme.colorScheme.primary
        } else {
            "Nenhum modelo carregado" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        }
        is ModelStatus.Copying -> "Copiando modelo... ${(s.progress * 100).toInt()}%" to
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        is ModelStatus.Loading -> "Carregando modelo..." to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        is ModelStatus.Ready -> "Pronto: ${s.name}" to MaterialTheme.colorScheme.primary
        is ModelStatus.Error -> "Erro: ${s.message}" to MaterialTheme.colorScheme.error
    }
    val busy = status is ModelStatus.Copying || status is ModelStatus.Loading
    val copyProgress = (status as? ModelStatus.Copying)?.progress

    PanelHeader("MODELOS", "Carregar modelo GGUF (llama.cpp)", onBack)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status atual
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Status",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        color = statusColor,
                    )
                }
                if (status is ModelStatus.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (copyProgress != null) {
                LinearProgressIndicator(
                    progress = { copyProgress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }
        }

        HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)

        // Botão para escolher arquivo
        OutlinedButton(
            onClick = onPickFile,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = "Escolher arquivo .gguf",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Info sobre modelos
        Text(
            text = "Modelos GGUF (Q4_K_M recomendado) • 400 MB – 8 GB\nExemplos: Qwen2.5-0.5B, Gemma-2-2B, Llama-3.2-3B",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )

        // Se houver modelo, botão para descarregar
        if (engine.isLoaded && !busy) {
            HorizontalDivider(color = GirassolColors.CardBorder, thickness = 1.dp)
            OutlinedButton(
                onClick = { ModelManager.unload() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    text = "Descarregar modelo",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Resolve a família de uma fonte sem propagar falhas.
 *
 * Uma fonte do Google que não esteja em cache pode não carregar; nesse caso a
 * lista usa a família de fallback, em vez de rebentar a lista inteira.
 */
private fun safeFamily(font: GirassolFont): FontFamily =
    runCatching { FontManager.family(font) }.getOrDefault(font.fallback)
