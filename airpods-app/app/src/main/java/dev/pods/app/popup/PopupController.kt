package dev.pods.app.popup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.pods.app.bluetooth.Permissions
import dev.pods.app.data.PodsRepository
import dev.pods.app.ui.MainActivity
import dev.pods.app.ui.theme.PodsTheme

/**
 * The iPhone-style card that slides up when your AirPods connect. Drawn as an
 * overlay window, which needs the "display over other apps" permission.
 */
class PopupController(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var visibility: MutableTransitionState<Boolean>? = null
    private val autoDismiss = Runnable { dismiss() }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null || !Permissions.canDrawOverlays(context)) return
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return

        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val state = MutableTransitionState(false).apply { targetState = true }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                PodsTheme {
                    val podsState by PodsRepository.state.collectAsStateWithLifecycle()
                    ConnectionPopup(
                        state = podsState,
                        visibleState = state,
                        onDismiss = { dismiss() },
                        onOpen = {
                            dismiss()
                            context.startActivity(
                                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                    )
                }
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) dismiss()
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            title = "Pods connection pop-up"
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            lifecycleOwner.destroy()
            return
        }
        view = composeView
        owner = lifecycleOwner
        visibility = state
        handler.postDelayed(autoDismiss, 8_000)
    }

    fun dismiss(animated: Boolean = true) {
        handler.removeCallbacks(autoDismiss)
        val current = view ?: return
        val currentOwner = owner
        view = null
        owner = null
        visibility?.targetState = false
        visibility = null
        val remove = Runnable {
            try {
                context.getSystemService(WindowManager::class.java)?.removeView(current)
            } catch (e: Exception) {
                // Already gone.
            }
            currentOwner?.destroy()
        }
        if (animated) handler.postDelayed(remove, 350) else remove.run()
    }
}

private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
