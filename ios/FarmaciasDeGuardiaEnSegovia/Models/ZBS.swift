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
        ZBS(id: "navas-asuncion", name: "Nava de la Asunción", icon: "🏘️"),
        ZBS(id: "villacastin", name: "Villacastín", icon: "🚂"),
        ZBS(id: "cantalejo", name: "Cantalejo", icon: "🏘️")
    ]
}
