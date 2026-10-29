---
name: Persian Financial Minimal
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#3d4947'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#6d7a77'
  outline-variant: '#bcc9c6'
  surface-tint: '#006a61'
  primary: '#00685f'
  on-primary: '#ffffff'
  primary-container: '#008378'
  on-primary-container: '#f4fffc'
  inverse-primary: '#6bd8cb'
  secondary: '#006a63'
  on-secondary: '#ffffff'
  secondary-container: '#99efe5'
  on-secondary-container: '#006f67'
  tertiary: '#b90538'
  on-tertiary: '#ffffff'
  tertiary-container: '#dc2c4f'
  on-tertiary-container: '#fffbff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#89f5e7'
  primary-fixed-dim: '#6bd8cb'
  on-primary-fixed: '#00201d'
  on-primary-fixed-variant: '#005049'
  secondary-fixed: '#9cf2e8'
  secondary-fixed-dim: '#80d5cb'
  on-secondary-fixed: '#00201d'
  on-secondary-fixed-variant: '#00504a'
  tertiary-fixed: '#ffdadb'
  tertiary-fixed-dim: '#ffb2b7'
  on-tertiary-fixed: '#40000d'
  on-tertiary-fixed-variant: '#92002a'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 44px
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 34px
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 30px
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 26px
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 20px
  label-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 18px
  label-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
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
  margin: 1.25rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 2.5rem
---

## Brand & Style

This design system delivers a serene, dependable, and highly focused financial experience tailored specifically for Persian (RTL) loan tracking, installment schedules, and joint-debt management. The target audience encompasses modern Iranian mobile banking users, small business owners, and families managing multi-person shared installment pools. The visual direction replaces dense, high-friction ledger forms with breathable structure, soft tonal separation, and immediate visual status clarity.

The aesthetic merges modern Scandinavian minimalism with contemporary Persian digital craftsmanship. Key attributes include:
- **Calm & Reassuring:** A balanced palette of emerald green, seafoam, and deep slate replaces harsh alerts with quiet confidence and disciplined clarity.
- **RTL-First Structure:** Natural right-to-left layout balance where primary inputs, hierarchy indicators, and actions flow seamlessly from right to left without awkward mirroring artifacts.
- **Structured Decluttering:** Card-based modularity, spacious padding, and deliberate micro-groupings that isolate complex financial equations into easily digested steps.

## Colors

The palette relies on balanced natural mineral tones and deliberate functional contrast:

- **Primary (`#0D9488` - Emerald Teal):** Signifies financial prosperity, settled debts, active selection states, and principal actions.
- **Secondary (`#0F766E` - Deep Sea Pine):** Anchors high-emphasis headers, active toggles, and solid brand accents.
- **Tertiary (`#F43F5E` - Rose Coral):** Denotes overdue installments, missing inputs, and critical warnings without visual alarmism.
- **Neutral (`#0F172A` - Slate Charcoal):** Provides deep, high-clarity contrast for numerals, Persian typography, and primary labels.
- **Surface Foundations:** Background canvases use pristine slate-tinted off-white (`#F8FAFC`), while cards sit on pure white (`#FFFFFF`) framed by hairline borders (`#E2E8F0`). Secondary module fills use an ultra-soft cool mint tint (`#F0FDFA`) to distinguish status blocks and summary calculations.

## Typography

While **Plus Jakarta Sans** serves as the geometric baseline container token for system alignment, production RTL implementations map directly to **Vazirmatn** or **IranSansX** webfonts to preserve Persian typographic harmony.

Key rules:
- **Persian Numbers & Toman Display:** Financial figures, counts (e.g., `۱۲ از ۱۲`), and dates (`۱۴۰۵/۰۸/۰۷`) must always render with native Persian digits (`font-feature-settings: "ss01", "ss02"`).
- **Line Heights:** Persian scripts require slightly taller line heights (minimum 1.5x to 1.7x) compared to Latin scripts to prevent ascenders and descenders (`گ`, `ک`, `ژ`, `ی`) from clipping against borders or input boundaries.
- **Text Alignment:** Strict right-to-left justification. Currency notations (`تومان`) sit to the left of the numeral in Persian visual flow or trailing the amount naturally.

## Layout & Spacing

The layout is built around a centralized sheet modal or responsive single-column feed optimized for touch and keyboard form navigation:

- **Mobile Viewport (up to 640px):** Single-column layout with `1.25rem` screen margins. Form groups stack vertically with `space-md` gaps. Segmented monthly schedules horizontally scroll or wrap into a compact 4-column responsive grid.
- **Tablet & Desktop Modal (641px+):** Centers in a max-width `680px` floating sheet with `margin-desktop` padding, separating dense inputs into two complementary columns when needed (e.g., installment count alongside interest date).
- **Form Group Modularity:** Every related logical group (e.g., "Bank Details & Icon", "Repayment Terms", "Jalali Repayment Calendar", "Co-borrower Shares") is contained in its own isolated sub-card separated by `space-lg`.

## Elevation & Depth

Visual hierarchy uses crisp ghost borders paired with ultra-diffused ambient depth to keep dense financial workflows clear:

- **Base Surfaces (`Level 0`):** `#F8FAFC` page background.
- **Card Surfaces (`Level 1`):** Pure white (`#FFFFFF`) with a 1px border (`#E2E8F0`) and an ambient shadow: `0 4px 20px -4px rgba(15, 23, 42, 0.04)`.
- **Nested Highlight Containers:** Light mint (`#F0FDFA`) with a subtle `#CCFBF1` border, used for monthly installment status grids and co-borrower breakdowns.
- **Floating Modals & Bottom Sheets (`Level 2`):** Elevated over a `rgba(15, 23, 42, 0.35)` backdrop blur with `0 20px 40px -12px rgba(15, 23, 42, 0.12)`.
- **Interactive State Elevation:** Active pills and toggle segments step forward with an inner ring (`#0D9488`) rather than aggressive drop shadows.

## Shapes

The interface adopts a refined, medium-rounded geometry (`0.5rem` / `8px` base):

- **Inputs and Form Controls:** `0.75rem` (12px) border radius, creating a comfortable touch area without appearing cartoonish.
- **Cards and Sheet Containers:** `1rem` (16px) to `1.5rem` (24px) corner radius.
- **Selection Chips & Badges:** Fully rounded pills (`9999px`) or `0.625rem` soft rectangles for calendar tiles.
- **Circular Status Elements:** Monthly cycle indicators and user/bank category icons employ smooth circular (`50%`) or `10px` squircle envelopes.

## Components

### Input Fields & Selectors
- **Default State:** Clean white surface, 1px border `#E2E8F0`, placeholder `#94A3B8`, with the Persian label floating or positioned directly above right-aligned.
- **Focus State:** 1.5px border `#0D9488` with a 3px soft outer ring (`rgba(13, 148, 136, 0.12)`).
- **Error State:** Border `#F43F5E` with an inline error label underneath right-aligned in `body-sm`.
- **Amount & Date Fields:** Equipped with permanent RTL adornments (e.g., calendar icon docked on the right, currency title `تومان` docked on the left).

### Repayment Period & Chip Toggles
- **Duration Chips (e.g., `۶ ماهه`, `۱۲ ماهه`):** Ghost-bordered pill buttons. Unselected chips have a `#FFFFFF` fill, `#E2E8F0` border, and `#475569` text. Selected chips transition to `#0D9488` solid fill or a soft `#CCFBF1` fill with `#0F766E` text and a distinct `#0D9488` stroke.

### Jalali Monthly Installment Grid
- **Status Badges:** Small squircle cards representing each month (`آبان ۱۴۰۵`, etc.).
  - *Paid (`پرداخت شده`):* Solid `#0D9488` or soft teal background with a micro-checkmark.
  - *Current / Due (`قسط ٶ�6-8�#6,vb
N���ܙ\�Y[��M�][�[Y\�[[�H���H
�ݙ\�YH
6.v`�*8�#6)�`v*�)�+�a�
N���ٝ���H�X��ܛ�[�
ё�MM�
H�]�LLQ^��H
�[�[����[XZ[�[��
6av)�a�+�a�
N��]]Y�]H�\��X�H
ьQ�Q�X
H�]�]]�[͍��\�ܘ\K��������X�ܜ���\�
\��\��\
H�\��HH\�[��Y�Y�Y[�[\�[��HZ[��۝Z[�\�[�
ь��X
K��X]\�\�[�]�]\��Y�KH�[X\�H�]ۈ�Y��X�ܜ���\��

�6)�`v,�b6+�a�6-6,v�6�X
K[�H�[�[]Y]�H[H
6,�a�aH6�v)�,v*6,H6av*6a�)Έ6�6*�b6av)�a�
H�[�\�Y[���XY[�K\�X[Y\�[^�������]ۜ�	�X�[ۜH
���[X\�H�]ێ����M��Y�[ё�����^ZY���ܙ\�\�Y]\ΈL��]Hݙ\��[��][ۈ���͍�X��H
���X�ۙ\�H��[��[�]ێ���ё������[\��Y�L�N���MM�X^ݙ\�[���ю�Q����H
��\�X�Y�]N����[Y�]�L�N�^�MLЎ��[]�][ۈ܈�[�\�[�\�X�[ۜ�