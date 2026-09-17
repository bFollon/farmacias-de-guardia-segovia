/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Small indeterminate indicator shown while the app is contacting the server: a dot bouncing back
 * and forth along a track, with a brief squash at each end, flanked by a phone and a cloud glyph.
 * When [hasError] is true, the ball is replaced with a blinking red X centered on the track, so a
 * fetch timeout/failure (or being fully offline) reads as distinct from "still loading".
 */
@Composable
fun BouncingBallLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    hasError: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "bouncingBall")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bouncingBallProgress",
    )

    // Squash-and-stretch pulse as the ball touches each end of the track — driven by the same
    // progress value as the bounce itself, so it's tied to the turnaround, not an independent clock.
    val edgeProximity = min(progress, 1f - progress)
    val squashWindow = 0.12f
    val squash = 1f - (edgeProximity / squashWindow).coerceIn(0f, 1f)

    val errorTransition = rememberInfiniteTransition(label = "bouncingBallError")
    val errorAlpha by errorTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bouncingBallErrorAlpha",
    )

    val errorColor = MaterialTheme.colorScheme.error

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )

        Canvas(modifier = Modifier.size(width = 64.dp, height = 13.dp)) {
            val ballRadius = size.height / 2f - 2.dp.toPx()
            val trackInset = ballRadius + 2.dp.toPx()
            val trackY = size.height / 2f

            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(trackInset, trackY),
                end = Offset(size.width - trackInset, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            if (hasError) {
                // Drawn by hand (not Icons.Default.Close) so the stroke can be made bold
                // enough to actually read at this size.
                val crossRadius = ballRadius + 1.dp.toPx()
                val crossCenter = Offset(size.width / 2f, trackY)
                val crossColor = errorColor.copy(alpha = errorAlpha)
                val crossStroke = 2.5.dp.toPx()
                drawLine(
                    color = crossColor,
                    start = Offset(crossCenter.x - crossRadius, crossCenter.y - crossRadius),
                    end = Offset(crossCenter.x + crossRadius, crossCenter.y + crossRadius),
                    strokeWidth = crossStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = crossColor,
                    start = Offset(crossCenter.x - crossRadius, crossCenter.y + crossRadius),
                    end = Offset(crossCenter.x + crossRadius, crossCenter.y - crossRadius),
                    strokeWidth = crossStroke,
                    cap = StrokeCap.Round,
                )
            } else {
                val ballX = trackInset + (size.width - 2 * trackInset) * progress
                // Flatten along the direction of travel (horizontal) and bulge perpendicular
                // (vertical) — matches a ball bouncing off a wall it's moving into, not one
                // dropping onto a floor.
                scale(scaleX = 1f - squash * 0.12f, scaleY = 1f + squash * 0.12f, pivot = Offset(ballX, trackY)) {
                    drawCircle(color = color, radius = ballRadius, center = Offset(ballX, trackY))
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}
