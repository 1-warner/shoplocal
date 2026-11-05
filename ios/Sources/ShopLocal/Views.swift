import SwiftUI

let brandGreen = Color(red: 0.0, green: 0.44, blue: 0.35)
let brandGold = Color(red: 0.96, green: 0.65, blue: 0.14)

/// Root view — shows the shop when signed in, otherwise the login screen.
struct RootView: View {
    @StateObject private var store = Store()

    var body: some View {
        Group {
            if store.isSignedIn {
                HomeView().environmentObject(store)
            } else {
                LoginView().environmentObject(store)
            }
        }
    }
}

struct LoginView: View {
    @EnvironmentObject var store: Store
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var registering = false

    var body: some View {
        ZStack {
            LinearGradient(colors: [brandGreen, Color(red: 0.07, green: 0.65, blue: 0.54)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
                .ignoresSafeArea()
            VStack(spacing: 16) {
                Image(systemName: "bag.fill").font(.system(size: 56)).foregroundStyle(.white)
                Text("ShopLocal").font(.largeTitle.bold()).foregroundStyle(.white)
                Text("Discover. Support. Shop South African.")
                    .font(.footnote).foregroundStyle(brandGold)

                VStack(spacing: 12) {
                    if registering {
                        field("Full name", text: $name)
                    }
                    field("Email", text: $email, keyboard: .emailAddress)
                    secureField("Password", text: $password)

                    if let e = store.error {
                        Text(e).font(.caption).foregroundStyle(.red)
                    }

                    Button {
                        Task {
                            if registering { await store.signUp(email: email, password: password, name: name) }
                            else { await store.signIn(email: email, password: password) }
                        }
                    } label: {
                        HStack {
                            if store.loading { ProgressView().tint(.white) }
                            Text(registering ? "Create account" : "Log in").bold()
                        }
                        .frame(maxWidth: .infinity).padding()
                        .background(brandGreen).foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 26))
                    }
                    .disabled(store.loading)

                    Button(registering ? "Already have an account? Log in"
                                       : "New here? Create an account") {
                        registering.toggle()
                    }.font(.footnote.bold()).foregroundStyle(brandGreen)
                }
                .padding()
                .background(.white)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .padding()
            }
        }
    }

    private func field(_ ph: String, text: Binding<String>, keyboard: UIKeyboardType = .default) -> some View {
        TextField(ph, text: text)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
            .keyboardType(keyboard).padding()
            .background(Color(.systemGray6)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
    private func secureField(_ ph: String, text: Binding<String>) -> some View {
        SecureField(ph, text: text).padding()
            .background(Color(.systemGray6)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct HomeView: View {
    @EnvironmentObject var store: Store
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        NavigationStack {
            ScrollView {
                // Category chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(store.categories, id: \.self) { c in
                            Text(c).font(.subheadline)
                                .padding(.horizontal, 14).padding(.vertical, 7)
                                .background(store.selectedCategory == c ? brandGreen : Color(.systemGray5))
                                .foregroundStyle(store.selectedCategory == c ? .white : .primary)
                                .clipShape(Capsule())
                                .onTapGesture { store.selectedCategory = c }
                        }
                    }.padding(.horizontal)
                }

                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(store.filtered) { p in
                        NavigationLink(value: p) { ProductCard(product: p) }
                            .buttonStyle(.plain)
                    }
                }.padding()
            }
            .searchable(text: $store.search, prompt: "Search local products")
            .navigationTitle("ShopLocal")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Sign out") { store.signOut() }.foregroundStyle(.red)
                }
            }
            .navigationDestination(for: Product.self) { ProductDetailView(product: $0) }
            .overlay { if store.loading && store.products.isEmpty { ProgressView() } }
            .task { await store.loadProducts() }
            .refreshable { await store.loadProducts() }
        }
        .tint(brandGreen)
    }
}

struct ProductCard: View {
    let product: Product
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            AsyncImage(url: URL(string: product.imageUrl ?? "")) { img in
                img.resizable().aspectRatio(contentMode: .fill)
            } placeholder: {
                Color(.systemGray6)
            }
            .frame(height: 140).frame(maxWidth: .infinity).clipped()

            Text(product.name).font(.subheadline.bold()).lineLimit(2)
                .padding(.horizontal, 8)
            HStack(spacing: 6) {
                Text(product.priceText).font(.callout.bold()).foregroundStyle(brandGreen)
                if product.isOnSale {
                    Text(Product.rand(product.price)).font(.caption)
                        .strikethrough().foregroundStyle(.secondary)
                }
            }.padding(.horizontal, 8)
            if product.ratingCount > 0 {
                Text(String(format: "★ %.1f (%d)", product.ratingAvg, product.ratingCount))
                    .font(.caption).foregroundStyle(brandGold).padding(.horizontal, 8)
            }
            Spacer(minLength: 8)
        }
        .background(.background)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(.systemGray5)))
    }
}

struct ProductDetailView: View {
    let product: Product
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                AsyncImage(url: URL(string: product.imageUrl ?? "")) { img in
                    img.resizable().aspectRatio(contentMode: .fill)
                } placeholder: { Color(.systemGray6) }
                .frame(height: 260).frame(maxWidth: .infinity).clipped()

                Group {
                    Text(product.name).font(.title2.bold())
                    HStack {
                        Text(product.priceText).font(.title3.bold()).foregroundStyle(brandGreen)
                        if product.isOnSale {
                            Text(Product.rand(product.price)).strikethrough().foregroundStyle(.secondary)
                        }
                    }
                    if let origin = product.origin {
                        Text("Made in \(origin)").font(.caption).foregroundStyle(.secondary)
                    }
                    Text(product.description ?? "").padding(.top, 4)
                }.padding(.horizontal)
            }
        }
        .navigationTitle(product.name).navigationBarTitleDisplayMode(.inline)
    }
}
