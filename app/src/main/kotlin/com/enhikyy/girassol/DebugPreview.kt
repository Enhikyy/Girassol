package com.enhikyy.girassol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enhikyy.girassol.ui.KodakFilm

/**
 * Previews de diagnóstico para isolar a camada preta (tela cheia).
 * Verificam que nenhuma camada de efeito apaga o conteúdo que envolve.
 */

/** Controle: camada de teste vermelha, sem filtro. Deve render vermelho + "TESTE". */
@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 780)
@Composable
private fun CamadaControlePreview() {
    Box(Modifier.fillMaxSize().background(Color.Red)) {
        Text("TESTE", color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}

/** Camada vermelha COM o filtro Kodak aplicado. O vermelho tem de continuar visível. */
@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 780)
@Composable
private fun CamadaKodakSobrepostaPreview() {
    Box(Modifier.fillMaxSize().background(Color.Red)) {
        KodakFilm(enabled = true) {
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                Text(
                    "TESTE",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
                )
            }
        }
    }
}

/** Hierarquia real do app (referência). */
@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 780)
@Composable
private fun GirassolAppDebugPreview() {
    GirassolApp()
}
