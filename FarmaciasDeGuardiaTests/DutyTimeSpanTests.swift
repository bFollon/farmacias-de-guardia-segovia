import XCTest
@testable import FarmaciasDeGuardia

final class DutyTimeSpanTests: XCTestCase {
    func testTimeSpanSameDay() {
        // Test a simple time span within the same day (10:15 - 22:00)
        let span = DutyTimeSpan(startHour: 10, startMinute: 15, endHour: 22, endMinute: 0)
        
        XCTAssertFalse(span.spansMultipleDays)
        XCTAssertEqual(span.displayName, "10:15 - 22:00")
        
        // Test contains() with various times
        let calendar = Calendar.current
        let now = Date()
        let baseDate = calendar.startOfDay(for: now)
        
        // 10:00 - Before start
        let beforeStart = calendar.date(bySettingHour: 10, minute: 0, second: 0, of: now)!
        XCTAssertFalse(span.contains(beforeStart))
        
        // 10:15 - Exactly at start
        let atStart = calendar.date(bySettingHour: 10, minute: 15, second: 0, of: now)!
        XCTAssertTrue(span.contains(atStart))
        
        // 15:00 - Middle of span
        let middle = calendar.date(bySettingHour: 15, minute: 0, second: 0, of: now)!
        XCTAssertTrue(span.contains(middle))
        
        // 22:00 - Exactly at end
        let atEnd = calendar.date(bySettingHour: 22, minute: 0, second: 0, of: now)!
        XCTAssertTrue(span.contains(atEnd))
        
        // 22:01 - After end
        let afterEnd = calendar.date(bySettingHour: 22, minute: 1, second: 0, of: now)!
        XCTAssertFalse(span.contains(afterEnd))
    }
    
    func testTimeSpanOvernight() {
        // Test a time span that crosses midnight (22:00 - 10:15)
        let span = DutyTimeSpan(startHour: 22, startMinute: 0, endHour: 10, endMinute: 15)
        
        XCTAssertTrue(span.spansMultipleDays)
        XCTAssertEqual(span.displayName, "22:00 - 10:15")
        
        let calendar = Calendar.current
        let now = Date()
        
        // 21:59 - Before start
        let beforeStart = calendar.date(bySettingHour: 21, minute: 59, second: 0, of: now)!
        XCTAssertFalse(span.contains(beforeStart))
        
        // 22:00 - Exactly at start
        let atStart = calendar.date(bySettingHour: 22, minute: 0, second: 0, of: now)!
        XCTAssertTrue(span.contains(atStart))
        
        // 23:59 - Before midnight
        let beforeMidnight = calendar.date(bySettingHour: 23, minute: 59, second: 0, of: now)!
        XCTAssertTrue(span.contains(beforeMidnight))
        
        // 00:01 - After midnight
        let afterMidnight = calendar.date(bySettingHour: 0, minute: 1, second: 0, of: now)!
        XCTAssertTrue(span.contains(afterMidnight))
        
        // 10:15 - Exactly at end
        let atEnd = calendar.date(bySettingHour: 10, minute: 15, second: 0, of: now)!
        XCTAssertTrue(span.contains(atEnd))
        
        // 10:16 - After end
        let afterEnd = calendar.date(bySettingHour: 10, minute: 16, second: 0, of: now)!
        XCTAssertFalse(span.contains(afterEnd))
    }
    
    func testFullDaySpan() {
        // Test a 24-hour span (00:00 - 23:59)
        let span = DutyTimeSpan(startHour: 0, startMinute: 0, endHour: 23, endMinute: 59)
        
        XCTAssertFalse(span.spansMultipleDays)
        XCTAssertEqual(span.displayName, "00:00 - 23:59")
        
        let calendar = Calendar.current
        let now = Date()
        
        // Test various times throughout the day
        let midnight = calendar.date(bySettingHour: 0, minute: 0, second: 0, of: now)!
        XCTAssertTrue(span.contains(midnight))
        
        let noon = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: now)!
        XCTAssertTrue(span.contains(noon))
        
        let endOfDay = calendar.date(bySettingHour: 23, minute: 59, second: 0, of: now)!
        XCTAssertTrue(span.contains(endOfDay))
    }
}
