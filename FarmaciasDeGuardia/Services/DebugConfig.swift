import Foundation

/// Global debug configuration for the entire application
struct DebugConfig {
    /// Master debug flag - controls all debug output in the application
    /// Can be overridden by the DEBUG_ENABLED environment variable
    static var isDebugEnabled: Bool = {
        // Check environment variable first
        if let envValue = ProcessInfo.processInfo.environment["DEBUG_ENABLED"] {
            return envValue.lowercased() == "true" || envValue == "1"
        }
        
        // Default to true for debugging the closest pharmacy feature
        #if DEBUG
        return true  // Enabled for debugging
        #else
        return false
        #endif
    }()
    
    /// Conditional debug print - only prints when debug is enabled
    /// - Parameter message: The message to print
    static func debugPrint(_ message: String) {
        if isDebugEnabled {
            print("[DEBUG] \(message)")
        }
    }
    
    /// Conditional debug print with format - only prints when debug is enabled
    /// - Parameters:
    ///   - format: The format string
    ///   - arguments: The arguments for the format string
    static func debugPrint(_ format: String, _ arguments: CVarArg...) {
        if isDebugEnabled {
            print("[DEBUG] \(String(format: format, arguments: arguments))")
        }
    }
    
    /// Enable debug mode programmatically
    static func enableDebug() {
        isDebugEnabled = true
    }
    
    /// Disable debug mode programmatically
    static func disableDebug() {
        isDebugEnabled = false
    }
    
    /// Get current debug status
    static func getDebugStatus() -> Bool {
        return isDebugEnabled
    }
}
