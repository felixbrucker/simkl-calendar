package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.sharedBoundsItem(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || sharedTransitionScope == null || animatedVisibilityScope == null || key.isBlank()) return this
    return with(sharedTransitionScope) {
        this@sharedBoundsItem.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

@Composable
fun Modifier.sharedElementItem(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
    enabled: Boolean = true
): Modifier {
    if (!enabled || sharedTransitionScope == null || animatedVisibilityScope == null || key.isBlank()) return this
    return with(sharedTransitionScope) {
        this@sharedElementItem.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}
