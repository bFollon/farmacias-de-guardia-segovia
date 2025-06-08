//
//  Item.swift
//  FarmaciasDeGuardia
//
//  Created by Bruno Follon on 16/5/25.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
