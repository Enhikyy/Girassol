package dev.labforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.labforge.app.ui.theme.Lab

@Composable
fun PremiumToast(
    title: String,
    message: String,
    accent: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.linearGradient(listOf(Lab.BgElev.copy(alpha = 0.98f), Lab.SurfaceHigh.copy(alpha = 0.96f))))
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(19.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(35.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Lab.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(message, color = Lab.Muted, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 3)
        }
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Rounded.Close,
            "Dispensar notificação",
            tint = Lab.Muted,
            modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onDismiss).padding(7.dp),
        )
    }
}
