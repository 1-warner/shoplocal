# ShopLocal 🛍️🇿🇦

> **Discover. Support. Shop South African.**
>
> A native Android shopping-app prototype that connects South African shoppers
> with **local artisans and small businesses**. Built for OPSC Part 2 (App
> Prototype Development) from the design in the Part 1 Planning & Design document.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)
![Min SDK](https://img.shields.io/badge/minSdk-24-orange)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF)

---

## 1. Purpose of the app

South Africa's e-commerce space is dominated by generalist and fashion-only
players (Takealot, Superbalist, Bash). **ShopLocal** carves out a distinct niche —
it *exclusively* sells locally-made goods and puts the **maker's story** front and
centre, backed by a **gamified loyalty programme** ("Local Points") that rewards
customers for supporting local artisans.

This repository is the **working prototype** for Part 2: it compiles and runs on a
real device, registers and authenticates users against a hosted backend, reads a
live product catalogue from a REST API, and lets the user build a cart and check
out — awarding loyalty points server-side.

---

## 2. Feature overview

| Area | What it does | Rubric mapping |
|------|--------------|----------------|
| **Register / Log in** | Email + password auth; passwords bcrypt-hashed server-side; session cached so the app skips login on relaunch | *Feature: sign in* |
| **Google / Apple sign-in** | One-tap social login on the login screen, via the provider's own page in a Custom Tab (PKCE); no password to remember | *Feature: sign in* |
| **Settings menu** | Edit profile, switch **language** (English / isiZulu / Afrikaans), **light/dark theme**, notification toggles — saved locally and synced to the API | *Feature: settings menu* |
| **REST API** | A hosted Supabase (Postgres + PostgREST) backend, including a **custom `place_order` endpoint** we authored | *Creation/use of the REST API* |
| **API integration** | Every screen (catalogue, wishlist, cart, reviews, orders, profile) reads/writes through the REST API | *Integration of the REST API* |
| **Local Points loyalty** *(user-defined 1)* | Earn 1 point per R10 spent; balance shown on the profile; awarded atomically at checkout | *User Defined 1* |
| **Community reviews** *(user-defined 2)* | Read and post star reviews; product ratings recompute via a DB trigger | *User Defined 2* |
| **Wishlist** *(user-defined 3)* | Save items for later, synced to your account | *User Defined 3* |
| **Artisan map** | An OpenStreetMap (osmdroid) map with a pin for each seller's home town; tap a pin to meet the maker and browse their products | Extra user-defined feature |
| **Member marketplace** | Facebook-Marketplace-style flow: any member can **list an item to sell**; listings show on a map with an embedded location and a "contact seller" share | Extra user-defined feature |
| **In your area** | Set your area (a SA city) and Home filters to items **near you** vs **other areas**, with each product card showing the distance to the maker | Extra user-defined feature |
| **Sort, filter & deals** | Sort by price/rating/newest, filter by category, and a **Deals** toggle (Superbalist/Bash/Takealot-style) | Polish |
| **ShopLocal MORE** | TakealotMORE-style subscription giving **free delivery** on every order | Polish |
| **Recently viewed** | A Shein/Takealot-style strip of the products you last opened | Polish |
| **Share** | Share any product or listing via the Android share sheet | Polish |
| **Extras** | Offline catalogue cache, artisan storytelling, pull-to-refresh, **48-item catalogue across 17 artisans in 9 provinces**, modern Material 3 styling (rounded cards, sale badges, pill buttons) | Polish |

> **See [REFERENCES.md](REFERENCES.md)** for a full, Harvard-style reference list:
> which competitor app each feature came from, every library/SDK and its licence,
> and the source of all product imagery.

---

## 3. Screens

Splash → Login / Register → **Home** (search, category chips, product grid) →
**Product detail** (photo, price, the maker's story, reviews) → **Cart** (quantity
steppers, live totals, checkout) → **Wishlist** → **Profile** (Local Points,
orders, settings, logout) → **Settings** → **Orders**.

---

## 4. Architecture

```
┌─────────────────────────── Android app (Kotlin) ───────────────────────────┐
│  ui/            Activities + Fragments (View layer, ViewBinding)            │
│   ├─ auth       LoginFragment / RegisterFragment                           │
│   ├─ home       HomeFragment + ProductAdapter                              │
│   ├─ product    ProductDetailActivity + ReviewAdapter                      │
│   ├─ cart       CartFragment + CartAdapter                                 │
│   ├─ wishlist   WishlistFragment                                          │
│   ├─ map        ArtisanMapActivity (osmdroid seller map)                  │
│   ├─ profile    ProfileFragment                                           │
│   ├─ orders     OrdersActivity                                            │
│   └─ settings   SettingsActivity                                          │
│  data/          SupabaseAuth · ShopRepository · Models · Pricing ·         │
│                 Validators · Prefs (single place for all network + logic)  │
│  util/          LocaleHelper (language) · AppTheme (dark mode)             │
└────────────────────────────────┬───────────────────────────────────────────┘
                                  │  HTTPS (OkHttp + coroutines)
                                  ▼
┌───────────────────────── Hosted backend (Supabase) ─────────────────────────┐
│  GoTrue  /auth/v1/…      → register / login (bcrypt)                        │
│  PostgREST /rest/v1/…    → products, sellers, wishlist, cart, orders,       │
│                            reviews, profiles (Row-Level Security)           │
│  RPC /rest/v1/rpc/place_order  → custom checkout endpoint (our code)        │
│  Postgres 17             → data + triggers (rating refresh, auto-profile)   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key libraries / SDKs**

- **OkHttp** + **Kotlin Coroutines** — talk to the REST API off the main thread.
- **osmdroid** — OpenStreetMap map view for the artisan map (no API key required).
- **Coil** — load product images from the API.
- **Material Components 3** — UI, theming, dark mode.
- **AndroidX** (AppCompat, Fragment, RecyclerView, ConstraintLayout, SwipeRefresh).

---

## 5. The REST API

The backend is a **Supabase** project (Postgres + auto-generated PostgREST API),
hosted at `https://swchtpvdtncslvlsxeul.supabase.co`.

### Endpoints the app uses

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/auth/v1/signup` | Register (password bcrypt-hashed server-side) |
| `POST` | `/auth/v1/token?grant_type=password` | Log in, returns a JWT |
| `GET`  | `/auth/v1/authorize?provider=google\|apple` | Start a Google / Apple sign-in (opens in a Custom Tab) |
| `POST` | `/auth/v1/token?grant_type=pkce` | Redeem the authorization code from the deep link for a session |
| `POST` | `/auth/v1/token?grant_type=refresh_token` | Renew an expired access token |
| `GET`  | `/rest/v1/products?select=…,sellers(...)` | Catalogue with artisan join |
| `GET`  | `/rest/v1/reviews?product_id=eq.<id>` | Product reviews |
| `POST` | `/rest/v1/reviews` | Post a review |
| `GET/POST/DELETE` | `/rest/v1/wishlist` | Wishlist |
| `GET/POST/DELETE` | `/rest/v1/cart_items` | Cart |
| `GET`  | `/rest/v1/orders` | Order history |
| **`POST`** | **`/rest/v1/rpc/place_order`** | **Custom endpoint we wrote** — turns the cart into an order, awards Local Points and clears the cart, atomically |
| `GET/PATCH` | `/rest/v1/profiles` | Profile + settings |

### Why this counts as *creating* an API

Beyond the auto-generated CRUD, we authored a server-side function,
`place_order(delivery_address, delivery_fee)`, exposed as a REST endpoint. It
encapsulates the checkout business rule (loyalty points = ⌊subtotal ÷ 10⌋) so the
figure the app previews always matches what the server records. Two database
**triggers** keep product ratings and user profiles in sync.

### Security

Access is protected by **Row-Level Security**: the public `anon` key can only read
the catalogue, while each signed-in user can only see and change *their own* cart,
wishlist, orders and reviews. The `anon` key shipped in the app is a public client
key by design — RLS, not secrecy, is what protects the data.

---

### Social sign-in (Google / Apple)

The login screen offers **Continue with Google** and **Continue with Apple**
alongside email/password. The flow is the standard OAuth redirect, run through the
device browser rather than a WebView — both providers reject embedded WebViews, and
the user gets to see the real `google.com` / `apple.com` address bar before typing a
password:

```
LoginFragment                 Custom Tab                GoTrue + provider
     │  beginOAuth()               │                            │
     │  (random code_verifier       │                            │
     │   kept on device, only       │                            │
     │   its SHA-256 sent)          │                            │
     ├─ /auth/v1/authorize ────────►│──── consent screen ───────►│
     │                              │                            │
     │        shoplocal://auth-callback?code=…  ◄────────────────┤
     ▼                              │
OAuthCallbackActivity ── /auth/v1/token?grant_type=pkce ────────►│
     │   (code + code_verifier)                                  │
     │◄──────────── access_token + refresh_token ────────────────┤
     ▼
MainActivity (session cached exactly like an email login)
```

**PKCE** is what makes the deep link safe: another app that registered the same
`shoplocal://` scheme could intercept the redirect, but the authorization code in it
is worthless without the verifier, which never leaves the device.

Everything downstream is unchanged — the social session is cached in the same
SharedPreferences, refreshes the same way, and the `handle_new_user` trigger creates
the same `profiles` row (its name fallback chain reads Google's `full_name` and
Apple's `given_name`/`family_name`, since neither sends the `name` key that email
sign-ups use).

#### Backend configuration this needs

The code is complete, but each provider has to be given credentials; these cannot be
created from inside the app:

1. **Supabase → Authentication → URL Configuration → Redirect URLs**
   Add `shoplocal://auth-callback` (must match `Supabase.OAUTH_REDIRECT`).

2. **Google** — [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
   → *Create credentials → OAuth client ID* → application type **Web application**
   (not Android: the redirect lands on Supabase, not on the device). Under
   *Authorized redirect URIs* add:
   `https://swchtpvdtncslvlsxeul.supabase.co/auth/v1/callback`
   Then paste the **Client ID** and **Client secret** into
   *Supabase → Authentication → Providers → Google* and enable it.

3. **Apple** — requires a paid **Apple Developer Program** membership (~$99/year).
   In the [developer portal](https://developer.apple.com/account/resources/identifiers/list):
   create an **App ID** with *Sign in with Apple* enabled, then a **Services ID**
   (e.g. `com.noor.shoplocal.web`) whose *Website URLs* are domain
   `swchtpvdtncslvlsxeul.supabase.co` and return URL
   `https://swchtpvdtncslvlsxeul.supabase.co/auth/v1/callback`. Create a
   *Sign in with Apple* **key**, download the `.p8`, and note the **Key ID** and
   **Team ID**. Enter the Services ID plus the generated secret in
   *Supabase → Authentication → Providers → Apple* and enable it.

Until a provider is enabled, `/authorize` answers with a plain JSON 400
(`"Unsupported provider: provider is not enabled"`) and never redirects back to the
app — so the login screen checks `/auth/v1/settings` first and shows *"Google
sign-in is not set up yet"* instead of opening a browser tab full of JSON. Check the
live state with:

```bash
curl -s https://swchtpvdtncslvlsxeul.supabase.co/auth/v1/settings \
  -H "apikey: <anon key>" | grep -o '"google":[a-z]*'
```

---

## 6. Build & run

**Requirements:** Android Studio (Giraffe or newer), JDK 17, Android SDK 34.

1. `git clone <this repo>` and open the folder in Android Studio.
2. Let Gradle sync (pulls dependencies from Google's Maven + Maven Central).
3. Pick a device/emulator (API 24+) and press **Run ▶**.

From the command line:

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew testDebugUnitTest  # run the unit tests
```

The backend is already live, so no server setup is needed — register a new account
in-app and start shopping.

---

## 7. Testing & CI

- **Unit tests** (`app/src/test/`): `PricingTest` covers the cart subtotal,
  free-delivery threshold, total and loyalty-point maths; `ValidatorsTest` covers
  email / password / name validation and the registration-form rules. These are
  pure-JVM tests — fast and deterministic.
- **GitHub Actions** (`.github/workflows/android.yml`): on every push / PR it sets
  up JDK 17, **runs the unit tests**, **builds the debug APK**, and uploads both the
  APK and the test report as artifacts. This proves the project builds and passes on
  a clean machine, not just locally.

---

## 8. Robustness

Every network call is wrapped so failures surface as friendly messages instead of
crashes. Invalid input (bad email, weak password, mismatched passwords, empty cart)
is caught before hitting the network. If the device is offline, Home falls back to
the last cached catalogue. Logging (`android.util.Log`) traces auth and API calls
for debugging.

**Session handling:** access tokens expire after ~1 hour, so the app stores the
refresh token and automatically renews the session — proactively at startup and
again whenever an authenticated request returns `401` — then retries the request.
This keeps the cart, wishlist and orders working long after login instead of
silently failing.

**Delivery location:** a member must set a delivery location before they can place
an order. The cart shows a warning banner ("You haven't set your delivery location")
until one is saved, and a confirmation banner ("Delivering to …") once it is; the
address is stored on the user's profile and reused for future orders. The Cart tab
also shows a live item-count badge.

---

## 9. Demonstration video

📹 **Watch the walkthrough:** https://youtu.be/_J2R1znujEc

The video shows registration & login (with the password encrypted), changing
settings (language + theme), browsing the API-backed catalogue, adding to the
cart/wishlist, checking out through the custom REST endpoint, earning Local Points,
and the data as it lands in the hosted database.

---

## 10. References

A complete, Harvard-style reference list is in **[REFERENCES.md](REFERENCES.md)**, covering:

1. **Feature inspiration** — each borrowed feature mapped to the competitor app it
   came from (Takealot, Superbalist, Bash, Facebook Marketplace, Shein), with the
   same citations used in the Part 1 research.
2. **Libraries & SDKs** — Kotlin, AndroidX, Material, OkHttp, Coil, osmdroid, JUnit,
   each with its licence and source.
3. **Backend & tooling** — Supabase, OpenStreetMap, and the module-supplied GitHub
   Actions guides the CI workflow was adapted from.
4. **Icons** — custom vectors based on Material Symbols.
5. **Imagery** — all product photos are royalty-free Unsplash images (licence linked).
6. **Data** — SA town coordinates from OpenStreetMap/Nominatim.

## 11. Tech summary

| | |
|---|---|
| Language | Kotlin |
| Min / Target SDK | 24 / 34 |
| Architecture | Activities/Fragments + a single repository layer |
| Backend | Supabase (Postgres 17, PostgREST, GoTrue auth) |
| Networking | OkHttp + Coroutines |
| CI | GitHub Actions (build + unit tests) |

_Built as the Part 2 prototype for the ShopLocal concept defined in Part 1._
