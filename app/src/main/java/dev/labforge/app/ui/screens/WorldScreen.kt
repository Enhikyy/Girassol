package dev.labforge.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.labforge.app.core.world.WorldRuntime
import dev.labforge.app.service.GirassolService
import dev.labforge.app.ui.Routes
import dev.labforge.app.ui.world.FilamentWorldSurface
import kotlin.math.roundToInt

@Composable
fun WorldScreen(onNavigate: (String) -> Unit) {
    val context=LocalContext.current
    val world by WorldRuntime.snapshot.collectAsState()
    var enabled by remember { mutableStateOf(WorldRuntime.isEnabled(context)) }

    LaunchedEffect(Unit) {
        WorldRuntime.initialize(context)
        enabled=WorldRuntime.isEnabled(context)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF070908))) {
        AndroidView(factory={ FilamentWorldSurface(it) },modifier=Modifier.fillMaxSize())

        Column(
            modifier=Modifier.fillMaxWidth().align(Alignment.TopStart).padding(18.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp),
        ) {
            Text("GIRASSOL  /  WORLD ENGINE",color=Color(0xFFF2C14E),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
            Text("Um mundo em atividade.",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text("FILAMENT 3D  ·  VULKAN PREFERENCIAL  ·  SIMULAÇÃO LOCAL",color=Color.White.copy(alpha=0.62f),fontSize=9.sp,letterSpacing=1.sp)
        }

        Surface(
            modifier=Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(14.dp),
            color=Color(0xE6111512),
            shape=RoundedCornerShape(22.dp),
            border=BorderStroke(1.dp,Color.White.copy(alpha=0.10f)),
        ) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CAMPUS GIRASSOL",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                        Text(world.residents.size.toString()+" agentes · "+world.eventCount+" mudanças de comportamento",color=Color(0xFFB9C1B5),fontSize=10.sp)
                    }
                    Text(if(enabled) "● AO VIVO" else "○ PAUSADO",color=if(enabled) Color(0xFF84D98A) else Color(0xFFF2C14E),fontSize=10.sp,fontWeight=FontWeight.Bold)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Metric("ENERGIA",world.averageEnergy,Modifier.weight(1f))
                    Metric("SOCIAL",world.averageSocial,Modifier.weight(1f))
                    Metric("CONHECIMENTO",world.averageKnowledge,Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick={
                            if(enabled) GirassolService.stopWorld(context) else GirassolService.startWorld(context)
                            enabled=!enabled
                        },
                        modifier=Modifier.weight(1f),
                    ) { Text(if(enabled) "Pausar simulação" else "Iniciar simulação") }
                    OutlinedButton(onClick={ onNavigate(Routes.CODEX) }) { Text("Codex") }
                    OutlinedButton(onClick={ onNavigate(Routes.AGENT) }) { Text("Chat") }
                }
                Text(
                    "Tempo simulado: "+(world.simulatedMinutes/60)+" h · Agente com maior conhecimento: "+(world.residents.maxByOrNull { it.knowledge }?.name ?: "—"),
                    color=Color(0xFF929A91),fontSize=9.sp,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String,value: Float,modifier: Modifier) {
    Column(
        modifier=modifier.background(Color(0xFF1B211C),RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(4.dp),
    ) {
        Text(label,color=Color(0xFF9DA99D),fontSize=8.sp,letterSpacing=0.8.sp,maxLines=1)
        Text(value.roundToInt().toString()+"%",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
    }
}
