package dev.labforge.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.components.Segmented
import dev.labforge.app.ui.theme.Lab
import dev.labforge.app.vm.AgentViewModel
import dev.labforge.app.vm.LabViewModel

@Composable
fun LabScreen(vm: LabViewModel, agentVm: AgentViewModel, initialTab: Int) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(18.dp))
            Text("Laboratório", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Lab.Text)
            Text("quantização, fine-tuning e importance matrix", fontSize = 11.sp, color = Lab.Muted)
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("Quantizar", "Treinar", "imatrix", "Autoestudo"), tab, Modifier.fillMaxWidth()) { tab = it }
            Spacer(Modifier.height(4.dp))
        }
        when (tab) {
            0 -> QuantizePane(vm)
            1 -> TrainPane(vm)
            2 -> ImatrixPane(vm)
            else -> AutodidactPane(vm, onTrain = { tab = 1 })
        }
    }
}
