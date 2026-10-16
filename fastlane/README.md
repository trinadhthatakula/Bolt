# Bolt release resources

Bolt is distributed through GitHub releases. These are local authoring and build
lanes; they do not upload to Google Play, sign an APK, publish a release or deploy
the website. `README.md` is maintained manually (`skip_docs` in the Fastfile).

Run from the repository root with fastlane installed and JDK 21 configured:

```sh
fastlane android test
fastlane android build_release
cd web
npm ci
cd ..
fastlane android validate_metadata
```

`test` runs Android host tests and lint. `build_release` assembles the minified
release APK. Signing and GitHub publication remain maintainer steps.

`metadata/android/en-US/` contains the title, short and full descriptions, privacy
URL, 512×512 icon, 1024×500 feature graphic and eight 1080×1920 phone screenshots.
The capture provenance and regeneration commands are documented in
[the marketing handoff](../docs/marketing/README.md).

The listing and [upcoming release notes](RELEASE_NOTES.md) describe the current
Cockpit source. The published 1.01.0 APK predates it; do not assign these notes to
that historic release. No Android version was changed for this work.
