#!/usr/bin/env node

/**
 * Creates an admin user in Firebase Auth and Firestore.
 * Usage: npm run create-admin
 *
 * Reads config from admin-portal/.env.local
 */

const fs = require('fs');
const path = require('path');
const { initializeApp } = require('firebase/app');
const { getAuth, createUserWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, doc, setDoc, serverTimestamp } = require('firebase/firestore');

function loadEnvLocal() {
  const envPath = path.join(__dirname, '..', '.env.local');
  if (!fs.existsSync(envPath)) {
    throw new Error('Missing .env.local — copy .env.example and fill in Firebase values.');
  }

  fs.readFileSync(envPath, 'utf8')
    .split('\n')
    .forEach((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) return;
      const eq = trimmed.indexOf('=');
      if (eq === -1) return;
      const key = trimmed.slice(0, eq).trim();
      const value = trimmed.slice(eq + 1).trim();
      if (!process.env[key]) process.env[key] = value;
    });
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing ${name} in .env.local`);
  }
  return value;
}

loadEnvLocal();

const firebaseConfig = {
  apiKey: requireEnv('NEXT_PUBLIC_FIREBASE_API_KEY'),
  authDomain: requireEnv('NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN'),
  projectId: requireEnv('NEXT_PUBLIC_FIREBASE_PROJECT_ID'),
  storageBucket: requireEnv('NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET'),
  messagingSenderId: requireEnv('NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID'),
  appId: requireEnv('NEXT_PUBLIC_FIREBASE_APP_ID'),
};

const ADMIN_EMAIL = requireEnv('ADMIN_EMAIL');
const ADMIN_PASSWORD = requireEnv('ADMIN_PASSWORD');
const ADMIN_NAME = process.env.ADMIN_NAME || 'Admin User';

async function createAdminUser() {
  try {
    console.log(`🚀 Connecting to Firebase project: ${firebaseConfig.projectId}`);

    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    const db = getFirestore(app);

    console.log('📧 Creating admin user in Firebase Auth...');
    const userCredential = await createUserWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
    const user = userCredential.user;

    console.log('✅ Admin user created in Firebase Auth');
    console.log(`   UID: ${user.uid}`);
    console.log(`   Email: ${user.email}`);

    console.log('📝 Creating admin user document in Firestore...');
    await setDoc(doc(db, 'users', user.uid), {
      uid: user.uid,
      email: ADMIN_EMAIL,
      name: ADMIN_NAME,
      phone: null,
      role: 'admin',
      dealerId: null,
      active: true,
      disabled: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      virtualCredit: 0,
      totalCreditUsed: 0,
      totalCreditEarned: 0,
      creditUpdatedAt: serverTimestamp(),
    });

    console.log('✅ Admin user document created in Firestore');
    console.log('\n🎉 Setup complete. Admin portal login:');
    console.log(`   Email: ${ADMIN_EMAIL}`);
    console.log(`   Password: ${ADMIN_PASSWORD}`);
    console.log('\n   Run: npm run dev  →  http://localhost:3000/login');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error creating admin user:', error.message);

    if (error.code === 'auth/email-already-in-use') {
      console.log('\n💡 Admin already exists in Firebase Auth.');
      console.log(`   Try logging in with: ${ADMIN_EMAIL}`);
      console.log('   If Firestore profile is missing, add users/{uid} manually in Firebase Console.');
    } else if (error.code === 'auth/operation-not-allowed') {
      console.log('\n💡 Enable Email/Password in Firebase Console → Authentication → Sign-in method.');
    } else if (error.code === 'auth/weak-password') {
      console.log('\n💡 Use a stronger ADMIN_PASSWORD in .env.local (min 6 characters).');
    }

    process.exit(1);
  }
}

createAdminUser();
