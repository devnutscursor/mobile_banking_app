const { initializeApp } = require('firebase/app');
const { getFirestore, collection, getDocs, limit, query } = require('firebase/firestore');

const app = initializeApp({
  apiKey: 'AIzaSyBh8PXOqnhxBT7_8nvYTg3RITS3DiOSHSg',
  authDomain: 'mobile-banking-app-c9033.firebaseapp.com',
  projectId: 'mobile-banking-app-c9033',
  storageBucket: 'mobile-banking-app-c9033.firebasestorage.app',
});
const db = getFirestore(app);

(async () => {
  const snap = await getDocs(query(collection(db, 'transactions'), limit(100)));
  const rows = snap.docs.map((d) => {
    const x = d.data();
    const sig = x.signatureUrl || null;
    let sigKind = 'none';
    if (sig) {
      if (sig.startsWith('https://')) sigKind = 'https';
      else if (sig.startsWith('data:')) sigKind = 'base64';
      else if (sig.startsWith('file://')) sigKind = 'file';
      else sigKind = 'other';
    }
    return {
      id: d.id,
      amount: x.amount,
      actionName: x.actionName,
      status: x.status,
      sigKind,
      signatureUrlPreview: sig ? String(sig).slice(0, 80) : null,
      createdAt: x.createdAt?.toDate?.()?.toISOString?.() || String(x.createdAt || ''),
      customerName: x.customerName,
      userName: x.userName,
    };
  });
  rows.sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  console.log('RECENT_15:');
  console.log(JSON.stringify(rows.slice(0, 15), null, 2));
  const withSig = rows.filter((r) => r.sigKind !== 'none');
  console.log('STATS total=', rows.length, 'withSignature=', withSig.length);
  console.log('BY_KIND', {
    https: withSig.filter((r) => r.sigKind === 'https').length,
    base64: withSig.filter((r) => r.sigKind === 'base64').length,
    file: withSig.filter((r) => r.sigKind === 'file').length,
    other: withSig.filter((r) => r.sigKind === 'other').length,
  });
  const target = rows.find(
    (r) =>
      (r.id || '').toLowerCase().includes('5fdfb921c698') ||
      (r.id || '').toLowerCase().includes('b0563a346465')
  );
  console.log('TARGET:', JSON.stringify(target, null, 2));
  process.exit(0);
})().catch((e) => {
  console.error('ERROR', e);
  process.exit(1);
});
