//
//  ShiftInfoContent.swift
//  FarmaciasDeGuardiaEnSegovia
//
//  Copyright (C) 2024 Bruno Follón
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

import SwiftUI

/// Protocol for providing explanatory content about duty shifts
public protocol ShiftInfoContent {
    var title: String { get }
    func bodyContent(for date: Date) -> AnyView
}

/// Info content for Segovia Capital day/night shifts
public struct CapitalShiftInfo: ShiftInfoContent {
    let shiftType: DutyDate.ShiftType

    public init(shiftType: DutyDate.ShiftType) {
        self.shiftType = shiftType
    }

    public var title: String {
        "Horarios de Guardia"
    }

    public func bodyContent(for date: Date) -> AnyView {
        let isEarlyMorning: Bool = {
            let hour = Calendar.current.component(.hour, from: Date())
            let minute = Calendar.current.component(.minute, from: Date())
            return hour < 10 || (hour == 10 && minute < 15)
        }()

        let isCurrentDay = Calendar.current.isDateInToday(date)

        if shiftType == .day {
            return AnyView(
                Text("El turno diurno empieza a las 10:15 y se extiende hasta las 22:00 del mismo día.")
                    .multilineTextAlignment(.leading)
            )
        } else {
            return AnyView(
                VStack(alignment: .leading, spacing: 8) {
                    Text("El turno nocturno empieza a las 22:00 y se extiende hasta las 10:15 del día siguiente.")
                        .multilineTextAlignment(.leading)

                    if isCurrentDay && isEarlyMorning {
                        Text("Por ello, la farmacia que está de guardia ahora comenzó su turno ayer a las 22:00.")
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }
                }
            )
        }
    }
}
