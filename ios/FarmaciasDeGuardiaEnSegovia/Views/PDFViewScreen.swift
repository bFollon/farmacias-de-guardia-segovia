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

import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    @State private var isPresentingInfo = false
    @State private var isRefreshing = false
    @State private var isLoading = true
    @State private var selectedDate: Date = Date()
    @State private var isShowingDatePicker = false
    @State private var refreshTrigger = false // For triggering UI refresh
    @State private var cacheTimestamp: TimeInterval? = nil
    var url: URL
    var location: DutyLocation
    
    private var dateButtonText: String {
        if Calendar.current.isDateInToday(selectedDate) {
            return "Hoy"
        } else {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            return formatter.string(from: selectedDate)
        }
    }
    
    var body: some View {
        NavigationView {
            Group {
                if isLoading || isRefreshing {
                    LoadingView()
                } else if Calendar.current.isDateInToday(selectedDate),
                       let current = ScheduleService.findCurrentSchedule(in: schedules, for: location.associatedRegion) {
                    // Today's view with current schedule
                    ScheduleContentView(
                        schedule: current.0,
                        activeShift: current.1,
                        location: location,
                        isPresentingInfo: $isPresentingInfo,
                        formattedDateTime: ScheduleService.getCurrentDateTime(),
                        cacheTimestamp: cacheTimestamp
                    )
                } else {
                    // Selected date view - DayScheduleView handles nil schedule with inline card
                    DayScheduleView(
                        schedule: ScheduleService.findSchedule(for: selectedDate, in: schedules),
                        location: location,
                        isPresentingInfo: $isPresentingInfo,
                        date: selectedDate
                    )
                }
            }
            .id(refreshTrigger) // Force refresh when trigger changes
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        isShowingDatePicker = true
                    } label: {
                        HStack {
                            Image(systemName: "calendar")
                            Text(dateButtonText)
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        refreshCurrentView()
                    }) {
                        Image(systemName: "arrow.clockwise.circle")
                            .imageScale(.large)
                    }
                }
            }
            .sheet(isPresented: $isShowingDatePicker) {
                NavigationView {
                    DatePicker("Seleccionar fecha",
                             selection: $selectedDate,
                             in: ...Date().addingTimeInterval(86400 * 365), // Up to 1 year in the future
                             displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    .onChange(of: selectedDate) { oldDate, newDate in
                        isShowingDatePicker = false
                    }
                    .navigationTitle("Seleccionar fecha")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Hoy") {
                                selectedDate = Date()
                            }
                            .disabled(Calendar.current.isDate(selectedDate, inSameDayAs: Date()))
                        }
                        
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Listo") {
                                isShowingDatePicker = false
                            }
                        }
                    }
                }
                .presentationDetents([.medium])
            }
        }
        .onAppear {
            loadPharmacies()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            refreshCurrentView()
        }
    }
    
    private func loadPharmacies() {
        isLoading = true
        Task {
            let loadedSchedules = await ScheduleService.loadSchedules(for: location)
            let timestamp = ScheduleCacheService.shared.getCacheTimestamp(for: location)
            await MainActor.run {
                schedules = loadedSchedules
                cacheTimestamp = timestamp
                isLoading = false
            }
        }
    }

    private func refreshCurrentView() {
        // Just re-evaluate what should be shown based on current time
        // Uses existing cached schedules, no re-downloading
        // This handles the 23:55 -> 00:05 day rollover scenario

        DebugConfig.debugPrint("🔄 Refreshing current view for \(location.name)")

        // Toggle refresh trigger to force SwiftUI re-evaluation
        // The views will automatically re-evaluate ScheduleService.findCurrentSchedule
        // based on current time
        refreshTrigger.toggle()
    }

    private func refreshData() {
        isRefreshing = true

        Task {
            let refreshedSchedules = await ScheduleService.loadSchedules(for: location, forceRefresh: true)
            let timestamp = ScheduleCacheService.shared.getCacheTimestamp(for: location)

            // Update UI on main thread
            await MainActor.run {
                schedules = refreshedSchedules
                cacheTimestamp = timestamp
                isRefreshing = false
            }
        }
    }
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025",
                withExtension: "pdf")!,
            location: DutyLocation.fromRegion(.segoviaCapital)
        )
    }
}
