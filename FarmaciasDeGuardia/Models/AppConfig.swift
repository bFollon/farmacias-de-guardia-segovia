import Foundation

/// Global application configuration
struct AppConfig {
    /// Contact email for error reporting, feedback, and general inquiries
    static let contactEmail = "alive.intake_0b@icloud.com"
    
    /// Pre-formatted mailto URLs for common contact purposes
    struct EmailLinks {
        /// For reporting errors in pharmacy schedule data
        static func errorReport(subject: String = "Error en Farmacias de Guardia", body: String = "") -> URL? {
            let finalBody = body.isEmpty ? defaultErrorReportBody : body
            let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let encodedBody = finalBody.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            return URL(string: "mailto:\(AppConfig.contactEmail)?subject=\(encodedSubject)&body=\(encodedBody)")
        }
        
        /// For feedback and feature requests
        static func feedback(subject: String = "Sugerencias y mejoras - Farmacias de Guardia", body: String = "Me gustaría sugerir...") -> URL? {
            let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let encodedBody = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            return URL(string: "mailto:\(AppConfig.contactEmail)?subject=\(encodedSubject)&body=\(encodedBody)")
        }
        
        /// For general contact
        static func general(subject: String = "Contacto - Farmacias de Guardia", body: String = "") -> URL? {
            let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let encodedBody = body.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            return URL(string: "mailto:\(AppConfig.contactEmail)?subject=\(encodedSubject)&body=\(encodedBody)")
        }
        
        /// Generate error report email body for daily schedule errors
        static func dayScheduleErrorBody(date: String, dayPharmacyName: String, dayPharmacyAddress: String, nightPharmacyName: String, nightPharmacyAddress: String) -> String {
            return """
Hola,

He encontrado un error en las farmacias de guardia mostradas para:

Fecha: \(date)

Turno diurno:
Farmacia mostrada: \(dayPharmacyName)
Dirección: \(dayPharmacyAddress)

Turno nocturno:
Farmacia mostrada: \(nightPharmacyName)
Dirección: \(nightPharmacyAddress)

La información correcta es:


Gracias.
"""
        }
        
        /// Generate error report email body for ZBS schedule errors
        static func zbsScheduleErrorBody(date: String, zbsName: String, pharmacyName: String, pharmacyAddress: String) -> String {
            return """
Hola,

He encontrado un error en la farmacia de guardia mostrada para:

Fecha: \(date)
ZBS: \(zbsName)
Farmacia mostrada: \(pharmacyName)
Dirección: \(pharmacyAddress)

La farmacia correcta es:


Gracias.
"""
        }
        
        /// Default body template for error reports
        private static let defaultErrorReportBody = """
Hola,

He encontrado un error en la información mostrada en la app.

Detalles del error:


La información correcta es:


Gracias.
"""
    }
}
