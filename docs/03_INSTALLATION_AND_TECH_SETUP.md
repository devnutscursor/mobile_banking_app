# Installation & Tech Setup — Android (Java) + Firebase (English)

This guide explains installation, configuration, and technology choices to start the project in Android (Java) with Firebase, offline-first storage, USSD, and PDF417 scanning.

---

## 1) Prerequisites
- Android Studio (Giraffe+), Java 17 (or compatible with chosen Android SDK)
- Firebase project (Firestore, Auth, Cloud Functions, Storage)
- Android device with a SIM card for USSD tests
- Internet access for initial configuration

---

## 2) Create Android Project (Java)
1. Android Studio → New Project → Empty Views Activity → Language: Java → Min SDK: 23+
2. Enable ViewBinding (or Compose later). Start simple with ViewBinding.
3. Configure Gradle dependencies:
```gradle
plugins {
    id 'com.android.application'
    id 'com.google.gms.google-services'
}

dependencies {
    implementation platform('com.google.firebase:firebase-bom:33.2.0')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-firestore'
    implementation 'com.google.firebase:firebase-storage'

    implementation 'androidx.room:room-runtime:2.6.1'
    annotationProcessor 'androidx.room:room-compiler:2.6.1'

    implementation 'com.google.mlkit:barcode-scanning:17.2.0'
}
```
Adjust versions as needed.

---

## 3) Firebase Integration
1. Create a Firebase project → Add Android app.
2. Provide `applicationId` and SHA-1 if required.
3. Download `google-services.json` and place it under `app/`.
4. Enable products: Authentication (Email/Password), Firestore, Storage.
5. (Later) Cloud Functions for server logic (licensing, audits, derived balances).

Tip: Use Firebase BoM to align versions; keep the google-services plugin enabled.

---

## 4) Local Database (Offline-First)
- Use Room for local tables: `Customer`, `Operator`, `OperatorAction`, `Transaction`, `LicenseCache`, `CashMovement`, `VirtualLedgerEntry`, etc.
- Implement a `SyncQueue` for pending operations.
- Sync strategy: WorkManager periodic jobs and retries with backoff.

---

## 5) USSD (Android)
- Launch USSD with Intent `ACTION_DIAL` and `tel:` scheme:
```java
String ussd = "*144*2*1*" + number + "*" + amount + Uri.encode("#");
Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ussd));
startActivity(intent);
```
Notes:
- iOS does not support USSD.
- Android requires user confirmation in the dialer UI.
- Encode `#` using `Uri.encode("#")`.

---

## 6) PDF417 Scan (ML Kit)
- Add dependency `com.google.mlkit:barcode-scanning`.
- Use CameraX or Camera2; capture frames and pass to ML Kit.
- Filter for PDF417; parse the fields with a country-specific mapping.

---

## 7) Internationalization
- `res/values/strings.xml` (EN default)
- `res/values-fr/strings.xml` (FR) if needed later
- Optional dynamic `Locale` switch.

---

## 8) Security & Storage
- Encrypt sensitive data at rest (EncryptedSharedPreferences, optional SQLCipher for Room).
- Strict Firestore rules: role-based access (admin, dealer, agent) and valid licenses.
- Store proofs (receipts, photos) in Firebase Storage with controlled access.

---

## 9) Licensing (MVP)
- Firestore `licenses` collection: { key, assignedToUserId, deviceId, validUntil, active }
- App: on login → read license for user+device → cache in `LicenseCache` with TTL.
- Cloud Function: admin creates/activates/revokes licenses.

---

## 10) Synchronization
- Periodic WorkManager task (e.g., every 15 min):
  - Push `SyncQueue` to Firestore
  - Fetch updates since `lastSync` → apply locally
  - Resolve conflicts (last-write-wins + domain rules)

---

## 11) Logging & Audit
- Local logs + periodic upload of key events.
- Firestore/Functions to record: transactions, movements, sign-ins, license changes.

---

## 12) Build & Test
- Build variants: `dev`, `prod` (optional separate google-services configs)
- Tests: USSD intent URI verification, Room DAO tests, simulated sync tests.

---

## 13) Common Issues
- USSD not launching: check `#` encoding, ensure a dialer app is available.
- PDF417 scan accuracy: lighting, focus, correct country mapping.
- Sync conflicts: inconsistent timestamps → prefer server timestamps where possible.
