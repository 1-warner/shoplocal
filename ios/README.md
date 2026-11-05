# ShopLocal — iOS (SwiftUI)

A native **iOS** client for ShopLocal, written in SwiftUI. It talks to the **same
Supabase backend** as the Android app, so it shares the identical data, user
accounts and Google/Facebook sign-in — it's a second front-end, not a separate
system. (The OPSC PoE itself is the Android app; this iOS version is extra.)

## What it does

- Email/password **login & registration** (Supabase GoTrue).
- **Browse** the live product catalogue with category chips + search.
- **Product detail** with image, price, sale badge and the maker's origin.

The networking is plain `URLSession` async/await against Supabase's REST +
auth endpoints — no third-party SDK. See `Sources/ShopLocal/`:

| File | Purpose |
|------|---------|
| `Supabase.swift` | Backend config + a tiny REST/auth client |
| `Models.swift` | `Product`, `Session` (Codable) |
| `Store.swift` | App state: auth + catalogue (`ObservableObject`) |
| `Views.swift` | `LoginView`, `HomeView`, `ProductDetailView` |
| `ShopLocalApp.swift` | `@main` app entry point |

## Build & run

### Option A — xtool (Linux / WSL / Windows, no Mac)

[xtool](https://github.com/xtool-org/xtool) builds and installs iOS apps without
Xcode.

```bash
# one-time: install Swift 6+, then xtool (see xtool docs), then the Darwin SDK.
# IMPORTANT: xtool builds the iOS SDK from Apple's Xcode — there is no prebuilt
# SDK to download. Get Xcode.xip from https://developer.apple.com/download/all
# (Apple ID required, ~10 GB), then:
xtool sdk install /path/to/Xcode.xip

# from this ios/ folder:
xtool dev build            # compiles the app for iOS → .ipa
xtool install <app>.ipa    # signs (with your Apple ID) + installs to a connected iPhone
```

Notes:
- **You don't need a Mac**, but you *do* need Apple's **Xcode.xip** once (to extract
  the iOS SDK) — that's the big requirement.
- Installing to a physical iPhone needs your **Apple ID** (free provisioning = 7-day
  signing) and the device reachable over USB. On Windows this goes through WSL USB
  passthrough (`usbipd-win`), which is finicky; a Linux or macOS host is more reliable.

### Option B — Xcode (Mac)

Open the package in Xcode (`File → Open` the `ios/` folder), pick your iPhone as the
run destination, set your signing team, and press **Run**.

> The `anon` key in `Supabase.swift` is a public client key by design — Row-Level
> Security on the server is what protects the data.
