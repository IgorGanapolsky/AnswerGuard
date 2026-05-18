import Foundation
import Contacts
import os

@MainActor
final class ContactsService: ObservableObject {

    static let shared = ContactsService()

    @Published private(set) var isAuthorized: Bool = false

    private let store = CNContactStore()
    private let logger = Logger(subsystem: "com.igorganapolsky.answerguard", category: "ContactsService")

    private init() {
        checkStatus()
    }

    func checkStatus() {
        let status = CNContactStore.authorizationStatus(for: .contacts)
        isAuthorized = (status == .authorized)
    }

    func requestAccess() async -> Bool {
        do {
            let granted = try await store.requestAccess(for: .contacts)
            isAuthorized = granted
            return granted
        } catch {
            logger.error("Failed to request contacts access: \(error.localizedDescription)")
            return false
        }
    }

    func isNumberInContacts(_ phoneNumber: String) -> Bool {
        guard isAuthorized else { return false }

        let digits = phoneNumber.filter { $0.isNumber }
        guard !digits.isEmpty else { return false }

        let keysToFetch = [CNContactPhoneNumbersKey as CNKeyDescriptor]
        let fetchRequest = CNContactFetchRequest(keysToFetch: keysToFetch)

        do {
            var found = false
            try store.enumerateContacts(with: fetchRequest) { contact, stop in
                for number in contact.phoneNumbers {
                    let contactDigits = number.value.stringValue.filter { $0.isNumber }
                    if contactDigits.hasSuffix(digits) || digits.hasSuffix(contactDigits) {
                        found = true
                        stop.pointee = true
                        return
                    }
                }
            }
            return found
        } catch {
            logger.error("Error fetching contacts: \(error.localizedDescription)")
            return false
        }
    }
}
