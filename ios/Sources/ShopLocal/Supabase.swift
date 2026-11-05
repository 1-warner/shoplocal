import Foundation

/// Configuration + a tiny client for the hosted ShopLocal backend.
///
/// This iOS app talks to the *same* Supabase project as the Android app, so it
/// shares the exact same data, accounts and Google/Facebook sign-in. The `anon`
/// key is a public client key by design — Row-Level Security protects the data.
enum Supabase {
    static let url = "https://swchtpvdtncslvlsxeul.supabase.co"
    static let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN3Y2h0cHZkdG5jc2x2bHN4ZXVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ5MjQ5NjYsImV4cCI6MjEwMDUwMDk2Nn0.wxih6yML-LfgI_aN2tcLtLmxbJKIEtIJL_p303hLxAo"

    static var rest: String { "\(url)/rest/v1" }
    static var auth: String { "\(url)/auth/v1" }
}

/// Errors surfaced to the UI.
struct APIError: Error, LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Minimal REST helper built on URLSession async/await — no third-party SDK.
enum API {
    private static let session = URLSession.shared
    private static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()

    /// GET from PostgREST (public catalogue uses the anon key).
    static func get<T: Decodable>(_ path: String, token: String? = nil, as type: T.Type) async throws -> T {
        var req = URLRequest(url: URL(string: "\(Supabase.rest)/\(path)")!)
        req.setValue(Supabase.anonKey, forHTTPHeaderField: "apikey")
        req.setValue("Bearer \(token ?? Supabase.anonKey)", forHTTPHeaderField: "Authorization")
        let (data, resp) = try await session.data(for: req)
        try check(resp, data)
        return try decoder.decode(T.self, from: data)
    }

    /// POST raw JSON to an auth or rest endpoint, returning the raw data.
    static func post(_ fullURL: String, body: [String: Any], token: String? = nil) async throws -> Data {
        var req = URLRequest(url: URL(string: fullURL)!)
        req.httpMethod = "POST"
        req.setValue(Supabase.anonKey, forHTTPHeaderField: "apikey")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, resp) = try await session.data(for: req)
        try check(resp, data)
        return data
    }

    private static func check(_ resp: URLResponse, _ data: Data) throws {
        guard let http = resp as? HTTPURLResponse else { return }
        guard (200..<300).contains(http.statusCode) else {
            let msg = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let text = msg?["message"] as? String ?? msg?["msg"] as? String
                ?? msg?["error_description"] as? String ?? "Request failed (HTTP \(http.statusCode))"
            throw APIError(message: text)
        }
    }
}
