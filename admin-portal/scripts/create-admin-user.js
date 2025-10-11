#!/usr/bin/env node

/**
 * Admin User Creation Script
 * 
 * This script creates an admin user in Firebase Auth and Firestore.
 * Run this script once to set up the initial admin user.
 * 
 * Usage: node scripts/create-admin-user.js
 * 
 * IMPORTANT: Delete this script after running it for security!
 */

const { initializeApp } = require('firebase/app');
const { getAuth, createUserWithEmailAndPassword } = require('firebase/auth');
const { getFirestore, doc, setDoc, serverTimestamp } = require('firebase/firestore');

// Firebase configuration (same as .env.local)
const firebaseConfig = {
  apiKey: "AIzaSyAO_hkW3YfU7YYNrWmxRIQ42WFUa8ita-g",
  authDomain: "mobile-banking-app-b6a40.firebaseapp.com",
  projectId: "mobile-banking-app-b6a40",
  storageBucket: "mobile-banking-app-b6a40.firebasestorage.app",
  messagingSenderId: "121791230823",
  appId: "1:121791230823:android:a676a7c2e2c456d66875c2"
};

// Admin credentials
const ADMIN_EMAIL = "admin@test.com";
const ADMIN_PASSWORD = "admin123";
const ADMIN_NAME = "Admin User";

async function createAdminUser() {
  try {
    console.log('🚀 Initializing Firebase...');
    
    // Initialize Firebase
    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    const db = getFirestore(app);

    console.log('📧 Creating admin user in Firebase Auth...');
    
    // Create user in Firebase Auth
    const userCredential = await createUserWithEmailAndPassword(auth, ADMIN_EMAIL, ADMIN_PASSWORD);
    const user = userCredential.user;
    
    console.log('✅ Admin user created in Firebase Auth!');
    console.log(`   UID: ${user.uid}`);
    console.log(`   Email: ${user.email}`);

    console.log('📝 Creating admin user document in Firestore...');
    
    // Create user document in Firestore
    const userDocRef = doc(db, 'users', user.uid);
    await setDoc(userDocRef, {
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
      creditUpdatedAt: serverTimestamp()
    });

    console.log('✅ Admin user document created in Firestore!');

    console.log('\n🎉 Admin user setup complete!');
    console.log('\n📋 Login Credentials:');
    console.log(`   Email: ${ADMIN_EMAIL}`);
    console.log(`   Password: ${ADMIN_PASSWORD}`);
    console.log('\n🔗 You can now login to the admin portal at: http://localhost:3000');
    
    console.log('\n⚠️  SECURITY WARNING:');
    console.log('   - Delete this script file after running it');
    console.log('   - Change the default password in production');
    console.log('   - Consider using environment variables for credentials');

    process.exit(0);

  } catch (error) {
    console.error('❌ Error creating admin user:', error.message);
    
    if (error.code === 'auth/email-already-in-use') {
      console.log('\n💡 The admin user already exists!');
      console.log('   You can login with the existing credentials:');
      console.log(`   Email: ${ADMIN_EMAIL}`);
      console.log(`   Password: ${ADMIN_PASSWORD}`);
    } else if (error.code === 'auth/weak-password') {
      console.log('\n💡 Password is too weak. Please use a stronger password.');
    } else if (error.code === 'auth/invalid-email') {
      console.log('\n💡 Invalid email format.');
    }
    
    process.exit(1);
  }
}

// Run the script
createAdminUser();

