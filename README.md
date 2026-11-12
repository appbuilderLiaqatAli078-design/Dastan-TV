# Dastan TV — Premium Turkish Drama OTT Application (Urdu Dubbed & Subtitled)

**Developer:** Liaquat Ali  
**Platform:** Android (Kotlin + Jetpack Compose + Material 3)  
**Theme:** Midnight Cinema (`#101019` Background, `#20152E` Secondary, `#A879FF` Violet Accent, `#E9C77B` Ottoman Gold)  
**Architecture:** Single Android Application (APK/AAB) with Role-Based User & Admin Workspaces

---

## 1. Architecture Overview

Dastan TV is a production-ready OTT streaming application for Turkish historical dramas dubbed and subtitled in Urdu:
- **Single APK / AAB with Dual Role-Based Workspaces:** Standard viewers experience the full consumer streaming app, while the verified administrator (`rdxkinglucky@gmail.com`) unlocks the in-app **Admin Console** (`AdminDashboardScreen.kt`).
- **Live Content API Integration (`https://giveme5.co/wp-json/wp/v2`):**
  - Fetches categories (`GET /categories?per_page=100`) and paginated episode posts (`GET /posts?categories={categoryId}&per_page=50&page={page}&_embed`).
  - Extracts featured posters (`_embedded["wp:featuredmedia"]`) and authorized video embeds (`vidmoly.org` and `ok.ru`), with retry logic, caching, duplicate filtering, and API health telemetry.
- **Hardened Multi-Server Video Player:**
  - Evaluates Vidmoly and OK.ru embeds in a hardened Android `WebView` with ad-domain blocking, full-screen landscape toggle, transport controls, Urdu subtitle indicator, episode selector, and automatic playback progress sync to Firestore.

---

## 2. Firebase & Zero-Trust Admin Role Setup

1. **Authentication:** Uses Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`).
2. **Firestore Database:** Connects to the provisioned named enterprise Firestore database via `R.string.firestore_database_id`.
3. **Assigning the Admin Role (`rdxkinglucky@gmail.com`):**
   - Run the backend admin script (`backend/scripts/set-admin-role.ts`) using a Firebase Service Account:
     ```bash
     cd backend
     npm install
     export GOOGLE_APPLICATION_CREDENTIALS="/path/to/serviceAccountKey.json"
     npm run set-admin
     ```
   - `firestore.rules` enforces zero-trust server-side checks via `isAdmin()` (verifying `/admins/$(request.auth.uid)` or `request.auth.token.email_verified == true && request.auth.token.email == 'rdxkinglucky@gmail.com'`).

---

## 3. Building APK & AAB Locally and via GitHub Actions

- **Compile & Assemble Debug APK:**
  ```bash
  gradle :app:assembleDebug
  ```
- **Build Android App Bundle (AAB):**
  ```bash
  gradle :app:bundleRelease
  ```
- **GitHub Actions CI/CD:** Configured in `.github/workflows/android-build.yml` to run unit tests, Android Lint, and build APK/AAB artifacts automatically on every push to `main`.
