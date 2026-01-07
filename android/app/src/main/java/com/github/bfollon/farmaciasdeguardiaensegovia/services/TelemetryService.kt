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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import android.content.pm.PackageManager
import com.github.bfollon.farmaciasdeguardiaensegovia.BuildConfig
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import java.util.concurrent.TimeUnit

/**
 * Service for OpenTelemetry tracing and error recording
 * Provides convenient methods for creating spans and capturing errors for Grafana
 * Equivalent to iOS TelemetryService
 */
object TelemetryService {

    private const val INSTRUMENTATION_NAME = "farmacias-guardia-segovia"

    private var openTelemetry: OpenTelemetry? = null
    private var tracer: Tracer? = null
    private var meter: Meter? = null
    private var metricReader: PeriodicMetricReader? = null
    private var appLaunchCounter: LongCounter? = null
    private var urlScrapingCounter: LongCounter? = null

    /**
     * Whether the telemetry service has been initialized
     */
    val isInitialized: Boolean
        get() = openTelemetry != null

    /**
     * Initialize the OpenTelemetry SDK
     * Only initializes if user has opted in to monitoring
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        if (openTelemetry != null) {
            DebugConfig.debugPrint("TelemetryService already initialized")
            return
        }

        if (!MonitoringPreferencesService.hasUserOptedIn()) {
            DebugConfig.debugPrint("OpenTelemetry (Grafana) monitoring disabled (user has not opted in)")
            return
        }

        try {
            // Get app version
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown"
            }

            // Determine environment
            val environment = if (BuildConfig.DEBUG) "debug" else "production"

            DebugConfig.debugPrint("Configuring Grafana OTLP HTTP exporter")
            DebugConfig.debugPrint("Endpoint: ${Secrets.signozEndpoint}")
            DebugConfig.debugPrint("Auth header: signoz-ingestion-key")

            // Create OTLP HTTP exporter
            val otlpExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(Secrets.signozEndpoint)
                .addHeader("signoz-ingestion-key", Secrets.signozIngestionKey)
                .setTimeout(10, TimeUnit.SECONDS)
                .build()

            DebugConfig.debugPrint("OTLP HTTP exporter created successfully")

            // Create resource with service attributes
            val resource = Resource.getDefault().merge(
                Resource.create(
                    Attributes.of(
                        AttributeKey.stringKey("service.name"), "farmacias-guardia-segovia",
                        AttributeKey.stringKey("service.version"), appVersion,
                        AttributeKey.stringKey("deployment.environment"), environment
                    )
                )
            )

            // Create tracer provider with simple span processor for immediate export
            val tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(otlpExporter))
                .setResource(resource)
                .build()

            // Build and register OpenTelemetry SDK
            openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build()

            // Get tracer
            tracer = openTelemetry?.getTracer(INSTRUMENTATION_NAME)

            DebugConfig.debugPrint("OpenTelemetry (Grafana) tracing initialized (user opted in)")

            // Configure metrics exporter
            DebugConfig.debugPrint("Configuring Grafana OTLP HTTP metrics exporter")

            val metricsEndpoint = Secrets.signozEndpoint.replace("/v1/traces", "/v1/metrics")
            DebugConfig.debugPrint("Metrics endpoint: $metricsEndpoint")

            val metricsExporter = OtlpHttpMetricExporter.builder()
                .setEndpoint(metricsEndpoint)
                .addHeader("signoz-ingestion-key", Secrets.signozIngestionKey)
                .setTimeout(10, TimeUnit.SECONDS)
                .build()

            DebugConfig.debugPrint("OTLP HTTP metrics exporter created successfully")

            // Create periodic metric reader (60s interval)
            metricReader = PeriodicMetricReader.builder(metricsExporter).build()

            // Create meter provider with shared resource
            val meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .setResource(resource)
                .build()

            // Rebuild OpenTelemetry SDK with both tracer and meter providers
            openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build()

            // Get meter and create counters
            meter = openTelemetry?.getMeter(INSTRUMENTATION_NAME)
            appLaunchCounter = meter?.counterBuilder("app.launches")?.build()
            urlScrapingCounter = meter?.counterBuilder("pdf.urls.scraped")?.build()

            DebugConfig.debugPrint("OpenTelemetry (Grafana) metrics initialized (user opted in)")

        } catch (e: Exception) {
            DebugConfig.debugError("Failed to initialize OpenTelemetry: ${e.message}", e)
            openTelemetry = null
            tracer = null
        }
    }

    /**
     * Start a new span
     * @param name Name of the span (e.g., "pdf.url.scraping")
     * @param kind SpanKind indicating the type of operation
     * @return Started span that must be ended with span.end(), or a no-op span if not initialized
     */
    fun startSpan(name: String, kind: SpanKind = SpanKind.INTERNAL): Span {
        val currentTracer = tracer
        if (currentTracer == null) {
            // Return a no-op span that does nothing
            return Span.getInvalid()
        }

        DebugConfig.debugPrint("Starting span: $name (kind: $kind)")
        val span = currentTracer.spanBuilder(name)
            .setSpanKind(kind)
            .startSpan()
        DebugConfig.debugPrint("Span started: $name with context: ${span.spanContext}")
        return span
    }

    /**
     * Record an error as an exception event
     * @param exception The exception to record
     * @param attributes Additional context attributes
     */
    fun recordError(exception: Exception, attributes: Map<String, Any> = emptyMap()) {
        val currentTracer = tracer ?: return

        val span = currentTracer.spanBuilder("error.capture")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()

        // Add error attributes
        span.setAttribute("error.type", exception.javaClass.simpleName)
        span.setAttribute("error.message", exception.message ?: "Unknown error")

        // Add custom attributes
        for ((key, value) in attributes) {
            when (value) {
                is String -> span.setAttribute(key, value)
                is Long -> span.setAttribute(key, value)
                is Double -> span.setAttribute(key, value)
                is Boolean -> span.setAttribute(key, value)
                else -> span.setAttribute(key, value.toString())
            }
        }

        // Record as exception event
        span.addEvent(
            "exception",
            Attributes.of(
                AttributeKey.stringKey("exception.type"), exception.javaClass.simpleName,
                AttributeKey.stringKey("exception.message"), exception.message ?: "Unknown error"
            )
        )

        span.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
        span.end()
    }

    /**
     * Record app launch event with platform and version information
     */
    fun recordAppLaunch(context: Context) {
        val currentTracer = tracer ?: return

        // Gather platform information
        val platform = "Android"
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

        val buildNumber = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

        val osVersion = android.os.Build.VERSION.RELEASE
        val environment = if (BuildConfig.DEBUG) "debug" else "production"

        // Create shared attributes for both span and metric
        val attributes = Attributes.of(
            AttributeKey.stringKey("platform"), platform,
            AttributeKey.stringKey("app.version"), appVersion,
            AttributeKey.stringKey("app.build"), buildNumber,
            AttributeKey.stringKey("os.version"), osVersion,
            AttributeKey.stringKey("environment"), environment
        )

        // Increment metrics counter
        appLaunchCounter?.add(1, attributes)
        DebugConfig.debugPrint("📊 Metrics counter incremented: app.launches = 1")

        // Create a zero-duration span for app launch
        val span = currentTracer.spanBuilder("app.launch")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()

        // Add attributes to span
        span.setAttribute("platform", platform)
        span.setAttribute("app.version", appVersion)
        span.setAttribute("app.build", buildNumber)
        span.setAttribute("os.version", osVersion)
        span.setAttribute("environment", environment)

        // Add event for semantic clarity
        span.addEvent("app.launched", attributes)

        // Immediately end span (zero duration)
        span.end()

        DebugConfig.debugPrint("📱 App launch recorded: $platform $appVersion ($buildNumber) on Android $osVersion")
    }

    /**
     * Record URL scraping event with region and status
     * @param region Region identifier (e.g., "segovia-capital", "cuellar")
     * @param status Scraping status ("success" or "failure")
     */
    fun recordURLScraping(region: String, status: String) {
        val platform = "Android"
        val environment = if (BuildConfig.DEBUG) "debug" else "production"

        val attributes = Attributes.of(
            AttributeKey.stringKey("region"), region,
            AttributeKey.stringKey("status"), status,
            AttributeKey.stringKey("platform"), platform,
            AttributeKey.stringKey("environment"), environment
        )

        urlScrapingCounter?.add(1, attributes)
        DebugConfig.debugPrint("📊 URL scraping recorded: region=$region, status=$status")
    }

    /**
     * Force flush metrics immediately (useful after splash screen)
     */
    fun forceFlushMetrics() {
        val reader = metricReader
        if (reader == null) {
            DebugConfig.debugPrint("⚠️ No metric reader available for flush")
            return
        }

        DebugConfig.debugPrint("📤 Force flushing metrics...")
        val result = reader.forceFlush()
        if (result.isSuccess) {
            DebugConfig.debugPrint("✅ Metrics flushed successfully")
        } else {
            DebugConfig.debugPrint("❌ Metrics flush failed")
        }
    }

    /**
     * Shutdown the telemetry service gracefully
     */
    fun shutdown() {
        (openTelemetry as? OpenTelemetrySdk)?.let { sdk ->
            sdk.sdkTracerProvider.shutdown()
            sdk.sdkMeterProvider.shutdown()
        }
        openTelemetry = null
        tracer = null
        meter = null
        metricReader = null
        appLaunchCounter = null
        urlScrapingCounter = null
        DebugConfig.debugPrint("TelemetryService shutdown")
    }
}
