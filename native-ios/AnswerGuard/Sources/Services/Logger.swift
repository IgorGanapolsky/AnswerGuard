import Foundation
import os

extension Logger {
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.igorganapolsky.answerguard"

    static let callScreening = Logger(subsystem: subsystem, category: "callScreening")
    static let callDirectory = Logger(subsystem: subsystem, category: "callDirectory")
    static let analytics = Logger(subsystem: subsystem, category: "analytics")
}
