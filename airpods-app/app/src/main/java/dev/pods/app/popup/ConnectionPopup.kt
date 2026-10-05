package dev.pods.app.popup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pods.app.data.PodsState
import dev.pods.app.ui.components.PodsComponentsRow
import dev.pods.app.ui.components.PrimaryButton
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsType
import dev.pods.app.ui.theme.SquircleShape

@Composable
fun ConnectionPopup(
    state: PodsState,
    visibleState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = Pods.colors
    val shape = SquircleShape(34.dp)

    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        Box(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp, top = 24.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(if (colors.isDark) colors.elevated else colors.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpen,
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        text = state.deviceName,
                        style = PodsType.title3,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 40.dp, vertical = 6.dp),
                    )
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.fill)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.secondaryLabel, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                PodsComponentsRow(snapshot = state.snapshot, live = true, compact = true)
                Spacer(Modifier.height(20.dp))
                PrimaryButton(text = "Done", onClick = onDismiss)
            }
        }
    }
}
