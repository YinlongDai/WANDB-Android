package io.github.yinlongdai.runwatch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** The adaptive launcher icon under the usual launcher masks, big and at home-screen size. */
class IconScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, environment = testEnvironment())

    @Composable
    private fun Icon(shape: Shape, size: Dp, themed: Boolean = false) {
        // Adaptive layers are 108dp with the visible part in the middle 72dp.
        Box(Modifier.size(size).clip(shape).background(if (themed) Color(0xFFDCE4F7) else Color.Transparent), contentAlignment = Alignment.Center) {
            val layer = Modifier.size(size * 1.5f)
            if (!themed) Image(painterResource(R.drawable.ic_launcher_background), null, layer)
            Image(
                painterResource(if (themed) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground),
                null,
                layer,
                colorFilter = if (themed) ColorFilter.tint(Color(0xFF1F3A68)) else null,
            )
        }
    }

    @Test
    fun launcherIcon() {
        paparazzi.snapshot {
            Column(Modifier.background(Color(0xFF6E7B8B)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(CircleShape, 160.dp)
                    Icon(RoundedCornerShape(36.dp), 160.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CircleShape, 56.dp)
                    Icon(RoundedCornerShape(14.dp), 56.dp)
                    Icon(CircleShape, 56.dp, themed = true)
                    Icon(CircleShape, 40.dp)
                }
            }
        }
    }
}
