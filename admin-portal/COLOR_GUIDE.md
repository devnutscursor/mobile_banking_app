# 🎨 Color Palette Guide

Your custom color palette has been integrated throughout the admin portal.

## Color Palette

### 🖤 Rich Black
```
Main Background & Deep Shadows
────────────────────────────────
#000406  ████  100
#00090c  ████  200
#010d12  ████  300
#011118  ████  400
#01161e  ████  500 (DEFAULT)
#04597b  ████  600
#079cd8  ████  700
#45c6f9  ████  800
#a2e3fc  ████  900
```

### 🌊 Midnight Green
```
Primary Brand Color & Cards
────────────────────────────────
#040e12  ████  100
#071c24  ████  200
#0b2935  ████  300
#0f3747  ████  400
#124559  ████  500 (DEFAULT)
#20799c  ████  600
#36a9d6  ████  700
#79c5e4  ████  800
#bce2f1  ████  900
```

### 💙 Air Force Blue
```
Secondary Accents & Borders
────────────────────────────────
#121a1d  ████  100
#24343a  ████  200
#354e57  ████  300
#476874  ████  400
#598392  ████  500 (DEFAULT)
#769dab  ████  600
#99b6c0  ████  700
#bbced5  ████  800
#dde7ea  ████  900
```

### 🌿 Ash Gray
```
Success & Positive Actions
────────────────────────────────
#1f2a20  ████  100
#3e5441  ████  200
#5e7f61  ████  300
#83a386  ████  400
#aec3b0  ████  500 (DEFAULT)
#bdcebf  ████  600
#cedbcf  ████  700
#dee7df  ████  800
#eff3ef  ████  900
```

### 📄 Beige
```
Text & Light Accents
────────────────────────────────
#384915  ████  100
#71912a  ████  200
#a4cc4e  ████  300
#c9e197  ████  400
#eff6e0  ████  500 (DEFAULT)
#f2f8e6  ████  600
#f5f9ec  ████  700
#f8fbf2  ████  800
#fcfdf9  ████  900
```

---

## Usage Examples

### In TypeScript/JavaScript

```typescript
import { colors } from '@/lib/theme';

// Using colors
const primaryColor = colors.midnight_green[600]; // #20799c
const textColor = colors.beige[500]; // #eff6e0
const borderColor = colors.air_force_blue[300]; // #354e57
```

### In CSS/Tailwind

```css
/* Using CSS variables */
background: var(--midnight-green-600);
color: var(--beige-500);
border-color: var(--air-force-blue-300);
```

### Gradient Examples

```typescript
// Gradient 1: Midnight to Air Force Blue
background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`

// Gradient 2: Ash Gray to Beige
background: `linear-gradient(135deg, ${colors.ash_gray[500]}, ${colors.beige[400]})`

// Gradient 3: Rich Black to Midnight
background: `linear-gradient(135deg, ${colors.rich_black[500]}, ${colors.midnight_green[500]})`
```

---

## Where Each Color is Used

### Rich Black (#01161e)
- ✅ Main app background
- ✅ Deep shadows
- ✅ Input backgrounds
- ✅ Darkest elements

### Midnight Green (#124559)
- ✅ Primary buttons
- ✅ Card backgrounds
- ✅ Header backgrounds
- ✅ Active states
- ✅ Elevated surfaces

### Air Force Blue (#598392)
- ✅ Borders and dividers
- ✅ Secondary buttons
- ✅ Hover states
- ✅ Links
- ✅ Icons

### Ash Gray (#aec3b0)
- ✅ Success messages
- ✅ Positive indicators
- ✅ Active licenses
- ✅ Completed states
- ✅ Secondary text

### Beige (#eff6e0)
- ✅ Primary text
- ✅ Headings
- ✅ Light accents
- ✅ Hover overlays
- ✅ Input text

---

## Accessibility

All color combinations have been chosen to meet WCAG 2.1 Level AA standards:

✅ **High Contrast**: Beige text on Rich Black/Midnight Green backgrounds
✅ **Clear Hierarchy**: Distinct shades for different importance levels
✅ **Color Blind Friendly**: Not relying solely on color for information

---

## Design System

### Backgrounds
- **Page**: Rich Black 500
- **Cards**: Midnight Green 500
- **Elevated**: Midnight Green 400
- **Input**: Rich Black 400

### Text
- **Primary**: Beige 500
- **Secondary**: Ash Gray 500
- **Tertiary**: Air Force Blue 800
- **Disabled**: Air Force Blue 600

### Borders
- **Default**: Air Force Blue 300 (40% opacity)
- **Hover**: Air Force Blue 500
- **Focus**: Midnight Green 700

### States
- **Success**: Ash Gray 500
- **Warning**: Beige 300
- **Error**: #ef4444 (custom red)
- **Info**: Air Force Blue 600

---

## Quick Reference

```typescript
// Most common colors you'll use:

// Backgrounds
colors.rich_black[500]      // Main background
colors.midnight_green[500]  // Cards
colors.midnight_green[400]  // Elevated cards

// Text
colors.beige[500]          // Primary text
colors.ash_gray[500]       // Secondary text
colors.air_force_blue[600] // Tertiary text

// Accents
colors.midnight_green[600] // Primary actions
colors.air_force_blue[600] // Secondary actions
colors.ash_gray[500]       // Success

// Borders
colors.air_force_blue[300] // Default borders
colors.midnight_green[700] // Active borders
```

---

## Color Philosophy

Your palette creates a **professional, calming, and trustworthy** feel:

- **Rich Black**: Sophistication and depth
- **Midnight Green**: Trust and stability (perfect for banking)
- **Air Force Blue**: Reliability and technology
- **Ash Gray**: Balance and success
- **Beige**: Clarity and warmth

Perfect for a **financial/banking** application! 💰🏦






