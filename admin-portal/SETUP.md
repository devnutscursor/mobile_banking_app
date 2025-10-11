# Admin Portal Setup Guide

## Quick Start

### Step 1: Install Dependencies

```bash
cd admin-portal
npm install
```

### Step 2: Configure Firebase

The `.env.local` file has already been created with your Firebase configuration. If you need to update it, edit the file with your credentials.

### Step 3: Create an Admin User

You need to create an admin user in Firebase before you can login. You have two options:

#### Option A: Using Firebase Console (Recommended)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `mobile-banking-app-b6a40`
3. Go to **Authentication** > **Users** > **Add user**
4. Create a user with:
   - Email: `admin@test.com` (or your preferred email)
   - Password: `admin123` (or your preferred password)
5. Copy the **User UID** from the user list
6. Go to **Firestore Database** > **users** collection
7. Click **Add document**
8. Document ID: Paste the User UID you copied
9. Add these fields:

```
uid: (the User UID)
email: admin@test.com
name: Admin User
phone: null
role: admin
dealerId: null
active: true
createdAt: (click "Add timestamp" and select current time)
updatedAt: (click "Add timestamp" and select current time)
```

10. Click **Save**

#### Option B: Using Firebase CLI

If you have Firebase CLI installed:

```bash
# Login to Firebase
firebase login

# Set project
firebase use mobile-banking-app-b6a40

# Create admin user programmatically
# (You'll need to write a small script or use Firebase Console as above)
```

### Step 4: Run the Development Server

```bash
npm run dev
```

Visit [http://localhost:3000](http://localhost:3000)

### Step 5: Login

Use the credentials you created:
- Email: `admin@test.com`
- Password: `admin123`

## Creating Additional Admin Users

To create more admin users:

1. Use Firebase Console to create the user in Authentication
2. Add a document to the `users` collection with `role: "admin"`
3. Ensure the document ID matches the Firebase Auth UID

## Troubleshooting

### "Access denied. Admin privileges required"
- Check that the user's role in Firestore is set to "admin"
- Verify the document ID matches the Firebase Auth UID

### "User profile not found"
- Make sure you created the user document in Firestore
- Check that the document ID matches exactly

### Firebase Connection Issues
- Verify your `.env.local` file has correct credentials
- Check Firebase project settings match your configuration

### Build Errors
- Run `npm install` again
- Delete `node_modules` and `.next` folders, then run `npm install`

## Production Deployment

### Option 1: Vercel (Recommended)

1. Install Vercel CLI: `npm i -g vercel`
2. Run: `vercel`
3. Follow the prompts
4. Set environment variables in Vercel dashboard

### Option 2: Firebase Hosting

1. Install Firebase CLI: `npm install -g firebase-tools`
2. Build the project: `npm run build`
3. Initialize Firebase: `firebase init hosting`
4. Deploy: `firebase deploy --only hosting`

### Environment Variables for Production

Make sure to set these in your hosting platform:
- `NEXT_PUBLIC_FIREBASE_API_KEY`
- `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN`
- `NEXT_PUBLIC_FIREBASE_PROJECT_ID`
- `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET`
- `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID`
- `NEXT_PUBLIC_FIREBASE_APP_ID`

## Security Rules

Don't forget to set up proper Firestore security rules for production:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Admin-only write access to sensitive collections
    match /users/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && 
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    
    match /licenses/{licenseId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && 
        get(/databases/$(database)/documents/users/$(request.auth.uid)).data.role == 'admin';
    }
    
    // Other collections remain readable by authenticated users
    match /{document=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

## Default Login Credentials

After setup, you can login with:
- **Email**: `admin@test.com`
- **Password**: `admin123`

**Important**: Change these credentials in production!

## Next Steps

1. Login to the admin portal
2. Create dealer accounts
3. Create agent accounts and assign them to dealers
4. Issue licenses to dealers and agents
5. Monitor transactions and user activity

## Support

For issues or questions:
1. Check the main README.md
2. Review Firebase Console for errors
3. Check browser console for error messages





