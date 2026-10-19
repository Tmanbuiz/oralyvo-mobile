import Foundation
import Combine
import Security

struct OralyvoSession: Codable {
    var access_token: String
    var refresh_token: String?
    var expires_at: Double?
    var email: String?
    var userId: String?
}

struct HistoryEntry: Codable, Identifiable {
    let id: String
    let type: String
    let content: String
    let created_at: String
}

struct APIError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

// Tokens are kept in the device Keychain, never UserDefaults or log output.
enum SessionVault {
    private static let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "app.oralyvo.mobile",
        kSecAttrAccount as String: "session"
    ]
    static func read() -> OralyvoSession? {
        var request = query
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(request as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(OralyvoSession.self, from: data)
    }
    static func save(_ session: OralyvoSession) throws {
        let data = try JSONEncoder().encode(session)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var item = query
            item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else {
                throw APIError(message: "Could not securely save your sign-in. Please try again.")
            }
        } else if status != errSecSuccess {
            throw APIError(message: "Could not securely save your sign-in. Please try again.")
        }
    }
    static func clear() { SecItemDelete(query as CFDictionary) }
}

@MainActor
final class OralyvoAPI: ObservableObject {
    static let origin = URL(string: "https://oralyvo.app")!
    @Published private(set) var session = SessionVault.read()
    private var refreshTask: Task<OralyvoSession, Error>?
    private let network: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 90
        config.timeoutIntervalForResource = 180
        return URLSession(configuration: config)
    }()

    func accept(_ value: OralyvoSession) throws {
        guard !value.access_token.isEmpty, let id = value.userId, UUID(uuidString: id) != nil else {
            throw APIError(message: "Sign-in was incomplete. Please try again.")
        }
        try SessionVault.save(value)
        session = value
    }
    func signOut() {
        refreshTask?.cancel()
        refreshTask = nil
        SessionVault.clear()
        session = nil
    }
    private func decode(_ data: Data, response: URLResponse) throws -> [String: Any] {
        guard let response = response as? HTTPURLResponse else {
            throw APIError(message: "Oralyvo returned an unexpected response.")
        }
        let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (200..<300).contains(response.statusCode) else {
            let message = ["error_description", "msg", "error", "message"].compactMap { object[$0] as? String }.first
            throw APIError(message: message ?? "Request failed (HTTP \(response.statusCode)). Please try again.")
        }
        return object
    }
    private func refresh() async throws -> OralyvoSession {
        if let refreshTask { return try await refreshTask.value }
        guard let old = session, let refreshToken = old.refresh_token else {
            throw APIError(message: "Please sign out and sign in again to renew your session.")
        }
        let task = Task<OralyvoSession, Error> {
            let (healthData, healthResponse) = try await network.data(from: Self.origin.appendingPathComponent("api/health"))
            let health = try decode(healthData, response: healthResponse)
            guard let base = health["supabaseUrl"] as? String,
                  let url = URL(string: base), url.scheme == "https",
                  let key = health["supabasePublishableKey"] as? String else {
                throw APIError(message: "Account service is temporarily unavailable.")
            }
            var request = URLRequest(url: url.appendingPathComponent("auth/v1/token").appending(queryItems: [URLQueryItem(name: "grant_type", value: "refresh_token")]))
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(key, forHTTPHeaderField: "apikey")
            request.httpBody = try JSONSerialization.data(withJSONObject: ["refresh_token": refreshToken])
            let (data, response) = try await network.data(for: request)
            let object = try decode(data, response: response)
            guard let access = object["access_token"] as? String else {
                throw APIError(message: "Please sign out and sign in again.")
            }
            return OralyvoSession(access_token: access, refresh_token: object["refresh_token"] as? String ?? refreshToken,
                                  expires_at: Date().timeIntervalSince1970 + (object["expires_in"] as? Double ?? 3600),
                                  email: old.email, userId: old.userId)
        }
        refreshTask = task
        defer { refreshTask = nil }
        let renewed = try await task.value
        try Task.checkCancellation()
        guard session?.userId == old.userId else { throw CancellationError() }
        try accept(renewed)
        return renewed
    }
    private func authorized(_ request: URLRequest) async throws -> [String: Any] {
        guard var active = session else { throw APIError(message: "Please sign in first.") }
        if (active.expires_at ?? 0) < Date().timeIntervalSince1970 + 60 { active = try await refresh() }
        var request = request
        request.setValue("Bearer \(active.access_token)", forHTTPHeaderField: "Authorization")
        var (data, response) = try await network.data(for: request)
        if (response as? HTTPURLResponse)?.statusCode == 401 {
            active = try await refresh()
            request.setValue("Bearer \(active.access_token)", forHTTPHeaderField: "Authorization")
            (data, response) = try await network.data(for: request)
        }
        return try decode(data, response: response)
    }
    func json(_ path: String, method: String = "POST", body: [String: Any]? = nil) async throws -> [String: Any] {
        var request = URLRequest(url: Self.origin.appendingPathComponent(path))
        request.httpMethod = method
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        return try await authorized(request)
    }
    func transcribe(_ file: URL) async throws -> String {
        let audio = try Data(contentsOf: file)
        guard audio.count <= 24 * 1024 * 1024 else { throw APIError(message: "This recording is too large. Record a shorter clip.") }
        let boundary = "Oralyvo-" + UUID().uuidString
        var body = Data()
        func append(_ text: String) { body.append(Data(text.utf8)) }
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"quality\"\r\n\r\naccurate\r\n")
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"recording.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n")
        body.append(audio)
        append("\r\n--\(boundary)--\r\n")
        var request = URLRequest(url: Self.origin.appendingPathComponent("api/transcribe"))
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        let result = try await authorized(request)
        guard let text = result["text"] as? String, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw APIError(message: "No speech was detected. You can retry or record again.")
        }
        return text
    }
    func process(_ text: String, mode: String, language: String) async throws -> String {
        let result = try await json("api/process", body: ["transcript": text, "mode": mode, "targetLanguage": language, "provider": "engine-1"])
        guard let summary = result["summary"] as? String else { throw APIError(message: "No result was returned. Please try again.") }
        return summary
    }
    func history() async throws -> [HistoryEntry] {
        let result = try await json("api/history", method: "GET")
        return try JSONDecoder().decode([HistoryEntry].self, from: JSONSerialization.data(withJSONObject: result["entries"] ?? []))
    }
    func save(_ text: String, type: String, id: String) async throws {
        _ = try await json("api/history", body: ["content": text, "type": type, "legacy_id": id])
    }
}
