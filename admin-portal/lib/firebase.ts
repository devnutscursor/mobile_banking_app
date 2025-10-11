import { initializeApp, getApps, getApp, FirebaseApp } from 'firebase/app';
import { getAuth, Auth, signOut } from 'firebase/auth';
import { getFirestore, Firestore } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

// Initialize Firebase
let app: FirebaseApp;
let auth: Auth;
let db: Firestore;

if (typeof window !== 'undefined') {
  if (!getApps().length) {
    app = initializeApp(firebaseConfig);
  } else {
    app = getApps()[0];
  }
  
  auth = getAuth(app);
  db = getFirestore(app);
}

export { auth, db };

// Provide a secondary auth instance that will NOT disturb the primary session
export function getSecondaryAuth(): Auth | undefined {
  if (typeof window === 'undefined') return undefined;
  const name = 'secondary';
  let secondaryApp: FirebaseApp;
  try {
    secondaryApp = getApp(name);
  } catch {
    secondaryApp = initializeApp(firebaseConfig, name);
  }
  return getAuth(secondaryApp);
}

// Utility to cleanup secondary auth after use
export async function signOutSecondary(): Promise<void> {
  const sec = getSecondaryAuth();
  if (sec) {
    try { await signOut(sec); } catch {}
  }
}


