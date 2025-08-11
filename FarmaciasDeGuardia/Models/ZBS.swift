import Foundation

/// Represents a Zona Básica de Salud (Basic Health Zone) for Segovia Rural
public struct ZBS: Identifiable {
    /// Unique identifier for the ZBS
    public let id: String
    
    /// Display name of the ZBS
    public let name: String
    
    /// Emoji icon for the ZBS
    public let icon: String
    
    public init(id: String, name: String, icon: String) {
        self.id = id
        self.name = name
        self.icon = icon
    }
}

extension ZBS {
    /// Available ZBS options for Segovia Rural
    public static let availableZBS: [ZBS] = [
        ZBS(id: "riaza-sepulveda", name: "Riaza / Sepúlveda", icon: "🏔️"),
        ZBS(id: "la-granja", name: "La Granja", icon: "🏰"),
        ZBS(id: "la-sierra", name: "La Sierra", icon: "⛰️"),
        ZBS(id: "fuentidueña", name: "Fuentidueña", icon: "🏞️"),
        ZBS(id: "carbonero", name: "Carbonero", icon: "🌲"),
        ZBS(id: "navas-asuncion", name: "Navas de la Asunción", icon: "🏘️"),
        ZBS(id: "villacastin", name: "Villacastín", icon: "🚂")
    ]
}
