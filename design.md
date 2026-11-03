# Design — CppStudio

A locked design system for this app. Every page redesign reads this file before
emitting code. Do not regenerate per page — extend or amend this file when the
system needs to grow.

Produced by a Hallmark `redesign` (multi-page flow) run. The previous palette was
the generic "deep-navy gradient + periwinkle/lavender accent" AI-app default; the
font token `jetbrains_mono` was wired to Roboto (a bug). This system replaces both
with a modern-minimal register tuned for a mobile C/C++ IDE.

## Genre
modern-minimal — the polished dev-tool / IDE register (Linear / Vercel / Zed
school). Confident sans, clean canvas, one restrained signal accent, hairline
borders, generous whitespace. Minimalism with conviction, not the absence of
choice.

## Macrostructure family
- **App pages** (editor, AI chat, terminal): **Workbench** — function carries
  the page; a tool-surface with a focused canvas (code / chat / terminal) and
  chrome (toolbar, tabs, drawer, sheets) around it. No enrichment.
- **List / settings pages** (home hub, settings, about, install, library
  manager): **Long Document** — vertical reading rhythm, section-grouped
  grouped-card lists, hero title at the top. Variation lives only in card
  archetype and section rhythm, never in theme.

## Theme
The accent is a single soft lavender, used for active state, focus, links, and
the primary CTA — never as a flood. Neutrals are not achromatic grey: paper,
surface, and rules are all tinted toward the accent's purple (hue ≈ 296°–300°),
which is what makes the lavender read as signal rather than decoration. No pure
black. No warm greys.

Because the accent is light in dark mode, text sitting **on** the accent uses a
near-black ink (`#17141F`), not white — that is what holds 4.5:1 contrast. Light
mode keeps white text on the accent.

### Dark
- `--color-paper`      `oklch(19.7% 0.014 298)`   #16141B  near-black canvas
- `--color-surface`    `oklch(23.5% 0.023 296)`   #1F1C28  card / raised (+3.8%)
- `--color-surface-2`  `oklch(26.3% 0.028 297)`   #262231  raised: top buttons, dialogs, chips
- `--color-editor`     `oklch(21.3% 0.020 298)`   #1A1721  code canvas (own plane)
- `--color-rule`       `rgb(255 255 255 / 12%)`   ≈#373243 hairline borders
- `--color-ink`        `oklch(94.2% 0.014 300)`   #EDEAF4  primary text
- `--color-ink-2`      `oklch(73.8% 0.032 300)`   #ADA6BC  card titles / secondary text
- `--color-muted`      `oklch(58.4% 0.036 299)`   #7E778E  subtitle / hint / chevron
- `--color-accent`     `oklch(70.9% 0.159 294)`   #A78BFA  lavender signal
- `--color-accent-ink` `oklch(20.0% 0.022 297)`   #17141F  text on accent
- `--color-focus`      `oklch(81.1% 0.101 294)`   #C4B5FD  focus ring (3:1+)
- `danger`  `oklch(71.6% 0.181 14)`  #FF6B81
- `success` `oklch(78.0% 0.154 162)` #3ED598
- `warning` `oklch(84.9% 0.135 77)`  #FFC15E
- `info`    `oklch(77.6% 0.113 262)` #8FB6FF

### Light
- `--color-paper`      `oklch(97.3% 0.008 301)`   #F7F5FB  lavender-tinted canvas
- `--color-surface`    `oklch(100% 0 0)`          #FFFFFF  card
- `--color-surface-2`  `oklch(95.0% 0.018 300)`   #F0ECF9  raised: pressed state, chips
- `--color-editor`     `oklch(98.7% 0.005 298)`   #FBFAFE  code canvas
- `--color-rule`       `rgb(0 0 0 / 12%)`         ≈#DCDBDF hairline
- `--color-ink`        `oklch(22.5% 0.019 294)`   #1C1A24
- `--color-ink-2`      `oklch(41.0% 0.030 298)`   #4C4759
- `--color-muted`      `oklch(57.7% 0.028 301)`   #7C7688
- `--color-accent`     `oklch(57.6% 0.192 290)`   #7C5CE0  lavender (stronger on light)
- `--color-accent-ink` `oklch(100% 0 0)`          #FFFFFF
- `--color-focus`      `oklch(57.6% 0.192 290)`   #7C5CE0
- `danger`  `oklch(59.0% 0.192 14)`  #D63A5A
- `success` `oklch(55.3% 0.117 161)` #14875C
- `warning` `oklch(61.9% 0.133 70)`  #B87503
- `info`    `oklch(55.4% 0.172 264)` #3D6BD6

The hue family never switches between modes; only lightness + chroma move (dark
mode recipe: paper lifts off black to 19.7%, ink drops off white to 94.2%, accent
loses ~0.03 chroma and gains ~13% lightness). Light mode's card plane is true
white — that is intentional: the tint lives in paper and raised, so a white card
still reads as a separate plane.

Status colors are tinted at 16% alpha for their background pills (`danger_bg`,
`success_bg`, `warning_bg`, `info_bg` in `color.kt`); never fill a large area with
a status color.

### Editor themes (TextMate pair)
The code editor does not read `app_colors` directly — it uses two paired TextMate
themes that mirror this table:

- `app/src/main/assets/textmate/themes/cppstudio.json` — dark: editor bg
  `#1A1721`, fg `#EDEAF4`, cursor/selection handle `#A78BFA`, selection
  `#3C3355`, line highlight `#221E2C`.
- `app/src/main/assets/textmate/themes/cppstudio_light.json` — light: editor bg
  `#FBFAFE`, fg `#1C1A24`, cursor `#7C5CE0`, selection `#DDD6F5`, line highlight
  `#F3F0FB`.

If the accent or the editor plane changes here, both JSON files move with it —
they are the same system seen through a second surface.

## Typography
- **UI sans (display + body):** Inter — Regular 400 / Medium 500 / SemiBold 600 /
  Bold 700. All non-code text (headlines, titles, labels, body, buttons).
- **Mono (code):** JetBrains Mono — Regular 400. Code, terminal, file paths,
  the editor symbol bar, tool-execution cards in AI.
- Display tracking: `-0.02em` on headline/title roles; body/label `0em`.
- Display weight: SemiBold (600), not Bold — a touch more refined for the genre.
- Type scale (sp): body 16/24 · bodyM 14/20 · bodyS 12/16 · headlineL 32/40 ·
  headlineM 28/36 · headlineS 24/32 · titleL 20/28 · titleM 18/24 · titleS 16/22
  · labelL 14/20 · labelM 12/16 · labelS 11/16.

> Why not Inter Tight: the app ships bundled `.ttf` (no network font fetch),
> and Inter's full family covers all four UI weights in one download. Tight is a
> display-only subfamily; using plain Inter keeps one family for display + body
> (single-family discipline).

## Spacing
4-point named scale. Values live in `color.kt`-adjacent spacing usage (the app
uses raw `dp` literals in composables — keep those; the scale is the canonical
reference, not an enforced token layer):
3 · 4 · 6 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56.

## Radius
Two-value system (verified against the implementation — the earlier "8px is the
house shape" line understated what shipped). Named by role, not by number:
- **Cards / grouped-list containers / raised surfaces:** 12dp. Every grouped-card
  list (home hub, settings, tools, library manager, AI settings, editor settings,
  install log, About groups) and every standalone `Card` uses 12dp.
- **Inputs, buttons, CTAs, icon chips, swatches:** 8dp. The CTA pair, every
  `OutlinedTextField`/`Button`, the 28dp icon-chip boxes, and color swatches.
Chat message bubbles are a distinct archetype (not cards): their larger radius is
allowed and does not participate in this system. Dialog surfaces hold their own
16dp radius (a deliberate "sheet/modal is rounder than inline" step up from cards).
Never pill (full-round) outside circular icon buttons and avatar-sized chips.

## Motion
- Minimal. Reveals stay off; pages are composed, not animated in.
- Existing nav transitions (tween 300ms fade+slide) and editor chrome tweens
  (220ms) are kept — they are functional, not decorative.
- Easings: keep the existing `tween` defaults; do NOT introduce bounce/overshoot.
- Reduced-motion: spatial collapses to opacity-only ≤ 150ms (deferred — no
  animation is content-bearing today).

## Microinteractions stance
- Silent success — no celebratory toasts for routine actions.
- Optimistic update over confirmation dialogs where data loss isn't at stake.
- Active/focus states use the accent at ≤ 5% viewport coverage.
- Pressed states: surface lightens one step (dark: surface → `#2C2739`) /
  darkens one step (light: surface → `#F0ECF9`).

## CTA voice
- **Primary CTA:** filled accent (`--color-accent`) with `--color-accent-ink`
  text, 8px radius, Medium weight, 14sp. One per surface.
- **Secondary CTA:** hairline border (`--color-rule`) on `--color-surface`,
  ink text, same radius.
- Icon buttons: 35dp circular, `--color-surface` fill on `--color-paper`, ink
  icon; pressed → `--color-surface-2`.
- Never gradient fills, never pill (full-round) — see `## Radius` for the full
  two-value system; CTA radius is 8dp, card radius is 12dp.

## Per-page allowances
- App pages (editor / AI / terminal): MUST NOT use enrichment. Function carries
  the page. Accent only on active state + focus + the run/AI toolbar buttons.
- List / settings pages: typography + grouped cards only. No hero imagery.
- Splash: typography-only wordmark; the `Cpp` prefix may carry the accent.

## What pages MUST share
- The CppStudio wordmark / logotype.
- The lavender accent and its placement (≤ 5% per viewport).
- Inter (UI) + JetBrains Mono (code) pairing.
- The CTA voice (8px radius, filled/outline pair).
- Section heading rhythm (grouped-card list idiom on settings/home; toolbar +
  tabs idiom on editor).
- Purple-tinted neutral greys — never flat achromatic grey, never warm.

## What pages MAY differ on
- Card archetype within the grouped-list family (icon-left vs centered vs
  two-line), as long as radius and border voice stay consistent.
- Editor chrome density (compact toolbar vs the floating action cluster).

## Hardcoded-color policy
All colors flow through `app_theme_provider.colors.*` (the `app_colors`
CompositionLocal). Inline `Color(0x…)` / `Color.White|Black|Gray` outside the
theme files is a slop tell and must be replaced with a token. The only
exceptions are the macOS traffic-light dots in any fake-terminal chrome — and
those should not exist (see "Re-drawn chrome forbidden" below); the install
screen's terminal mock was de-chromed in this redesign.

Theme presets (`theme_manager.preset`) remap the accent through the same token
layer: `LAVENDER` is the shipped default, and the other presets recolor the
accent while paper/surface keep their lightness relationships via
`lerp(..., accent, 0.05–0.12)`. Custom accents are normalized for readability
(`readable_on`) before use, so no preset can produce white-on-lavender.

## Re-drawn chrome forbidden
Do not hand-build fake browser/IDE chrome (traffic-light dots, fake title bars
wrapping a log view). Use the real surface + a hairline border, or let the
content stand alone. The previous install screen's macOS-window mock was
removed for this reason.

## Exports
Drop-in formats for re-using this design system in other projects. The Kotlin
source of truth is `app/src/main/kotlin/com/xiaonai/cppstudio/ui/theme/color.kt`;
these are the portable mirrors.

### tokens.css
```css
:root {
  /* dark (default) */
  --color-paper:      oklch(19.7% 0.014 298);
  --color-surface:    oklch(23.5% 0.023 296);
  --color-surface-2:  oklch(26.3% 0.028 297);
  --color-editor:     oklch(21.3% 0.020 298);
  --color-rule:       rgb(255 255 255 / 12%);
  --color-ink:        oklch(94.2% 0.014 300);
  --color-ink-2:      oklch(73.8% 0.032 300);
  --color-muted:      oklch(58.4% 0.036 299);
  --color-accent:     oklch(70.9% 0.159 294);
  --color-accent-ink: oklch(20.0% 0.022 297);
  --color-focus:      oklch(81.1% 0.101 294);
  --color-danger:     oklch(71.6% 0.181 14);
  --color-success:    oklch(78.0% 0.154 162);
  --color-warning:    oklch(84.9% 0.135 77);
  --color-info:       oklch(77.6% 0.113 262);

  --font-display: "Inter", system-ui, sans-serif;
  --font-body:    "Inter", system-ui, sans-serif;
  --font-mono:    "JetBrains Mono", ui-monospace, monospace;

  --space-3xs: 0.1875rem; --space-2xs: 0.25rem; --space-xs: 0.375rem;
  --space-sm:  0.5rem;    --space-md:  0.75rem;  --space-lg: 1rem;
  --space-xl:  1.5rem;    --space-2xl: 2.5rem;   --space-3xl: 3.5rem;

  --text-xs: 0.75rem; --text-sm: 0.875rem; --text-md: 1rem;
  --text-lg: 1.25rem; --text-xl: 1.75rem;  --text-2xl: 2rem;

  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --dur-short: 220ms;
  --radius-card: 12px; --radius-input: 8px; --radius-cta: 8px;
  --radius-dialog: 16px; --radius-pill: 9999px;
}

[data-theme="light"] {
  --color-paper:      oklch(97.3% 0.008 301);
  --color-surface:    oklch(100% 0 0);
  --color-surface-2:  oklch(95.0% 0.018 300);
  --color-editor:     oklch(98.7% 0.005 298);
  --color-rule:       rgb(0 0 0 / 12%);
  --color-ink:        oklch(22.5% 0.019 294);
  --color-ink-2:      oklch(41.0% 0.030 298);
  --color-muted:      oklch(57.7% 0.028 301);
  --color-accent:     oklch(57.6% 0.192 290);
  --color-accent-ink: oklch(100% 0 0);
  --color-focus:      oklch(57.6% 0.192 290);
  --color-danger:     oklch(59.0% 0.192 14);
  --color-success:    oklch(55.3% 0.117 161);
  --color-warning:    oklch(61.9% 0.133 70);
  --color-info:       oklch(55.4% 0.172 264);
}
```

### Tailwind v4 `@theme`
```css
@theme {
  --color-paper:   oklch(19.7% 0.014 298);
  --color-surface: oklch(23.5% 0.023 296);
  --color-ink:     oklch(94.2% 0.014 300);
  --color-accent:  oklch(70.9% 0.159 294);
  --color-focus:   oklch(81.1% 0.101 294);
  --font-display:  "Inter", sans-serif;
  --font-body:     "Inter", sans-serif;
  --font-mono:     "JetBrains Mono", monospace;
  --spacing-md:    0.75rem;
  --text-md:       1rem;
  --ease-out:      cubic-bezier(0.16, 1, 0.3, 1);
  --radius-card:   12px;
  --radius-cta:    8px;
}
```

### DTCG `tokens.json`
```json
{
  "color": {
    "paper":      { "$value": "oklch(19.7% 0.014 298)", "$type": "color" },
    "surface":    { "$value": "oklch(23.5% 0.023 296)", "$type": "color" },
    "raised":     { "$value": "oklch(26.3% 0.028 297)", "$type": "color" },
    "editor":     { "$value": "oklch(21.3% 0.020 298)", "$type": "color" },
    "rule":       { "$value": "rgb(255 255 255 / 12%)", "$type": "color" },
    "ink":        { "$value": "oklch(94.2% 0.014 300)", "$type": "color" },
    "ink2":       { "$value": "oklch(73.8% 0.032 300)", "$type": "color" },
    "muted":      { "$value": "oklch(58.4% 0.036 299)", "$type": "color" },
    "accent":     { "$value": "oklch(70.9% 0.159 294)", "$type": "color" },
    "accentInk":  { "$value": "oklch(20.0% 0.022 297)", "$type": "color" },
    "focus":      { "$value": "oklch(81.1% 0.101 294)", "$type": "color" }
  },
  "font": {
    "display": { "$value": "Inter",         "$type": "fontFamily" },
    "body":    { "$value": "Inter",         "$type": "fontFamily" },
    "mono":    { "$value": "JetBrains Mono","$type": "fontFamily" }
  },
  "space": { "md": { "$value": "0.75rem", "$type": "dimension" } }
}
```

### shadcn/ui CSS variables
```css
:root {
  --background:          oklch(19.7% 0.014 298);   /* paper */
  --foreground:          oklch(94.2% 0.014 300);   /* ink */
  --card:                oklch(23.5% 0.023 296);   /* surface */
  --card-foreground:     oklch(94.2% 0.014 300);   /* ink */
  --primary:             oklch(70.9% 0.159 294);   /* accent */
  --primary-foreground:  oklch(20.0% 0.022 297);   /* accent-ink */
  --secondary:           oklch(26.3% 0.028 297);   /* surface-2 */
  --secondary-foreground: oklch(94.2% 0.014 300);
  --muted:               oklch(26.3% 0.028 297);   /* surface-2 */
  --muted-foreground:    oklch(58.4% 0.036 299);   /* muted */
  --border:              rgb(255 255 255 / 12%);   /* rule */
  --input:               rgb(255 255 255 / 12%);   /* rule */
  --ring:                oklch(81.1% 0.101 294);   /* focus */
  --radius:              8px;
}
```
