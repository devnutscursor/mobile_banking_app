# Admin User Creation Script

## 🚀 Quick Setup

This script automatically creates an admin user in Firebase Auth and Firestore.

### Run the Script

```bash
# From the admin-portal directory
npm run create-admin
```

Or directly:
```bash
node scripts/create-admin-user.js
```

## 📋 What It Does

1. **Creates Firebase Auth User**:
   - Email: `admin@test.com`
   - Password: `admin123`

2. **Creates Firestore Document**:
   - Document ID: User's UID
   - Role: `admin`
   - All required fields with timestamps

3. **Provides Login Credentials**:
   - Shows the exact credentials to use
   - Confirms successful creation

## 🔐 Default Credentials

- **Email**: `admin@test.com`
- **Password**: `admin123`

## ⚠️ Security Notes

1. **Delete After Use**: Remove this script after running it
2. **Change Password**: Update the password in production
3. **Environment Variables**: Consider using env vars for credentials

## 🛠️ Troubleshooting

### "Email already in use"
- The admin user already exists
- You can login with the existing credentials

### "Weak password"
- The password doesn't meet Firebase requirements
- Update the password in the script

### "Invalid email"
- Check the email format in the script

## 🎯 After Running

1. **Delete the script**: `rm scripts/create-admin-user.js`
2. **Start the portal**: `npm run dev`
3. **Login**: Use the credentials shown by the script
4. **Change password**: Update in Firebase Console for production

## 📝 Manual Alternative

If the script fails, you can create the admin user manually:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select project: `mobile-banking-app-b6a40`
3. Authentication → Add user
4. Firestore → users collection → Add document
5. Use the same structure as in the script




