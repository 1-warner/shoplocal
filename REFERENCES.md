# References & Attribution

This document lists the sources behind ShopLocal — the apps that inspired its
features, the libraries and services it is built on, and the origin of every
asset used. Citations follow the Harvard style used in the Part 1 research and
planning documents.

---

## 1. Feature inspiration (competitor apps analysed in Part 1)

ShopLocal's feature set was informed by a comparative analysis of leading South
African shopping apps. Specific features and where they came from:

| Feature in ShopLocal | Inspired by | Source |
|----------------------|-------------|--------|
| Subscription free delivery ("ShopLocal MORE") | TakealotMORE unlimited free delivery | MyBroadband (2026) |
| Loyalty points | TakealotMORE rewards model | MyBroadband (2026) |
| Powerful search + category filters + sort | Superbalist search/filter critique; Bash filter system | Superbalist (2025); TechCentral (2024) |
| Wishlist / deferred purchase | Superbalist & Bash wishlist | Superbalist (2025) |
| Community reviews with ratings | Superbalist reviews (225k reviews, 4.6★) | Superbalist (2025) |
| Deals / on-sale highlighting | Takealot daily deals | Business Tech (2025) |
| Delivery transparency (address + status) | TakealotNOW real-time delivery | Business Tech (2025); ITWeb (2025) |
| Member-to-member marketplace ("Sell an item") | Facebook Marketplace peer listings | Meta Platforms (2024) |
| Product sharing via the share sheet | Facebook Marketplace share; Shein social sharing | Meta Platforms (2024) |
| "Recently viewed" strip | Shein / Takealot recently-viewed rails | Shein (2024) |
| Local-artisan storytelling + niche focus | Identified market gap in Part 1 research | UNIMALL (2026) |

### Bibliography

- Business Tech, 2025. *Takealot launches new service in three South African cities.* Business Tech, 3 December. Available at: https://businesstech.co.za/news/business/845345/takealot-launches-new-service-in-three-south-african-cities/ [Accessed 23 August 2026].
- ITWeb, 2025. *Takealot tests instant delivery with Mr D in major cities.* ITWeb, 3 December. Available at: https://www.itweb.co.za/article/takealot-tests-instant-delivery-with-mr-d-in-major-cities/VgZeyvJlPOYMdjX9 [Accessed 23 August 2026].
- Meta Platforms, 2024. *Facebook Marketplace.* Available at: https://www.facebook.com/marketplace/ [Accessed 21 September 2026].
- MyBroadband, 2026. *Amazon Prime versus TakealotMore in South Africa.* MyBroadband, 3 June. Available at: https://mybroadband.co.za/news/business/651674-amazon-prime-versus-takealotmore-in-south-africa.html [Accessed 23 August 2026].
- Shein, 2024. *SHEIN Shopping Online App.* Available at: https://play.google.com/store/apps/details?id=com.zzkko [Accessed 21 September 2026].
- Superbalist, 2025. *Superbalist Shopping App.* Available at: https://play.google.com/store/apps/details?id=com.superbalist.android [Accessed 23 August 2026].
- TechCentral, 2024. *South African online fashion retailer Zando to close down.* TechCentral, 16 October. Available at: https://techcentral.co.za/online-fashion-zando-to-close-down/253487/ [Accessed 23 August 2026].
- UNIMALL, 2026. *South Africa Ecommerce Market Report 2025.* Available at: https://unimall.ai/guides/markets/south-africa [Accessed 23 August 2026].

---

## 2. Software libraries & SDKs

All third-party libraries are pulled from Google's Maven and Maven Central via Gradle.

| Library | Purpose | Licence | Source |
|---------|---------|---------|--------|
| Kotlin (JetBrains) | Programming language | Apache-2.0 | https://kotlinlang.org |
| AndroidX (AppCompat, Fragment, RecyclerView, ConstraintLayout, SwipeRefreshLayout, Lifecycle) | Core UI + lifecycle | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Material Components for Android | Material 3 UI, theming, dark mode | Apache-2.0 | https://github.com/material-components/material-components-android |
| Kotlin Coroutines | Asynchronous networking | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp (Square) | HTTP client for the REST API | Apache-2.0 | https://square.github.io/okhttp/ |
| Coil | Image loading | Apache-2.0 | https://coil-kt.github.io/coil/ |
| osmdroid | OpenStreetMap map view (artisan map + embedded maps) | Apache-2.0 | https://github.com/osmdroid/osmdroid |
| JUnit 4 | Unit testing | EPL-1.0 | https://junit.org/junit4/ |

## 3. Backend & tooling

- **Supabase** (Postgres, PostgREST, GoTrue auth) — hosted backend and REST API. https://supabase.com
- **OpenStreetMap** — map tile data rendered by osmdroid. © OpenStreetMap contributors, ODbL. https://www.openstreetmap.org/copyright
- **GitHub Actions** — CI build + automated testing. Workflow adapted from the module-supplied guides:
  - Automated build Android app with GitHub Action. https://github.com/marketplace/actions/automated-build-android-app-with-github-action [Accessed 3 November 2025].
  - IMAD5112 GitHub Actions sample `build.yml`. https://github.com/IMAD5112/Github-actions/blob/main/.github/workflows/build.yml [Accessed 3 November 2025].

## 4. Icons

- App launcher, navigation and UI icons are custom vector drawables authored for this
  project, based on the **Material Symbols / Material Design Icons** shapes
  (Apache-2.0). https://fonts.google.com/icons

## 5. Product & artisan imagery

All catalogue product photographs are royalty-free images from **Unsplash**, used
under the Unsplash License (free to use, no attribution required, though credited
here as good practice). https://unsplash.com/license

The product image URLs are stored in the `products.image_url` column on the backend
and loaded at runtime with Coil. Representative sources:

- Jewellery & beadwork — Unsplash. https://unsplash.com/s/photos/beaded-jewellery
- Leather goods — Unsplash. https://unsplash.com/s/photos/leather-bag
- Ceramics & homeware — Unsplash. https://unsplash.com/s/photos/ceramic-mug
- Food & preserves — Unsplash. https://unsplash.com/s/photos/honey-jar
- Textiles & décor — Unsplash. https://unsplash.com/s/photos/cushion

> Note: seller names, artisan stories and product descriptions are fictional and
> were written for this prototype; any resemblance to real businesses is
> coincidental. Map coordinates are the public locations of South African towns
> from OpenStreetMap.

## 6. Data

- South African town coordinates (for the artisan map and the "sell an item" location
  picker) are public geographic data from **OpenStreetMap** / Nominatim.
  https://nominatim.openstreetmap.org

---

*Prepared for the OPSC POE Part 2 submission. Part 1 research and planning
documents contain the fuller competitor analysis this app builds on.*
