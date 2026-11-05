import Foundation
import SwiftUI

/// App-wide state: authentication and the product catalogue. Mirrors what the
/// Android app's repository layer does, against the same Supabase backend.
@MainActor
final class Store: ObservableObject {
    @Published var session: Session?
    @Published var products: [Product] = []
    @Published var categories: [String] = ["All"]
    @Published var selectedCategory = "All"
    @Published var search = ""
    @Published var loading = false
    @Published var error: String?

    private let tokenKey = "shoplocal.accessToken"

    var isSignedIn: Bool { session != nil }

    var filtered: [Product] {
        products.filter { p in
            (selectedCategory == "All" || p.category == selectedCategory) &&
            (search.isEmpty || p.name.localizedCaseInsensitiveContains(search))
        }
    }

    // MARK: - Auth

    func signIn(email: String, password: String) async {
        await authenticate(path: "token?grant_type=password", body: ["email": email, "password": password])
    }

    func signUp(email: String, password: String, name: String) async {
        // Create the account, then sign in to get a session deterministically.
        do {
            _ = try await API.post("\(Supabase.auth)/signup",
                                   body: ["email": email, "password": password,
                                          "data": ["name": name]])
        } catch { /* may already exist / may auto-confirm; continue to sign-in */ }
        await signIn(email: email, password: password)
    }

    private func authenticate(path: String, body: [String: Any]) async {
        loading = true; error = nil
        defer { loading = false }
        do {
            let data = try await API.post("\(Supabase.auth)/\(path)", body: body)
            let decoder = JSONDecoder(); decoder.keyDecodingStrategy = .convertFromSnakeCase
            let s = try decoder.decode(Session.self, from: data)
            session = s
            UserDefaults.standard.set(s.accessToken, forKey: tokenKey)
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Sign-in failed."
        }
    }

    func signOut() {
        session = nil
        UserDefaults.standard.removeObject(forKey: tokenKey)
    }

    // MARK: - Catalogue

    func loadProducts() async {
        loading = true; error = nil
        defer { loading = false }
        do {
            let select = "id,name,description,category,price,discount_price,image_url,stock,rating_avg,rating_count,origin"
            let items = try await API.get("products?select=\(select)&order=created_at.desc", as: [Product].self)
            products = items
            categories = ["All"] + Set(items.map { $0.category }).sorted()
        } catch {
            self.error = (error as? LocalizedError)?.errorDescription ?? "Could not load products."
        }
    }
}
