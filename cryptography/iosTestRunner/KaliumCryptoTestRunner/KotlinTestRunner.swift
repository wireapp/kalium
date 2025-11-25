import Foundation
import WireCoreCrypto
import WireCoreCryptoUniffi

/// This class provides a way to test CoreCrypto functionality directly from Swift/XCTest
/// which has proper Keychain entitlements
@objc public class KotlinTestRunner: NSObject {

    @objc public static func runCoreCryptoTest() async -> Bool {
        do {
            // Create a temporary directory for the keystore
            let tempDir = FileManager.default.temporaryDirectory
                .appendingPathComponent("corecrypto-test-\(UUID().uuidString)")

            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

            let keystorePath = tempDir.appendingPathComponent("keystore").path

            // Create a test passphrase
            let passphrase = "test-passphrase-12345678901234567890123456789012".data(using: .utf8)!

            print("Creating CoreCrypto instance at: \(keystorePath)")

            // Try to create CoreCrypto instance
            let databaseKey = try DatabaseKey(key: passphrase)
            let coreCrypto = try await CoreCrypto(keystorePath: keystorePath, key: databaseKey)

            print("CoreCrypto instance created successfully!")
            print("Version: \(CoreCrypto.version())")

            // Test a simple transaction
            let result = try await coreCrypto.transaction { context in
                // Just verify we can start a transaction
                return true
            }

            print("Transaction completed: \(result)")

            // Cleanup
            try? FileManager.default.removeItem(at: tempDir)

            return true
        } catch {
            print("CoreCrypto test failed: \(error)")
            return false
        }
    }
}
