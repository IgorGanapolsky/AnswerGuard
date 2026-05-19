import Foundation
import CallKit
import os

final class CallDirectoryHandler: CXCallDirectoryProvider {

    private let logger = os.Logger(
        subsystem: "com.igorganapolsky.answerguard.calldirectory",
        category: "handler"
    )

    override func beginRequest(with context: CXCallDirectoryExtensionContext) {
        context.delegate = self

        do {
            if context.isIncremental {
                try addIncrementalBlockingEntries(to: context)
                try addIncrementalIdentificationEntries(to: context)
            } else {
                try addAllBlockingEntries(to: context)
                try addAllIdentificationEntries(to: context)
            }
            SpamDatabase.shared.clearPendingBlockedDeltas()
            context.completeRequest()
        } catch {
            logger.error("CallDirectory request failed: \(error.localizedDescription)")
            context.cancelRequest(withError: error)
        }
    }

    // MARK: - Full reload

    private func addAllBlockingEntries(to context: CXCallDirectoryExtensionContext) throws {
        // iOS requires sorted-ascending, unique entries.
        let numbers = SpamDatabase.shared.sortedUniqueBlockedNumbers()
        for number in numbers {
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
        logger.info("Loaded \(numbers.count) blocking entries (full)")
    }

    private func addAllIdentificationEntries(to context: CXCallDirectoryExtensionContext) throws {
        let entries = SpamDatabase.shared.sortedUniqueIdentificationEntries()
        for entry in entries {
            context.addIdentificationEntry(
                withNextSequentialPhoneNumber: entry.phoneNumber,
                label: entry.label
            )
        }
        logger.info("Loaded \(entries.count) identification entries (full)")
    }

    // MARK: - Incremental update

    private func addIncrementalBlockingEntries(to context: CXCallDirectoryExtensionContext) throws {
        // iOS requires sorted-ascending, unique entries for both adds and removes.
        let removed = SpamDatabase.shared.pendingRemovedBlockedNumbers()
        for number in removed {
            context.removeBlockingEntry(withPhoneNumber: number)
        }
        let added = SpamDatabase.shared.pendingAddedBlockedNumbers()
        for number in added {
            context.addBlockingEntry(withNextSequentialPhoneNumber: number)
        }
        logger.info("Incremental blocking: +\(added.count) -\(removed.count)")
    }

    private func addIncrementalIdentificationEntries(to context: CXCallDirectoryExtensionContext) throws {
        // For identification we currently do a simple replace-all: remove nothing
        // explicitly and rely on user-driven full reloads when the identification
        // dataset changes. Future: track identification deltas similarly.
        let entries = SpamDatabase.shared.sortedUniqueIdentificationEntries()
        for entry in entries {
            context.addIdentificationEntry(
                withNextSequentialPhoneNumber: entry.phoneNumber,
                label: entry.label
            )
        }
        logger.info("Incremental identification: \(entries.count) entries")
    }
}

extension CallDirectoryHandler: CXCallDirectoryExtensionContextDelegate {
    func requestFailed(for extensionContext: CXCallDirectoryExtensionContext, withError error: Error) {
        logger.error("Extension context request failed: \(error.localizedDescription)")
    }
}
