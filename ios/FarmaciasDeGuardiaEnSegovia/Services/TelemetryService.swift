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

import Foundation
import OpenTelemetryApi

/// Service for OpenTelemetry tracing and error recording
/// Provides convenient methods for creating spans and capturing errors for Grafana
class TelemetryService {
    static let shared = TelemetryService()

    private init() {}

    private var tracer: Tracer {
        OpenTelemetry.instance.tracerProvider.get(
            instrumentationName: "farmacias-guardia-segovia",
            instrumentationVersion: nil
        )
    }

    /// Start a new span (equivalent to Sentry transaction)
    /// - Parameters:
    ///   - name: Name of the span (e.g., "pdf.url.scraping")
    ///   - kind: SpanKind indicating the type of operation
    /// - Returns: Started span that must be ended with span.end()
    func startSpan(name: String, kind: SpanKind = .internal) -> Span {
        DebugConfig.debugPrint("📊 Starting span: \(name) (kind: \(kind))")
        let span = tracer.spanBuilder(spanName: name)
            .setSpanKind(spanKind: kind)
            .startSpan()
        DebugConfig.debugPrint("📊 Span started: \(name) with context: \(span.context)")
        return span
    }

    /// Record an error as an exception event
    /// - Parameters:
    ///   - error: The error to record
    ///   - attributes: Additional context attributes
    func recordError(_ error: Error, attributes: [String: AttributeValue] = [:]) {
        let span = tracer.spanBuilder(spanName: "error.capture")
            .setSpanKind(spanKind: .internal)
            .startSpan()

        // Add error attributes
        span.setAttribute(key: "error.type", value: String(describing: type(of: error)))
        span.setAttribute(key: "error.message", value: error.localizedDescription)

        // Add custom attributes
        for (key, value) in attributes {
            span.setAttribute(key: key, value: value)
        }

        // Record as exception event
        span.addEvent(name: "exception", attributes: [
            "exception.type": AttributeValue.string(String(describing: type(of: error))),
            "exception.message": AttributeValue.string(error.localizedDescription)
        ])

        span.status = .error(description: error.localizedDescription)
        span.end()
    }

    /// Record app launch event with platform and version information
    func recordAppLaunch() {
        // Create a zero-duration span for app launch
        let span = tracer.spanBuilder(spanName: "app.launch")
            .setSpanKind(spanKind: .internal)
            .startSpan()

        // Gather platform information
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
        let osVersion = ProcessInfo.processInfo.operatingSystemVersionString
        let platform = "iOS"

        // Add attributes to span
        span.setAttribute(key: "platform", value: platform)
        span.setAttribute(key: "app.version", value: appVersion)
        span.setAttribute(key: "app.build", value: buildNumber)
        span.setAttribute(key: "os.version", value: osVersion)

        // Add event for semantic clarity
        span.addEvent(name: "app.launched", attributes: [
            "platform": AttributeValue.string(platform),
            "app.version": AttributeValue.string(appVersion),
            "app.build": AttributeValue.string(buildNumber),
            "os.version": AttributeValue.string(osVersion)
        ])

        // Immediately end span (zero duration)
        span.end()

        DebugConfig.debugPrint("📱 App launch recorded: \(platform) \(appVersion) (\(buildNumber)) on \(osVersion)")
    }
}
