---
name: Modern Persian Fintech
colors:
  surface: '#fcf8ff'
  surface-dim: '#dad6ff'
  surface-bright: '#fcf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f2ff'
  surface-container: '#efebff'
  surface-container-high: '#e9e5ff'
  surface-container-highest: '#e3dfff'
  on-surface: '#181445'
  on-surface-variant: '#4a4455'
  inverse-surface: '#2d2a5b'
  inverse-on-surface: '#f3eeff'
  outline: '#7b7487'
  outline-variant: '#ccc3d8'
  surface-tint: '#722fe5'
  primary: '#5200bb'
  on-primary: '#ffffff'
  primary-container: '#6c27df'
  on-primary-container: '#dac7ff'
  inverse-primary: '#d2bcff'
  secondary: '#006398'
  on-secondary: '#ffffff'
  secondary-container: '#5bb8fe'
  on-secondary-container: '#00476e'
  tertiary: '#004c32'
  on-tertiary: '#ffffff'
  tertiary-container: '#006645'
  on-tertiary-container: '#5ae8ac'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bcff'
  on-primary-fixed: '#24005b'
  on-primary-fixed-variant: '#5800c7'
  secondary-fixed: '#cce5ff'
  secondary-fixed-dim: '#93ccff'
  on-secondary-fixed: '#001d31'
  on-secondary-fixed-variant: '#004b73'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#fcf8ff'
  on-background: '#181445'
  surface-variant: '#e3dfff'
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 44px
  headline-xl-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 38px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 26px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 10px
    fontWeight: '500'
    lineHeight: 14px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  margin: 1rem
  margin-lg: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

The design system is crafted for contemporary Iranian personal finance, installment management, and debt-tracking products. It addresses users who navigate complex, high-velocity financial obligations across multiple banking institutions and peer networks.

The core visual ethos merges modern neo-corporate cleanliness with approachable soft-surfaced utility:
- **Tone:** Methodical, tranquil, transparent, and authoritative. It removes anxiety from personal debt tracking by presenting numbers with expansive breathing room and structured hierarchy.
- **Visual Style:** Soft Minimalist Fintech. Generous corner radii, crisp neutral canvas boundaries, and purposeful accents of electric violet and royal purple ensure the application feels modern, fluid, and distinctly premium.
- **RTL-First Architecture:** Built strictly for Right-to-Left writing mechanics (Persian/Farsi). Information cascades naturally from top-right to bottom-left, with iconography, badge indicators, and progress meters reversed to respect native reading flows while keeping monetary amounts legible.

## Colors

The palette establishes an immediate sense of financial precision with vibrant violet highlights and high-contrast ink levels.

- **Primary Violet (`#6C27DF`):** The signature brand accent. Used for primary actions, the floating action button (FAB), active tab indicators, high-emphasis borders, and completed progress segments.
- **Secondary Cyan/Blue (`#0284C7`):** Reserved for institutional bank identifiers (e.g., Blu Bank markers) and secondary balance progression paths.
- **Tertiary & Semantic Tones:**
  - **Success Emerald (`#10B981`):** User status active indicators and fully reconciled states.
  - **Warning / Alert Amber-Coral (`#F43F5E` and `#F59E0B`):** Upcoming urgent installment deadlines and outstanding receivables.
  - **Tinted Violet Container (`#F3EEFD` / `#EFEBFE`):** Soft, un-aggressive backgrounds for pill tags, active bottom bar pills, and highlighted card hulls.
- **Neutrals & Surfaces:**
  - **Background (`#F8F9FD`):** A cool, slightly blue-tinted paper white preventing eye strain.
  - **Surface Default (`#FFFFFF`):** High-clarity white card backgrounds with subtle borders.
  - **Text Primary (`#111827`):** Deep charcoal ink delivering superior contrast for numerals and Persian typography.
  - **Text Secondary (`#6B7280`):** Neutral slate for metadata, due dates, and supplementary bank labels.

## Typography

The design system uses a clean, friendly geometric grotesque font for system metrics, complemented in production by modern Persian type systems such as Dana, Vazirmatn, or Shabnam.

- **Numerals & Currency:** Amounts are rendered in heavy, bold weights (`700`), followed directly by the currency unit ("تومان") in matching regular or medium weight to ensure immediate balance scanning.
- **RTL Leading & Vertical Spacing:** Persian typography requires 1.4x to 1.6x line-height compared to Latin scripts due to deep ascenders and descenders. Text levels feature extended line heights to prevent diacritical clashes.
- **Hierarchical Anchoring:** Headings orient to the right. Secondary descriptors (e.g., installment ratios like "۴ از ۱۲ قسط پرداخت شده") use muted tones with medium weights to avoid visual contention with the main figure.

## Layout & Spacing

The layout is built upon an 8pt base grid system adapted for high-density mobile and tablet touch displays.

- **Screen Padding:** Outer margin sets a baseline of `1rem` (16px) on mobile viewports, expanding to `1.5rem` on larger tablet canvas tiers.
- **Card Flow & Composition:** Cards stack with a vertical gap of `0.75rem` (`space-md`) to `1rem` (`space-lg`), keeping related installments clustered without creating endless scrolling distance.
- **Grid Modes:** Supports dynamic layout switching:
  - **List Mode (Horizontal Cards):** 1-column full-width layout featuring horizontal metric progression and action pills.
  - **Grid Mode (2-Column):** 2-column layout with `0.75rem` gutters for compact side-by-side installment overviews.
  - **Compact Chart Mode:** Linear items featuring circular gauge rings aligned strictly along the leading or trailing card boundary.

## Elevation & Depth

Visual hierarchy is primarily driven by crisp micro-borders and soft ambient shadows rather than heavy skeuomorphic projections.

- **Surface Layer 0 (Canvas):** `#F8F9FD` background matte.
- **Surface Layer 1 (Standard Cards):** `#FFFFFF` with a subtle outline (`1px solid #E5E7EB`) and ambient shadow: `0 2px 10px rgba(30, 27, 75, 0.04)`.
- **Surface Layer 2 (Active / Selected Loan Cards):** Pure white container encased in an active primary border (`2px solid #8B5CF6`) with a soft violet glow: `0 8px 24px -4px rgba(108, 39, 223, 0.12)`.
- **Floating Controls (FAB):** The bottom-pinned action button uses high-impact elevation: `0 8px 20px rgba(108, 39, 223, 0.38)` with an active scale depression of `0.96` on touch press.
- **Bottom Navigation Dock:** Fixed elevation with frosted backdrop blur (`rgba(255, 255, 255, 0.95)` with `12px` blur) and an ultra-subtle border stroke along the top edge (`#F1F5F9`).

## Shapes

The design system embraces generous, friendly rounded forms (`roundedness: 2`), delivering a warm and approachable aesthetic.

- **Primary Cards & Containers:** Standardized at `1.25rem` (20px) to `1.5rem` (24px) border-radius, giving complex financial summaries an inviting card envelope.
- **Controls & Chips:** Segmented tabs, filter chips, and status badges use pill radii (`9999px`) or soft capsule bounds (`0.75rem` / 12px) to differentiate actionable meta-elements from container cards.
- **Action Buttons & Modals:** Standardized corner rounding at `1rem` (16px).

## Components

### Installment Cards (Cards)
- **Standard Card:** Single-surface white block with `20px` radius, housing bank/merchant branding (avatar + name) aligned right, status badges aligned left, main installment price headline, and a bottom dual-action area.
- **Highlighted / Selected Variant:** Framed with a 2px violet outline (`#8B5CF6`) and background tinted subtly with `#FAF7FF`.
- **Inner Progress Bar:** A full-width track (`height: 6px` to `8px`) with rounded cap endings. Unfilled track uses `#F3F4F6`; active filled portion uses solid primary violet (`#6C27DF`) or bank brand color. Progress runs right-to-left.

### Floating Action Button (FAB)
- Squircle shape (`24px` radius, `56x56px` footprint), filled with primary violet (`#6C27DF`), casting a diffused violet shadow.
- Houses a clean centered icon (e.g., `+`). Sits elevated above the content at the bottom right/left corner.

### Status Badges & Pills
- **Success/Paid Pill:** Light violet-lilac surface (`#F3EEFD`) with rich purple text and checkmark indicator.
- **Urgent/Upcoming Due Date:** Soft coral-red container (`#FEE2E2`) with crimson text (`#DC2626`) and miniature alert/clock iconography.
- **User Switcher Pill:** Small white capsule button with a green presence indicator dot, dark label, and chevron arrow.

### Segmented View Controls
- A three-part tab toggle (`Chart / Grid / List` modes) contained in a single pill track (`#FFFFFF` or `#F3F4F6`).
- Active segment has a light lilac highlight (`#EFEBFE`) with dark purple iconography, providing clear spatial feedback.

### Progress Gauges (Circular & Linear)
- **Linear Bar:** 8px height, rounded ends, animating RTL as installment payments accumulate.
- **Circular Gauge:** 48px diameter donut gauge placed at card margins for quick percentage digestion with the percentage figure centered.

### Bottom Navigation Bar
- A floating or docked navigation shell containing 3 primary destinations (Installments, Receivables, Settings).
- Active item displays an expansive lilac pill container (`#EFEBFE`) wrapping the icon, while the typography remains anchored underneath.