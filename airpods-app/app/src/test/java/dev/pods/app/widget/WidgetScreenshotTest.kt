package dev.pods.app.widget

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.BatteryManager
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.compose
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pods.app.data.DeviceInfo
import dev.pods.app.data.PhoneBattery
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSnapshot
import dev.pods.app.data.PodsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the home screen widget at its three layouts, light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class WidgetScreenshotTest {

    private val state = PodsState(
        snapshot = PodsSnapshot(
            modelId = 0x2014,
            left = 80, right = 70, case = 60,
            leftCharging = false, rightCharging = false, caseCharging = true,
            leftInEar = true, rightInEar = true,
            leftInCase = false, rightInCase = false,
            single = null, singleCharging = false,
            updatedAt = 0L,
        ),
        connected = DeviceInfo("Nik’s AirPods Pro", "00:11:22:33:44:55"),
        phone = PhoneBattery(18, false),
    )

    @Test
    fun widgetsLight() = render(dark = false)

    @Test
    fun widgetsDark() = render(dark = true)

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun render(dark: Boolean) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSystemService(BatteryManager::class.java)?.let {
            shadowOf(it).setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, 18)
        }
        Settings.Global.putString(context.contentResolver, Settings.Global.DEVICE_NAME, "Galaxy S24")
        PodsRepository.init(context)
        PodsRepository.replaceStateForPreview(state)

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = context.resources.displayMetrics.density
        val sizes = listOf(
            "small" to DpSize(170.dp, 170.dp),
            "wide" to DpSize(350.dp, 170.dp),
            "large" to DpSize(350.dp, 250.dp),
        )
        val root = FrameLayout(activity).apply { setBackgroundColor(if (dark) 0xFF2B3A55.toInt() else 0xFF9DB4D3.toInt()) }
        activity.setContentView(root)

        for ((name, size) in sizes) {
            val job = GlobalScope.async(Dispatchers.Default) { BatteryWidget().compose(context, size = size) }
            val deadline = System.currentTimeMillis() + 30_000
            while (!job.isCompleted && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            val remoteViews = job.getCompleted()

            root.removeAllViews()
            val frame = FrameLayout(activity).apply { setBackgroundColor(Color.TRANSPARENT) }
            val pad = (16 * density).toInt()
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            root.addView(frame, FrameLayout.LayoutParams(w + pad * 2, h + pad * 2))
            val widgetView: View = remoteViews.apply(activity, frame)
            frame.addView(widgetView, FrameLayout.LayoutParams(w, h).apply { setMargins(pad, pad, pad, pad) })
            shadowOf(Looper.getMainLooper()).idle()
            frame.captureRoboImage("build/screens/widget_${name}_${if (dark) "dark" else "light"}.png")
        }
    }
}
