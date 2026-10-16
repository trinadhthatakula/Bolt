# Bolt marketing handoff

The landing page, listing copy and README showcase the current Cockpit. The latest
published APK at authoring time is `Bolt_v1.01.0` (July 13, 2025), so all three
surfaces identify the UI as an upcoming release preview. No Android version change,
release publication, Cloudflare change or Vercel deployment is included.

## Screenshot provenance

Captured on 2026-10-17 from Bolt on the rooted ARM64 API 37 / 16 KiB-page emulator
`emulator-5584`, at 1080×1920 and density 420. The capture includes production code
from merged PR #23 (`932b862`) and the appearance localization/listener follow-ups
on `feat/bolt-web-marketing`. The foreground screenshot capture uses Android's
instrumentation screenshot API; the app UI is not recreated or painted in HTML.

The six-image recovery bundle contains zero-filled 1 MiB sample files with
valid sample manifests and deliberately nonexistent target paths. The log is emitted
by a harmless application coordinator fixture labeled “Sample log only; no installer
ran.” Neither implies a real kernel install, hardware restore or boot test. Device
identity and available backup partitions are from the emulator. No physical-device
data or user files are included.

| Capture | Screen |
| --- | --- |
| `01-overview.png` | Root/device information and a sample recovery bundle |
| `02-install.png` | AnyKernel ZIP selection screen |
| `03-recovery.png` | One compact six-partition sample bundle |
| `04-originals.png` | Original image choices in that sample bundle |
| `05-backup.png` | Manual backup destination and discovered emulator partitions |
| `06-appearance-light.png` | Asgardian light appearance settings |
| `07-appearance-amoled.png` | AMOLED appearance settings |
| `08-readable-log.png` | Readable sample provider messages and raw export controls |

`captures/` retains original PNGs. Fastlane receives those images unchanged. Web
images are resized to 720 px wide and encoded as WebP. The feature and social artwork
frames the actual Overview capture with the app icon and text; editable generation
code lives in `web/scripts/render-assets.mjs`. Fonts retain their OFL licenses.

## Repeat the authoring run

**This command clears Bolt's data on the named disposable emulator.** It rejects
physical-device serials and refuses an existing display-size override. It uses a
temporary display size, restores the native size on exit, and performs no installer,
restore or reboot operation. Use an emulator with working root access.

```sh
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
scripts/capture-marketing.sh emulator-5584
cd web
npm ci
npm run assets
npm run check:metadata
npm test
npm run build
```

`MarketingCaptureInstrumentedTest` only runs when `boltMarketingCapture=true` is
explicitly provided. Ordinary tests skip it. Its sample bundle is removed afterward;
the captures and clearly labeled sample operation log remain in emulator app data.

## Release and hosting handoff

- Vercel root `web`, Astro preset, output `dist`, build `npm run build`, install `npm ci`.
- The owner configures `bolt.trinadhthatakula.com` and its Cloudflare CNAME.
- The page resolves latest stable GitHub APKs dynamically and retains a no-JS release
  link. No release token, backend or rebuild is needed when a new APK is published.
- Publish a signed Cockpit APK only after the remaining runtime-source/publication
  and hardware acceptance checks in [runtime.md](../runtime.md) and
  [testing.md](../testing.md). This branch does not claim those checks passed.
- After that release, remove the “upcoming preview” notices consistently from website,
  README and listing copy. Keep screenshot fixture provenance explicit.

## PR #23 review follow-ups

Verified against merged master: historic log tail loading, Fira Code native 400/500
weights and portable grep were already fixed in `9510dbc` before the squash merge.
The upcoming PR adds the remaining two valid nitpicks: localized font-option semantics,
and explicit appearance listener cleanup for temporary stores and Koin disposal.

## Validation

- Android debug, instrumented-test and minified release builds, host tests and lint
  passed: 33 host tests, 24 emulator tests, zero lint errors. No physical-device
  installer, restore or reboot was run for this branch.
- The separate opt-in capture run passed and generated eight real app screenshots.
- Six release-parser/network tests, production-page validation, formatting, metadata
  dimensions and the fastlane `validate_metadata` lane passed.
- Browser checks covered dark/light headings, responsive reflow under browser zoom,
  gallery opening, Escape dismissal and restored focus, and live GitHub APK resolution.
  Exact phone viewport sizes and touch interactions remain manual review items.
- The hero uses Outfit weight 600, reduced from 900 after visual review. Promotional
  artwork renders the same variable-font outlines directly, avoiding OS font fallback.
