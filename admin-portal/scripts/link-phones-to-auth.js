#!/usr/bin/env node

/**
 * Link Firestore user phone numbers to Firebase Auth users.
 *
 * Goal:
 * - For every document in `users` that has a non-empty `phone` and `email`,
 *   set that phone number on the SAME Firebase Auth user (by email).
 * - After running this once, phone OTP sign-in will hit the same UID
 *   as email/password sign-in, so licenses keep working.
 *
 * Usage:
 *   1) Install dependencies (once):
 *        cd admin-portal
 *        npm install firebase-admin
 *   2) Set GOOGLE_APPLICATION_CREDENTIALS to your service account JSON, e.g.:
 *        set GOOGLE_APPLICATION_CREDENTIALS=path\to\serviceAccount.json   (Windows)
 *        export GOOGLE_APPLICATION_CREDENTIALS=path/to/serviceAccount.json (macOS/Linux)
 *   3) Run:
 *        node scripts/link-phones-to-auth.js
 *
 * This script is idempotent: re-running it will just update phone numbers again.
 */

const admin = require('firebase-admin');

// Initialize Admin SDK using a service account directly.
// Place your service account JSON in this folder as "serviceAccountKey.json".
// This script is for local/admin use only and should not be committed with real keys.
const serviceAccount = require('./serviceAccountKey.json');

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: 'https://mobile-banking-app-b6a40-default-rtdb.asia-southeast1.firebasedatabase.app',
  });
}

const auth = admin.auth();
const db = admin.firestore();

async function linkPhonesToAuth() {
  console.log('🔄 Starting phone ↔ Auth sync...');

  const usersRef = db.collection('users');
  const snapshot = await usersRef.get();

  if (snapshot.empty) {
    console.log('ℹ️ No users found in Firestore.');
    return;
  }

  let processed = 0;
  let updated = 0;
  let skipped = 0;
  let errors = 0;

  for (const doc of snapshot.docs) {
    processed++;
    const data = doc.data();
    const uid = data.uid || doc.id;
    const email = data.email;
    const phone = data.phone;

    // We only care about users that have BOTH email and phone
    if (!email || !phone) {
      skipped++;
      continue;
    }

    // Normalize phone (same as app: remove spaces/dashes/parentheses)
    const normalizedPhone = String(phone).replace(/[\s\-()]/g, '');

    try {
      // Load Auth user by UID if possible, otherwise by email
      let userRecord;
      try {
        userRecord = await auth.getUser(uid);
      } catch {
        userRecord = await auth.getUserByEmail(email);
      }

      // If phone already matches, skip
      if (userRecord.phoneNumber === normalizedPhone) {
        skipped++;
        continue;
      }

      await auth.updateUser(userRecord.uid, {
        phoneNumber: normalizedPhone,
      });

      console.log(`✅ Linked phone ${normalizedPhone} to Auth user ${userRecord.uid} (${email})`);
      updated++;
    } catch (e) {
      console.error(`❌ Failed to link phone for Firestore user ${doc.id} (${email} / ${phone}):`, e.message);
      errors++;
    }
  }

  console.log('\n📊 Sync complete:');
  console.log(`   Processed Firestore users: ${processed}`);
  console.log(`   Updated Auth users       : ${updated}`);
  console.log(`   Skipped (no email/phone or already linked): ${skipped}`);
  console.log(`   Errors                   : ${errors}`);
}

linkPhonesToAuth()
  .then(() => {
    console.log('\n🎉 Done.');
    process.exit(0);
  })
  .catch((err) => {
    console.error('Unexpected error:', err);
    process.exit(1);
  });


