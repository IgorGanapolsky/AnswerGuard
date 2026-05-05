import Foundation
import CallKit

final class CallDirectoryHandler: CXCallDirectoryProvider {

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        do {
            try addBlockingPhoneNumbers(to: context)
            try addIdentificationPhoneNumbers(to: context)
            context.completeRequest()
        } catch {
            let logger = Logger(subsystem: "com.igorganapolsky.answerguard.calldirectory", category: "handler")
            logger.error("CallDirectory request failed: \(error.localizedDescription)")
            context.cancelRequest(withError: error)
        }
    }

    // MARK: - Blocking

    private func addBlockingPhoneNumbers(to context: CXCallDirectoryExtensionContext) throws {
        let numbers = SpamDatabase.shared.blockedNumbers()
        // Numbers MUST be sorted ascending
        for number in numbers.sorted() {
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
    }

    // MARK: - Identification

    private func addIdentificationPhoneNumbers(to context: CXCallDirectoryExtensionContext) throws {
        let entries = SpamDatabase.shared.identificationEntries()
        for entry in entries.sorted(by: { $0.phoneNumber < $1.phoneNumber }) {
            context.addIdentificationEntry(withNextSequentialPhoneNumber: entry.phoneNumber,
                                           label: entry.label)
        }
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        let logger = Logger(subsystem: "com.igorganapolsky.answerguard.calldirectory", category: "delegate")
        logger.error("Extension context request failed: \(error.localizedDescription)")
    }
}
