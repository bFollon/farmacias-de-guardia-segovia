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

/// Result of PDF URL validation
enum PDFURLValidationResult {
    case valid
    case invalid(reason: String)
    case offline
    case timeout

    var isValid: Bool {
        if case .valid = self {
            return true
        }
        return false
    }

    var errorMessage: String? {
        switch self {
        case .valid:
            return nil
        case .invalid(let reason):
            return reason
        case .offline:
            return "Sin conexión a Internet"
        case .timeout:
            return "Tiempo de espera agotado al verificar el enlace"
        }
    }
}

/// Service for validating PDF URLs before attempting to load them
class PDFURLValidator {
    static let shared = PDFURLValidator()

    private let session: URLSession
    private var validationCache: [URL: CachedValidation] = [:]
    private let cacheExpirationSeconds: TimeInterval = 3600 // 1 hour

    /// Cached validation result
    private struct CachedValidation {
        let result: PDFURLValidationResult
        let timestamp: TimeInterval

        func isExpired() -> Bool {
            let now = Date().timeIntervalSince1970
            return (now - timestamp) > 3600 // 1 hour
        }
    }

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 5.0
        config.timeoutIntervalForResource = 5.0
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        self.session = URLSession(configuration: config)
    }

    /// Check if a URL needs validation (not in cache or cache expired)
    /// - Parameter url: The URL to check
    /// - Returns: True if validation is needed
    func needsValidation(for url: URL) -> Bool {
        // Check if we're offline first
        if !NetworkMonitor.shared.isOnline {
            return false // No need to validate if offline
        }

        // Check cache
        if let cached = validationCache[url], !cached.isExpired() {
            return false // Cache hit, no validation needed
        }

        return true // Cache miss or expired, validation needed
    }

    /// Validate a PDF URL using HTTP HEAD request
    /// - Parameter url: The URL to validate
    /// - Returns: Validation result
    func validateURL(_ url: URL) async -> PDFURLValidationResult {
        // Check if we're offline first
        if !NetworkMonitor.shared.isOnline {
            DebugConfig.debugPrint("📄 PDFURLValidator: Offline, skipping validation for \(url.absoluteString)")
            return .offline
        }

        // Check cache first
        if let cached = validationCache[url], !cached.isExpired() {
            DebugConfig.debugPrint("📄 PDFURLValidator: Using cached validation result for \(url.absoluteString)")
            return cached.result
        }

        DebugConfig.debugPrint("📄 PDFURLValidator: Validating URL: \(url.absoluteString)")

        do {
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")

            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                let result = PDFURLValidationResult.invalid(reason: "Respuesta inválida del servidor")
                cacheValidationResult(url, result: result)
                DebugConfig.debugPrint("❌ PDFURLValidator: Invalid server response")
                return result
            }

            // Check HTTP status code
            guard (200...299).contains(httpResponse.statusCode) else {
                let result = PDFURLValidationResult.invalid(reason: "El archivo no está disponible (código \(httpResponse.statusCode))")
                cacheValidationResult(url, result: result)
                DebugConfig.debugPrint("❌ PDFURLValidator: HTTP \(httpResponse.statusCode)")
                return result
            }

            // Check Content-Type if available
            if let contentType = httpResponse.value(forHTTPHeaderField: "Content-Type")?.lowercased() {
                // Accept application/pdf or application/octet-stream (some servers use generic type)
                if !contentType.contains("pdf") && !contentType.contains("octet-stream") {
                    let result = PDFURLValidationResult.invalid(reason: "El archivo no es un PDF válido")
                    cacheValidationResult(url, result: result)
                    DebugConfig.debugPrint("❌ PDFURLValidator: Invalid content type: \(contentType)")
                    return result
                }
            }

            // URL is valid
            let result = PDFURLValidationResult.valid
            cacheValidationResult(url, result: result)
            DebugConfig.debugPrint("✅ PDFURLValidator: URL is valid")
            return result

        } catch let error as URLError {
            // Handle specific URL errors
            if error.code == .timedOut {
                let result = PDFURLValidationResult.timeout
                cacheValidationResult(url, result: result)
                DebugConfig.debugPrint("⏱️ PDFURLValidator: Timeout")
                return result
            } else if error.code == .notConnectedToInternet || error.code == .networkConnectionLost {
                DebugConfig.debugPrint("📴 PDFURLValidator: Network error: \(error.localizedDescription)")
                return .offline
            } else {
                let result = PDFURLValidationResult.invalid(reason: "Error de red: \(error.localizedDescription)")
                cacheValidationResult(url, result: result)
                DebugConfig.debugPrint("❌ PDFURLValidator: Network error: \(error.localizedDescription)")
                return result
            }
        } catch {
            let result = PDFURLValidationResult.invalid(reason: "Error al verificar el enlace: \(error.localizedDescription)")
            cacheValidationResult(url, result: result)
            DebugConfig.debugPrint("❌ PDFURLValidator: Unexpected error: \(error.localizedDescription)")
            return result
        }
    }

    /// Validate a PDF URL and return a working URL with fallback logic
    /// - Parameters:
    ///   - primaryURL: The primary URL to try first (usually scraped)
    ///   - fallbackURL: The fallback URL to try if primary fails
    /// - Returns: A tuple with the working URL (if any) and validation result
    func validateWithFallback(primaryURL: URL, fallbackURL: URL) async -> (workingURL: URL?, validationResult: PDFURLValidationResult) {
        // Try primary URL first
        let primaryResult = await validateURL(primaryURL)
        if primaryResult.isValid {
            DebugConfig.debugPrint("✅ PDFURLValidator: Primary URL is valid")
            return (primaryURL, primaryResult)
        }

        DebugConfig.debugPrint("⚠️ PDFURLValidator: Primary URL failed, trying fallback")

        // If primary fails and we're offline, don't try fallback
        if case .offline = primaryResult {
            return (nil, primaryResult)
        }

        // Try fallback URL
        let fallbackResult = await validateURL(fallbackURL)
        if fallbackResult.isValid {
            DebugConfig.debugPrint("✅ PDFURLValidator: Fallback URL is valid")
            return (fallbackURL, fallbackResult)
        }

        DebugConfig.debugPrint("❌ PDFURLValidator: Both URLs failed validation")
        return (nil, fallbackResult)
    }

    /// Cache a validation result
    private func cacheValidationResult(_ url: URL, result: PDFURLValidationResult) {
        let cached = CachedValidation(
            result: result,
            timestamp: Date().timeIntervalSince1970
        )
        validationCache[url] = cached
    }

    /// Clear all cached validation results
    func clearCache() {
        validationCache.removeAll()
        DebugConfig.debugPrint("🗑️ PDFURLValidator: Cleared validation cache")
    }

    /// Clear expired cache entries
    func clearExpiredCache() {
        let beforeCount = validationCache.count
        validationCache = validationCache.filter { !$0.value.isExpired() }
        let afterCount = validationCache.count
        if beforeCount != afterCount {
            DebugConfig.debugPrint("🗑️ PDFURLValidator: Cleared \(beforeCount - afterCount) expired cache entries")
        }
    }
}
