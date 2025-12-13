/**
 * Format a number as currency with thousands separators
 * @param amount - The amount to format
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted string with thousands separators (e.g., "1,000.00", "10,000.50")
 */
export function formatCurrency(amount: number | null | undefined, decimals: number = 2): string {
  if (amount === null || amount === undefined || isNaN(amount)) {
    return '0.00';
  }
  
  return amount.toLocaleString('en-US', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

/**
 * Format a number as currency with dollar sign and thousands separators
 * @param amount - The amount to format
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted string with $ prefix (e.g., "$1,000.00", "$10,000.50")
 */
export function formatCurrencyWithSymbol(amount: number | null | undefined, decimals: number = 2): string {
  return `$${formatCurrency(amount, decimals)}`;
}

