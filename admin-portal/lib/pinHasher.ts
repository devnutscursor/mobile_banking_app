import crypto from 'crypto';

const ALGORITHM = 'sha256';
const SALT_LENGTH = 16; // 16 bytes for salt

/**
 * Hashes a PIN with a randomly generated salt.
 * @param pin The 6-digit PIN to hash.
 * @returns A string containing the salt and the hashed PIN, separated by a colon.
 *         Format: "salt:hashedPin"
 */
export function hashPin(pin: string): string {
  if (!pin || pin.length !== 6 || !/^\d{6}$/.test(pin)) {
    throw new Error('PIN must be a 6-digit number.');
  }

  // Generate random salt
  const salt = crypto.randomBytes(SALT_LENGTH);

  // Hash PIN with salt
  const hashedPin = hashWithSalt(pin, salt);

  // Encode salt and hashed PIN to Base64 for storage
  const encodedSalt = salt.toString('base64');
  const encodedHashedPin = hashedPin.toString('base64');

  return `${encodedSalt}:${encodedHashedPin}`;
}

/**
 * Verifies a PIN against a stored hashed PIN.
 * @param pin The PIN to verify.
 * @param storedHashedPin The stored string in "salt:hashedPin" format.
 * @returns true if the PIN matches, false otherwise.
 */
export function verifyPin(pin: string, storedHashedPin: string): boolean {
  if (!pin || pin.length !== 6 || !/^\d{6}$/.test(pin)) {
    return false; // Invalid PIN format
  }
  if (!storedHashedPin || !storedHashedPin.includes(':')) {
    return false; // Invalid stored format
  }

  const parts = storedHashedPin.split(':');
  if (parts.length !== 2) {
    return false;
  }

  const encodedSalt = parts[0];
  const encodedStoredHash = parts[1];

  const salt = Buffer.from(encodedSalt, 'base64');
  const storedHash = Buffer.from(encodedStoredHash, 'base64');

  const hashedInputPin = hashWithSalt(pin, salt);

  // Use timing-safe comparison to prevent timing attacks
  return crypto.timingSafeEqual(hashedInputPin, storedHash);
}

/**
 * Internal function to hash a PIN with a salt.
 * @param pin The PIN to hash.
 * @param salt The salt to use.
 * @returns The hashed PIN as a Buffer.
 */
function hashWithSalt(pin: string, salt: Buffer): Buffer {
  const hash = crypto.createHash(ALGORITHM);
  hash.update(salt);
  hash.update(pin);
  return hash.digest();
}

