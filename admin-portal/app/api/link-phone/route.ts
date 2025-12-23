import { NextRequest, NextResponse } from 'next/server';
import admin from 'firebase-admin';
import fs from 'fs';
import path from 'path';

// Load service account JSON from the project root using an absolute path.
// This avoids bundler path resolution issues while keeping the key on the server only.
function getServiceAccount() {
  const serviceAccountPath = path.join(process.cwd(), 'scripts', 'serviceAccountKey.json');
  const raw = fs.readFileSync(serviceAccountPath, 'utf8');
  return JSON.parse(raw);
}

if (!admin.apps.length) {
  const serviceAccount = getServiceAccount();
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount as admin.ServiceAccount),
    databaseURL: 'https://mobile-banking-app-b6a40-default-rtdb.asia-southeast1.firebasedatabase.app',
  });
}

export async function POST(request: NextRequest) {
  try {
    const { uid, phone } = await request.json();

    if (!uid || !phone) {
      return NextResponse.json({ error: 'uid and phone are required' }, { status: 400 });
    }

    // Normalize phone like the mobile app and script do
    const normalizedPhone = String(phone).replace(/[\s\-()]/g, '');

    await admin.auth().updateUser(uid, { phoneNumber: normalizedPhone });

    return NextResponse.json({ success: true });
  } catch (error: any) {
    return NextResponse.json(
      { error: error?.message || 'Failed to link phone' },
      { status: 400 },
    );
  }
}


