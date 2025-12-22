# MRZ Scanner Implementation Plan

## Overview
Replace PDF417 barcode scanner with MRZ (Machine Readable Zone) scanner for passport and ID card scanning. The MRZ scanner will work completely offline using Google ML Kit Text Recognition.

## Research Summary

### MRZ Format (ICAO 9303 Standard)
- **TD1** (ID Cards): 3 lines × 30 characters
- **TD2** (ID Cards): 2 lines × 36 characters  
- **TD3** (Passports): 2 lines × 44 characters

### MRZ Data Structure
Each MRZ line contains:
- **Line 1/2**: Document type, country code, name, document number, nationality, DOB, gender, expiry date
- **Line 3** (TD1 only): Additional document number, optional data

### Technology Choice
✅ **Selected: Google ML Kit Text Recognition** (Free, Offline, Already in Project)
- No additional commercial SDKs needed
- Works completely offline
- Already using ML Kit for barcode scanning
- Custom MRZ parser for data extraction

## Implementation Plan

### Phase 1: Dependencies & Setup
1. Add ML Kit Text Recognition dependency to `build.gradle.kts`
2. Keep existing CameraX setup (no changes needed)

### Phase 2: MRZ Parser Utility
1. Create `MRZParser.java` utility class in `utils/` package
2. Implement parsing for:
   - TD1 format (3-line ID cards)
   - TD2 format (2-line ID cards)
   - TD3 format (2-line passports)
3. Parse MRZ fields:
   - Document type
   - Country code
   - Surname and given names
   - Document number
   - Date of birth (YYMMDD → DD-MM-YYYY)
   - Gender
   - Expiry date (YYMMDD → YYYY-MM-DD)
   - Nationality

### Phase 3: Replace Barcode Scanner
1. In `CustomerManagementActivity.java`:
   - Replace `BarcodeScanner` with `TextRecognizer`
   - Update `analyzeImage()` method to use text recognition
   - Replace `processBarcodeData()` with `processMRZData()`
   - Extract MRZ lines from recognized text
   - Parse MRZ using `MRZParser`

### Phase 4: Data Mapping
Map MRZ fields to `CustomerEntity`:
- `fullName` ← Surname + Given names (cleaned of separators)
- `nationalIdNumber` ← Document number
- `dateOfBirth` ← DOB converted to DD-MM-YYYY format
- `expiryDate` ← Expiry date converted to YYYY-MM-DD format
- `documentType` ← Determine from MRZ document type code

### Phase 5: Error Handling
1. Handle invalid MRZ formats
2. Handle partial scans (missing lines)
3. Validate checksums (if available)
4. Show appropriate error messages

## MRZ Format Details

### TD3 (Passport) Format:
```
Line 1: P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
Line 2: L898902C36UTO7408122F1204159ZE184226B<<<<<6
```

Position breakdown:
- Line 1, pos 1: Document type (P)
- Line 1, pos 2: Document subtype
- Line 1, pos 5-44: Name (surname << given names)
- Line 2, pos 1-9: Document number + check digit
- Line 2, pos 10-12: Nationality
- Line 2, pos 13-19: DOB (YYMMDD) + check digit
- Line 2, pos 20: Gender (M/F/<)
- Line 2, pos 21-27: Expiry (YYMMDD) + check digit
- Line 2, pos 28-42: Personal number

### TD1 (ID Card) Format:
```
Line 1: I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
Line 2: D231458907UTO7408122F1204159<<<<<<<<<<<<<<<4
Line 3: <ZE184226B<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
```

## Customer Fields Mapping

| MRZ Field | CustomerEntity Field | Conversion |
|-----------|---------------------|------------|
| Surname + Given Names | `fullName` | Clean separators (<<), combine names |
| Document Number | `nationalIdNumber` | Direct mapping |
| Date of Birth (YYMMDD) | `dateOfBirth` | Convert to DD-MM-YYYY |
| Expiry Date (YYMMDD) | `expiryDate` | Convert to YYYY-MM-DD |
| Document Type Code | `documentType` | Map: P→PASSPORT, I→CNI, etc. |

**Note:** Phone number, address, and email are NOT in MRZ - these remain manual entry.

## UI Changes
**NONE** - Keep existing UI:
- Same camera preview
- Same "Scan Barcode" button (text can remain or change later)
- Same preview dialog
- Same customer form dialog

## Testing Requirements
1. Test with TD1 format (ID cards)
2. Test with TD2 format (ID cards)
3. Test with TD3 format (Passports)
4. Test offline functionality
5. Test with poor lighting conditions
6. Test error handling for invalid MRZ

## Files to Modify
1. `app/build.gradle.kts` - Add ML Kit Text Recognition dependency
2. `app/src/main/java/com/example/myapplication/utils/MRZParser.java` - NEW FILE
3. `app/src/main/java/com/example/myapplication/CustomerManagementActivity.java` - Replace barcode scanner logic

## Files NOT to Modify
- ❌ No layout XML files
- ❌ No theme/styling files
- ❌ No string resources (keep existing UI text)
- ❌ No drawable resources

