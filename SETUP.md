# ShopLocal — one-time setup for Firebase & Google SSO

The app **builds and runs without this**, but three Part 3 features only come alive
once you connect your own Firebase project and Google OAuth client:

| Feature | Needs |
|---|---|
| Real-time push notifications | Firebase Cloud Messaging |
| NoSQL "My activity" feed | Firebase Firestore |
| Google single sign-on (SSO) | Google OAuth client + Supabase Google provider |

It takes about 10 minutes. The repo ships a **placeholder** `app/google-services.json`
so CI stays green; you replace it with your real one below.

---

## 1. Firebase project (push + NoSQL)

1. Go to <https://console.firebase.google.com> and **Add project** (e.g. "ShopLocal").
2. In the project, **Add app → Android**. Use package name **`com.noor.shoplocal`**.
3. **Download `google-services.json`** and drop it into the **`app/`** folder,
   replacing the placeholder.
4. **Build → Firestore Database → Create database** → start in **test mode** (or add
   a rule allowing authenticated reads/writes to the `activity` collection).
5. **Run → Messaging** is enabled automatically. To test a push:
   **Messaging → New campaign → Notification**, or **Cloud Messaging → Send test
   message** to the device's FCM token (it's logged under the `FCM` tag in Logcat
   when the app starts).

That's it — the **My activity** screen (Profile → My activity) now reads/writes
Firestore, and pushes arrive as notifications.

## 2. SSO — Google & Facebook (part of the PoE)

The app already has **Continue with Google** and **Continue with Facebook** buttons
and the `shoplocal://auth-callback` deep link. Supabase handles the OAuth — you just
create the provider apps and plug the credentials in.

### 2a. Create the OAuth apps (console only — can't be scripted)

**Google** — *Google Cloud Console → APIs & Services → Credentials → Create OAuth
client ID → Web application*. Authorised redirect URI:
`https://swchtpvdtncslvlsxeul.supabase.co/auth/v1/callback`. Copy the **Client ID**
and **Client secret**.

**Facebook** — *developers.facebook.com → Create App → Consumer → add "Facebook
Login" → Settings*. Valid OAuth Redirect URI:
`https://swchtpvdtncslvlsxeul.supabase.co/auth/v1/callback`. From *Settings → Basic*
copy the **App ID** and **App secret**.

### 2b. Configure Supabase — the CLI does this for you

Instead of clicking through the Supabase dashboard, run the bundled script:

```bash
cp scripts/sso.env.example scripts/sso.env   # then fill in the 5 values
./scripts/setup-sso.sh
```

`sso.env` needs a **Supabase personal access token**
(<https://supabase.com/dashboard/account/tokens>) plus the Google/Facebook client
IDs + secrets from step 2a. The script enables both providers and sets the
`shoplocal://auth-callback` redirect allow-list via the Supabase Management API.

> Prefer the dashboard? *Authentication → Providers → Google / Facebook* (enable +
> paste credentials) and *Authentication → URL Configuration → Redirect URLs* (add
> `shoplocal://auth-callback`) does the same thing.

Now the Google/Facebook buttons open the provider, and the app captures the redirect
and signs you in.

---

## 3. Preparing a signed release (Play Store)

Generate a keystore and build a signed release:

```bash
keytool -genkey -v -keystore shoplocal-release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias shoplocal

./gradlew assembleRelease     # or bundleRelease for an .aab
```

Put the signing details in `keystore.properties` (git-ignored) — see
`keystore.properties.example`. The signed APK/AAB is under
`app/build/outputs/`. Upload the **.aab** to the Google Play Console to create your
store listing, then screenshot the uploaded app for the submission.
