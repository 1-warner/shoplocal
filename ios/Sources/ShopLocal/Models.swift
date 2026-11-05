import Foundation

/// A product from the shared catalogue. Decoded from PostgREST with
/// `.convertFromSnakeCase`, so `discount_price` → `discountPrice`, etc.
struct Product: Identifiable, Decodable, Hashable {
    let id: String
    let name: String
    let description: String?
    let category: String
    let price: Double
    let discountPrice: Double?
    let imageUrl: String?
    let stock: Int
    let ratingAvg: Double
    let ratingCount: Int
    let origin: String?

    var effectivePrice: Double { discountPrice ?? price }
    var isOnSale: Bool { (discountPrice ?? price) < price }

    var priceText: String { Self.rand(effectivePrice) }

    static func rand(_ amount: Double) -> String {
        "R\(String(format: "%.2f", amount))"
    }
}

/// The signed-in session (access token + basic identity).
struct Session: Codable {
    let accessToken: String
    let refreshToken: String?
    let user: User

    struct User: Codable {
        let id: String
        let email: String?
    }
}
