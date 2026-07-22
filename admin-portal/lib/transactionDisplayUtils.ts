/**
 * Extract account number from system notes (Android stores "Account Number: xxx").
 */
export function extractAccountNumber(notes?: string | null, customerPhone?: string | null): string {
  if (notes) {
    const match = notes.match(/Account Number:\s*([^\n|]+)/i);
    if (match && match[1]) {
      return match[1].trim();
    }
  }
  if (customerPhone && customerPhone.trim()) {
    return customerPhone.trim();
  }
  return '';
}

/**
 * Comment for display: prefer agent userNotes; do not hide account info (shown separately).
 */
export function getTransactionComment(userNotes?: string | null, notes?: string | null): string {
  if (userNotes && userNotes.trim()) {
    return userNotes.trim();
  }
  if (!notes) return '';
  // Strip account-number metadata lines from system notes for the Comment column
  return notes
    .split('\n')
    .filter((line) => !/^\s*Account Number:/i.test(line) && !/Use in USSD/i.test(line))
    .join('\n')
    .trim();
}
