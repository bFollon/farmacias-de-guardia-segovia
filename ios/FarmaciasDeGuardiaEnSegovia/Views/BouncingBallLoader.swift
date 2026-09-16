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

import SwiftUI

/// Keyframe values for `BouncingBallLoader`'s ball: horizontal position plus a squash-and-stretch
/// scale pulse timed to hit as the ball touches each end of the track.
private struct BouncingBallKeyframes {
    var x: CGFloat = 0
    var scaleX: CGFloat = 1
    var scaleY: CGFloat = 1
}

/// Small indeterminate indicator shown while the app is contacting the server: a dot bouncing back
/// and forth along a track, with a brief squash at each end, flanked by a phone and a cloud glyph.
/// When `hasError` is true, the ball is replaced with a blinking red X centered on the track, so a
/// fetch timeout/failure (or being fully offline) reads as distinct from "still loading".
struct BouncingBallLoader: View {
    var color: Color = .accentColor
    var hasError: Bool = false

    @State private var errorBlinkVisible = false

    private let trackWidth: CGFloat = 64
    private let trackHeight: CGFloat = 16
    private let ballDiameter: CGFloat = 8
    private let legDuration: TimeInterval = 0.7

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "iphone")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(color)

            trackView

            Image(systemName: "cloud.fill")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(color)
        }
    }

    private var trackView: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(color.opacity(0.25))
                .frame(width: trackWidth - ballDiameter, height: 2)
                .frame(width: trackWidth, height: trackHeight)

            Circle()
                .fill(color)
                .frame(width: ballDiameter, height: ballDiameter)
                .opacity(hasError ? 0 : 1)
                .keyframeAnimator(
                    initialValue: BouncingBallKeyframes(),
                    repeating: true
                ) { content, value in
                    content
                        .scaleEffect(x: value.scaleX, y: value.scaleY)
                        .offset(x: value.x)
                } keyframes: { _ in
                    KeyframeTrack(\.x) {
                        CubicKeyframe(0, duration: 0)
                        CubicKeyframe(trackWidth - ballDiameter, duration: legDuration)
                        CubicKeyframe(0, duration: legDuration)
                    }
                    // Flatten along the direction of travel (horizontal) and bulge perpendicular
                    // (vertical) — matches a ball bouncing off a wall it's moving into, not one
                    // dropping onto a floor.
                    KeyframeTrack(\.scaleX) {
                        CubicKeyframe(0.78, duration: 0)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(0.78, duration: 0.08)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(0.78, duration: 0.08)
                    }
                    KeyframeTrack(\.scaleY) {
                        CubicKeyframe(1.22, duration: 0)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(1.22, duration: 0.08)
                        CubicKeyframe(1, duration: 0.08)
                        CubicKeyframe(1, duration: legDuration - 0.16)
                        CubicKeyframe(1.22, duration: 0.08)
                    }
                }

            if hasError {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .heavy))
                    .foregroundStyle(.red)
                    .opacity(errorBlinkVisible ? 1 : 0.25)
                    .frame(width: trackWidth, alignment: .center)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 0.45).repeatForever(autoreverses: true)) {
                            errorBlinkVisible = true
                        }
                    }
            }
        }
        .frame(width: trackWidth, height: trackHeight)
    }
}
