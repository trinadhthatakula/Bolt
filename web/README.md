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

The Cockpit shown on the site ships in `Bolt_v1.02.0` and newer. The historic
`Bolt_v1.01.0` APK predates it; the download section retains an additional notice
if that old release is returned. Screenshot captions retain their emulator and
sample-data provenance. Release notes remain the authority for included features.

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

## Crawlers and LLM documentation

- `/robots.txt` allows crawling and advertises the production sitemap.
- `/sitemap.xml` contains the canonical homepage and privacy page. The 404 page
  and alternate text documentation are excluded.
- `/llms.txt` is a concise Markdown index following the [llms.txt proposal](https://llmstxt.org/),
  with a summary, release distinction and links to detailed documentation.
- `/llms-full.txt` is a standalone expanded Markdown document covering product
  behavior, the screen tour, downloads and privacy. It is linked from the index.
- HTML pages advertise the index with `rel="describedby"`. The text files are
  static assets served as `text/plain`; they need no JavaScript or GitHub token.

`npm run build` checks the sitemap URLs, crawler policy, text files and their local
links. After deployment, check that all four root URLs return HTTP 200 with XML or
plain-text content types, rather than a fallback HTML page. LLM files provide
context; they do not replace robots.txt or guarantee indexing or crawler adoption.

Keep both LLM files consistent with the website and source docs when features,
privacy or release availability change. The latest-release page remains the
authority for downloadable features.
