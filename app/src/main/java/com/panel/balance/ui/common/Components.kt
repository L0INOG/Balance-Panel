package com.panel.balance.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 等宽数字（tnum），让余额数字刷新时不跳动。 */
fun tnum(style: TextStyle): TextStyle = style.copy(fontFeatureSettings = "tnum")

fun lighten(c: Color, fraction: Float = 0.35f): Color = lerp(c, Color.White, fraction)

/** 平台头像：主题色渐变圆 + 首字母。 */
@Composable
fun PlatformAvatar(name: String, color: Long, size: Dp = 40.dp) {
    val c = Color(color)
    Box(
        modifier = Modifier
            .size(size)
            .background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "·",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** 状态小圆点：true=正常 / false=异常 / null=未刷新。 */
@Composable
fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> Color(0xFF34C77B)
        false -> Color(0xFFE5484D)
        null -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(modifier.size(8.dp).background(color, CircleShape))
}
