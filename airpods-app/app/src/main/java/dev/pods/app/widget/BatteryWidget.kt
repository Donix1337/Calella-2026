package dev.pods.app.widget

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.pods.app.R
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSettings
import dev.pods.app.data.PodsState
import dev.pods.app.ui.MainActivity

class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryWidget()
}

/** One ring in the widget. */
private data class WidgetItem(
    val label: String,
    val icon: Int,
    val level: Int?,
    val charging: Boolean,
    val live: Boolean,
)

class BatteryWidget : GlanceAppWidget() {

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(160.dp, 160.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 230.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PodsSettings.init(context)
        PodsRepository.init(context)
        PodsRepository.refreshPhoneBattery(context)
        val phoneName = phoneName(context)
        provideContent {
            val state by PodsRepository.state.collectAsState()
            WidgetContent(state, phoneName)
        }
    }

    private fun phoneName(context: Context): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: "Phone"
}

private val Background = ColorProvider(day = Color.White, night = Color(0xFF1C1C1E))
private val Primary = ColorProvider(day = Color.Black, night = Color.White)
private val Secondary = ColorProvider(day = Color(0x993C3C43), night = Color(0x99EBEBF5))
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9500)
private val Red = Color(0xFFFF3B30)
private val Gray = Color(0xFF8E8E93)
private val Track = Color(0x388E8E93)

private fun items(state: PodsState, phoneName: String): List<WidgetItem> {
    val s = state.snapshot
    val live = state.isConnected
    val phone = WidgetItem(phoneName, R.drawable.ic_glyph_phone, state.phone.level, state.phone.charging, true)
    if (s != null && s.isHeadphones) {
        return listOf(
            WidgetItem(state.shortModelName, R.drawable.ic_glyph_headphones, s.single, s.singleCharging, live),
            phone,
        )
    }
    return listOf(
        WidgetItem("Left", R.drawable.ic_glyph_bud_left, s?.left, s?.leftCharging == true, live),
        WidgetItem("Right", R.drawable.ic_glyph_bud_right, s?.right, s?.rightCharging == true, live),
        WidgetItem("Case", R.drawable.ic_glyph_case, s?.case, s?.caseCharging == true, live && s?.caseFromMemory == false),
        phone,
    )
}

private fun ringColor(item: WidgetItem): Color = when {
    !item.live -> Gray
    item.charging -> Green
    (item.level ?: 100) <= 10 -> Red
    (item.level ?: 100) <= 20 -> Orange
    else -> Green
}

@Composable
private fun WidgetContent(state: PodsState, phoneName: String) {
    val size = LocalSize.current
    val all = items(state, phoneName)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(Background)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            size.width >= 240.dp && size.height >= 220.dp -> ListLayout(state, all)
            size.width >= 240.dp -> RowLayout(all)
            else -> GridLayout(all, showLabels = size.height >= 150.dp)
        }
    }
}

@Composable
private fun RowLayout(items: List<WidgetItem>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        items.forEach { item ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                RingWithLabel(item, ringSize = 54.dp, showLabel = true)
            }
        }
    }
}

@Composable
private fun GridLayout(items: List<WidgetItem>, showLabels: Boolean) {
    val ring = if (showLabels) 48.dp else 40.dp
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        items.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(GlanceModifier.height(if (showLabels) 6.dp else 10.dp))
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                row.forEach { item ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        RingWithLabel(item, ringSize = ring, showLabel = showLabels)
                    }
                }
            }
        }
    }
}

@Composable
private fun ListLayout(state: PodsState, items: List<WidgetItem>) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Text(
            text = state.shortModelName,
            style = TextStyle(color = Primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = if (state.isConnected) "Connected" else "Not connected",
            style = TextStyle(color = Secondary, fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        items.forEach { item ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Ring(item, 34.dp)
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = item.label,
                    style = TextStyle(color = Primary, fontSize = 14.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                LevelText(item, 14)
            }
        }
    }
}

@Composable
private fun RingWithLabel(item: WidgetItem, ringSize: Dp, showLabel: Boolean) {
    Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Ring(item, ringSize)
        if (showLabel) {
            Spacer(GlanceModifier.height(4.dp))
            LevelText(item, 12)
        }
    }
}

@Composable
private fun Ring(item: WidgetItem, ringSize: Dp) {
    val context = LocalContext.current
    val px = (ringSize.value * context.resources.displayMetrics.density).toInt()
    val ring = RingRenderer.render(
        sizePx = px,
        fraction = item.level?.let { it / 100f },
        color = ringColor(item).toArgb(),
        trackColor = Track.toArgb(),
    )
    Box(modifier = GlanceModifier.size(ringSize), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ring),
            contentDescription = null,
            modifier = GlanceModifier.size(ringSize),
        )
        Image(
            provider = ImageProvider(item.icon),
            contentDescription = item.label,
            modifier = GlanceModifier.size(ringSize * 0.46f),
            colorFilter = ColorFilter.tint(if (item.live) Primary else Secondary),
        )
    }
}

@Composable
private fun LevelText(item: WidgetItem, sizeSp: Int) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        if (item.charging && item.live && item.level != null) {
            Image(
                provider = ImageProvider(R.drawable.ic_glyph_bolt),
                contentDescription = "Charging",
                modifier = GlanceModifier.size((sizeSp - 1).dp),
                colorFilter = ColorFilter.tint(ColorProvider(Green)),
            )
        }
        Text(
            text = item.level?.let { "$it%" } ?: "–",
            style = TextStyle(
                color = if (item.live) Primary else Secondary,
                fontSize = sizeSp.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
