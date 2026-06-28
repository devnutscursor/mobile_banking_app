# Security Overview — Mobile Banking App

This document describes authentication, credential storage, and known gaps for client review.

## Admin portal (web)

- **Authentication:** Firebase Auth (email/password). Passwords are hashed server-side by Firebase; the portal never stores raw passwords.
- **Authorization:** Role checks in `admin-portal/lib/authContext.tsx` (admin, dealer, agent routes).
- **Bot protection:** Cloudflare Turnstile on login when `NEXT_PUBLIC_TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY` are configured. Bypassed on localhost or when keys are unset (development).

## Mobile app — online login

- **Email + password:** Firebase Authentication. Passwords handled by Firebase (hashed server-side).
- **Phone + PIN:** Custom flow against Firestore `users` collection. PINs stored as SHA-256 + salt via `PinHasher.java` (format `salt:hash`).

## Mobile app — offline login

- **Email credentials:** Stored locally in Room `credentials` table. **Fixed:** passwords are now hashed with SHA-256 + salt via `PasswordHasher.java` (legacy plaintext entries are migrated on next successful login).
- **Session:** Local session state in Room; license validation cached in SharedPreferences.

## Firestore

- **Current rules:** Open read/write (`allow read, write: if true`) — required for phone+PIN login without Firebase Auth on mobile.
- **Risk:** Any client can read/write collections without authentication. Production hardening should use scoped rules and/or Cloud Functions for sensitive writes (license activation, balance changes).
- **License activation:** Uses Firestore transaction to enforce `maxAgentCount` atomically. License keys use crypto-random suffixes in admin portal.

## License security

- Activation assigns users via `arrayUnion` on `assignedToUserId`.
- Admin portal queries licenses with client-side array filtering (supports legacy string and array formats).
- **Remaining:** Server-side license writes restricted to admin; optional Cloud Function for activation.

## Recommendations (future)

1. Tighten Firestore rules with custom claims or App Check.
2. PIN brute-force: rate limiting / lockout after N failures.
3. Move license activation to a Cloud Function.
4. Consider Firebase Auth custom tokens for phone users to enable proper security rules.

## Related files

| Area | Path |
|------|------|
| PIN hashing | `app/.../utils/PinHasher.java` |
| Offline password hashing | `app/.../utils/PasswordHasher.java` |
| License activation | `app/.../utils/LicenseManager.java` |
| Session / credentials | `app/.../utils/SessionManager.java` |
| Admin auth | `admin-portal/lib/authContext.tsx` |
| Turnstile verify | `admin-portal/app/api/verify-turnstile/route.ts` |
