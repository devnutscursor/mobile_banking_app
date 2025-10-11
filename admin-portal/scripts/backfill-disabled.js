#!/usr/bin/env node

/**
 * One-time backfill: add `disabled: false` to all users missing the field.
 * Usage: node scripts/backfill-disabled.js
 */

const { initializeApp } = require('firebase/app');
const { getFirestore, collection, getDocs, doc, updateDoc } = require('firebase/firestore');

// Reuse the same config as .env.local; hard-coded here for convenience
const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || 'AIzaSyAO_hkW3YfU7YYNrWmxRIQ42WFUa8ita-g',
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || 'mobile-banking-app-b6a40.firebaseapp.com',
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || 'mobile-banking-app-b6a40',
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || 'mobile-banking-app-b6a40.firebasestorage.app',
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || '121791230823',
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || '1:121791230823:android:a676a7c2e2c456d66875c2',
};

async function run() {
  const app = initializeApp(firebaseConfig);
  const db = getFirestore(app);

  console.log('🔍 Fetching users...');
  const snap = await getDocs(collection(db, 'users'));

  let total = 0;
  let updated = 0;
  for (const d of snap.docs) {
    total += 1;
    const data = d.data();
    if (typeof data.disabled === 'undefined') {
      await updateDoc(doc(db, 'users', d.id), { disabled: false });
      updated += 1;
      console.log(`✅ Set disabled:false for ${d.id} (${data.email || data.name || 'unknown'})`);
    }
  }

  console.log(`\nDone. Scanned: ${total}, Backfilled: ${updated}.`);
}

run().catch((e) => {
  console.error('❌ Backfill failed:', e);
  process.exit(1);
});





