import Foundation

/// Represents pharmacy schedules organized by ZBS (Zona Básica de Salud)
public struct ZBSSchedule {
    /// The date this schedule applies to
    public let date: DutyDate
    
    /// Schedules organized by ZBS ID
    public let schedulesByZBS: [String: [Pharmacy]]
    
    public init(date: DutyDate, schedulesByZBS: [String: [Pharmacy]]) {
        self.date = date
        self.schedulesByZBS = schedulesByZBS
    }
    
    /// Get pharmacies for a specific ZBS
    public func pharmacies(for zbsId: String) -> [Pharmacy] {
        return schedulesByZBS[zbsId] ?? []
    }
    
    /// Get all available ZBS IDs for this date
    public var availableZBSIds: [String] {
        return Array(schedulesByZBS.keys).sorted()
    }
}

/// Service for managing ZBS-organized schedules
public class ZBSScheduleService {
    /// Get schedules organized by ZBS for a specific region
    /// - Parameter region: The region to get schedules for
    /// - Returns: Array of ZBS schedules, or nil if not applicable
    public static func getZBSSchedules(for region: Region) -> [ZBSSchedule]? {
        // Only applicable for Segovia Rural
        guard region == .segoviaRural else { return nil }
        
        // Get regular schedules and reorganize by ZBS
        let schedules = ScheduleService.loadSchedules(for: region)
        
        return convertToZBSSchedules(schedules)
    }
    
    private static func convertToZBSSchedules(_ schedules: [PharmacySchedule]) -> [ZBSSchedule] {
        var zbsSchedules: [ZBSSchedule] = []
        
        for schedule in schedules {
            // For Segovia Rural, all pharmacies are in fullDay shift
            let allPharmacies = schedule.shifts[.fullDay] ?? []
            
            // Group pharmacies by ZBS based on their additional info or name patterns
            var schedulesByZBS: [String: [Pharmacy]] = [:]
            
            for pharmacy in allPharmacies {
                let zbsId = detectZBSId(for: pharmacy)
                if !zbsId.isEmpty {
                    if schedulesByZBS[zbsId] == nil {
                        schedulesByZBS[zbsId] = []
                    }
                    schedulesByZBS[zbsId]?.append(pharmacy)
                }
            }
            
            if !schedulesByZBS.isEmpty {
                let zbsSchedule = ZBSSchedule(date: schedule.date, schedulesByZBS: schedulesByZBS)
                zbsSchedules.append(zbsSchedule)
            }
        }
        
        return zbsSchedules
    }
    
    private static func detectZBSId(for pharmacy: Pharmacy) -> String {
        let name = pharmacy.name.uppercased()
        
        // Map pharmacy names to ZBS IDs based on patterns from the PDF
        if name.contains("S.E. GORMAZ") || name.contains("SEPÚLVEDA") || name.contains("RIAZA") || name.contains("AYLLÓN") || name.contains("BOCEGUILLAS") || name.contains("CEREZO ABAJO") {
            return "riaza-sepulveda"
        } else if name.contains("LA GRANJA") {
            return "la-granja"
        } else if name.contains("PRÁDENA") || name.contains("NAVAFRÍA") || name.contains("TORREVAL") || name.contains("ARCONES") {
            return "la-sierra"
        } else if name.contains("FUENTIDUEÑA") || name.contains("SACRAMENIA") || name.contains("HONTALBILLA") || name.contains("TORRECILLA") || name.contains("FUENTESAUCO") || name.contains("OLOMBRADA") {
            return "fuentidueña"
        } else if name.contains("CARBONERO") || name.contains("NAVALMANZANO") || name.contains("ZARZUELA PINAR") || name.contains("ESCARABAJOSA") || name.contains("LASTRAS DE CUÉLLAR") || name.contains("FUENTEPELAYO") || name.contains("CANTIMPALOS") || name.contains("AGUILAFUENTE") || name.contains("ESCALONA") || name.contains("MOZONCILLO") {
            return "carbonero"
        } else if name.contains("NAVAS DE ORO") || name.contains("NIEVA") || name.contains("NAVA DE LA A") || name.contains("BERNARDOS") || name.contains("SANTIUSTE") || name.contains("STA. Mª REAL") || name.contains("COCA") {
            return "navas-asuncion"
        } else if name.contains("VILLACASTÍN") || name.contains("MAELLO") || name.contains("ZARZUELA M.") || name.contains("NAVAS DE SA") {
            return "villacastin"
        }
        
        return "" // Unknown ZBS
    }
}
