# Bolt website

Static Astro landing page for **https://bolt.trinadhthatakula.com**. The Vercel
project root is **`web`**. The owner handles Vercel configuration and Cloudflare DNS.

## Local development

Use Node 22.12+ (Node 24 LTS is suitable for hosting).

```sh
cd web
npm ci
npm test
npm run build
npm run preview -- --port 4321
```

`npm run dev` starts the development server. The production build validates the
generated pages, local links and assets, canonical URLs, gallery, structured data,
sitemap and the download fallback. It needs only `web/`, no Android SDK or secrets.

## Hosting settings

| Setting | Value |
| --- | --- |
| Root Directory | `web` |
| Framework | Astro |
| Install | `npm ci` |
| Build | `npm run build` |
| Output | `dist` |
| Domain | `bolt.trinadhthatakula.com` |
| Environment variables | None |

`vercel.json` provides these build settings and basic response headers. Configure
the custom domain on Vercel and use the exact DNS target it provides in Cloudflare.
No deployment or DNS changes are made by repository scripts.

## Downloads and release distinction

`src/lib/releases.mjs` fetches the latest stable GitHub release in the visitor's
browser when the download section approaches view. It offers all uploaded APK
assets from this repository, omitting debug, test and unsigned names. External
URLs are checked, and response strings enter the DOM as text rather than HTML.
No token or server function is required. No rebuild is needed for a new release.

The `/releases/latest` link is present in static HTML and remains usable without
JavaScript, on network errors, API rate limits, or if a release has no APK assets.
In the last case users see the release page rather than a guessed download URL.

The published `Bolt_v1.01.0` APK predates the Cockpit screenshots. The site makes
this distinction in its hero, gallery, download section and FAQ; it displays an
additional notice when that old release is returned. The showcase is explicitly
an upcoming release preview. Update preview wording after the owner publishes a
corresponding signed Android release; do not silently claim that old APK has the
new interface. This work does not bump an Android version or publish a release.

## Assets and appearance

- `src/styles/global.css`: Bolt's Asgardian dark/light palette, responsive layout,
  focus states and reduced-motion support.
- `public/fonts/`: self-hosted Outfit and Fira Code with their OFL licenses.
- `src/data/screenshots.mjs`: gallery captions and descriptive alternative text.
- `public/screenshots/`: WebP derivatives of actual emulator screenshots.
- `src/components/Downloads.astro`: dynamic APK links and static fallback.
- `/privacy/`: app and website behavior, local theme storage and GitHub requests.
- `public/social-card.png`, icons, webmanifest, robots and sitemap: sharing and
  discovery resources. Only the 404 page is marked noindex.

There are no advertising, analytics or cookie scripts. Website theme preference
is stored locally and is independent of the app's settings. The gallery uses a
keyboard-accessible dialog, and image links work without JavaScript.

To regenerate web and fastlane assets from the checked-in emulator captures:

```sh
npm run assets
npm run check:metadata
```

These two authoring commands need the full repository; they are deliberately not
part of the isolated Vercel build. See [the capture and marketing handoff](../docs/marketing/README.md).
