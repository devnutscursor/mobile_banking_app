# Bluetooth Receipt Printing — Decision Document

## Options

### Option A: Share sheet + PNG (current, improved)
- **Pros:** No Bluetooth permissions; works with any printer app (MPT-II, RawBT, etc.); simpler maintenance.
- **Cons:** Extra tap to pick printer app; less seamless UX.

### Option B: Direct ESC/POS over Bluetooth SPP
- **Pros:** One-tap print to paired thermal printer; best UX for dedicated devices.
- **Cons:** Requires `BLUETOOTH_CONNECT` permission (Android 12+); device-specific quirks; more code to maintain.

## Decision

**Hybrid approach (implemented):**
1. Receipt PNG uses localized header/footer strings and improved layout (share sheet path).
2. `ReceiptPrinterHelper` offers optional direct Bluetooth ESC/POS when a printer MAC is saved in preferences.
3. Print flow: try Bluetooth if configured → otherwise share PNG.

## Client action

Pair a 58mm thermal printer in Android settings, then set the MAC address in app preferences (Settings → Printer) when that UI is added, or use share-sheet with a printer app until then.
