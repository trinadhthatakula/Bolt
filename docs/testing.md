# Validation and device acceptance

## Automated checks

Use JDK 21 and the Android SDK. The Gradle wrapper is executable. Run:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --console=plain
python3 scripts/sync-busybox.py
scripts/test-emulator.sh emulator-5584
```

`test-emulator.sh` requires `JAVA_HOME` and `ANDROID_HOME`, rejects physical-device
serials, installs only on the named emulator, and checks the instrumentation success
message because `adb am instrument` can exit zero for failed assertions.

Host coverage (33 tests including the existing example): real process nonzero exits,
single-process environment, shell quoting, stderr flooding, timeout and inherited
pipe handling; bounded copies/hashes; AnyKernel structure, traversal, duplicate and
symlink rejection; original installer preservation; independent original slot images;
all-source integrity validation; wrong device/unknown slot/missing partner/remapped
target rejection; first/second write failures and backup/manifest-commit failure before
installer launch; same/changed boot identity, missing or wrong-device evidence,
interrupted installation and independent health feedback; current-product marketing
name priority, brand/model fallbacks and unavailable identity fields; `ui_print` message
formatting, flush markers, preserved whitespace, ordinary diagnostics, CRLF and partial
pipe reads. Partition mutations use a fake backend.

Instrumentation coverage (23 tests including the existing example): pinned runtime
hash/applets/execution, actual root UID, successful and failing harmless installer
fixtures with original arguments/environment/bytes, standalone applets without the
system PATH, native lock contention, read-only emulator partition backup, strict
manifest JSON, single-flight application ownership and activity recreation, durable
partial/Unknown outcomes and result/log restoration, malformed/write-failing journal
handling, hostile provider names/null/oversized streams, external import/recreation/
review/new selection, exported alias dispatch and consumed restore confirmation on preflight
rejection; all 18 Outfit weight/style declarations and visibly heavier bold outlines;
theme/font/AMOLED/dynamic-color persistence after recreation; one six-partition recovery
bundle and its detail flow; simulated restart observation, saved feedback and reopening
install details; streamed provider text with a readable/raw log toggle and unchanged
retained output, including older Activity logs loaded with a bounded 32 KiB read. The harmless failing installer also emits recovery messages through
its supplied output FD. Actual dark/light/AMOLED screen captures are generated in app-private
`files/ui-validation`. The provider exists only in the test APK and uses no production bypass.

The root fixtures only print, list their ZIP or sleep. The partition probe reads a
small vbmeta target into private cache. **No real block-device writes or reboot commands
are executed by these tests.** Journal mutation status is simulated without partition
writes. Restore UI rejection uses a deliberately nonexistent target. The restart UI
test uses invented boot IDs and a completed coordinator fixture: it does not launch an
installer, restart the emulator or establish real post-flash kernel health.

## Results recorded on 2026-10-16

- JDK 21: host tests, debug and instrumentation builds, lintDebug and minified unsigned
  release build passed. Lint: 0 errors, 49 warnings (dependency/toolchain currency,
  unused resources, conservative usable-space checks, device identity and icons).
- All four bundled ABI assets match the pinned official archive digests; the download
  and offline verification script both passed.
- Emulator `emulator-5584`: ARM64, API 37, 16 KiB pages, Magisk 30.7; Bolt root permission
  granted. The emulator exposes vbmeta but no boot/init_boot/recovery target. Its real
  flash preflight is intentionally blocked; full installer orchestration uses harmless
  fixtures and fake recovery targets instead.
- Manual SAF backup through Android's Documents picker exported a 1,048,576-byte
  vbmeta image and a complete manifest. Independent `sha256sum` matched the exported
  manifest. After force-stop/relaunch, the completed result and backup listing remained
  available; the recovery UI was visually checked. The full operation log was exported
  through CreateDocument and its SHA-256 matched the retained private log. No restore
  action was executed.
- The installed Espresso 3.6.1 test dependency failed against API 37's input APIs. Stable
  Espresso 3.7.0 and AndroidX JUnit 1.3.0 are now used for instrumentation; application
  runtime dependencies and the Android toolchain were not upgraded.
- Cockpit/Asgard implementation: all 29 host tests and 21 emulator tests passed. Native
  Outfit weights 100–900, upright/italic aliases, emphasized typography and painted
  bold/regular differences were verified on API 37. Dark, light and AMOLED screenshots
  were inspected. This is emulator evidence; no new Xiaomi font-weight check occurred.
- The user reported a successful physical-device BakaSU install and reboot using the
  preceding reliability implementation. This confirms that reported install, without
  substituting for broader hardware restore, font rendering or failure-mode acceptance.
- The current Cockpit debug APK was installed on physical device `1da5425f` (onyx /
  25053PC47G) and launched. The installed APK's SHA-256 matched the build, Bolt was
  resumed and the crash buffer was empty. The UI hierarchy confirmed **POCO F7** on
  Home and no raw model code. This validation ran no flash, restore or reboot action.

## Results recorded on 2026-10-17

- Readable operation logs: all 33 host tests and 22 emulator tests passed. Recovery
  messages preserve artwork spacing, remove protocol/flush markers and retain ordinary
  diagnostics. The UI updates an accumulated split message and switches between
  readable and raw output. The harmless root installer prints through its supplied
  output FD and still returns its original failing exit code.
- Debug, instrumentation, lint (0 errors, 49 warnings) and minified release builds
  passed. The new debug APK was installed on the POCO F7 and matched the built APK.
  Its existing kernel-operation log remained byte-for-byte unchanged. The physical
  log view was not checked because the phone was locked and Xiaomi rejected USB input
  injection; no physical flash, restore or reboot was performed by this validation.

- The user subsequently confirmed that operation logs are readable on the physical
  device. This is user-reported UI acceptance.
- Integrated merged Dependabot PR #24: AGP 9.5.0-alpha09 built-in Kotlin replaces the
  external Android Kotlin plugin; compileSdk is 37 for the updated AndroidX libraries
  (targetSdk 36 and minSdk 24 remain unchanged). JDK 21: Gradle help and build dry-run,
  33 host tests, debug/test APKs, lint (0 errors, 42 warnings) and the minified unsigned
  release passed. Fresh debug/test APKs passed all 22 instrumentation tests on
  emulator-5584. Android Studio Rabbit 2 Canary 4 Gradle sync completed successfully. No physical partition writes or reboot were run.

- PR review follow-ups: retained historic logs load off the main thread in both
  modes; Fira Code uses native 400/500 weights verified by actual rendered outlines;
  the emulator runner uses portable grep; recovery UI assertions await refresh.
  All 33 host and 23 emulator tests passed, and debug/test/release builds plus lint
  (0 errors, 42 warnings) passed. The owner-provided Gradle daemon criteria pin JBR 21,
  with the Foojay resolver configured for toolchain provisioning.

## Pending before declaring device support or shipping a release

- Real trusted AnyKernel packages on disposable, recoverable A/B and non-A/B devices:
  active slot, init_boot/vendor_boot/dtbo/vbmeta inventory, package-specific tool/boot
  format compatibility, recovery completeness, actual boot and post-boot health.
- Actual single and both-original-slot restore writes/readback failures with a reliable
  recovery route; emulator fake writes do not prove hardware write or boot behavior.
- Additional runtime ABIs and root managers: ARM32/x86/x86-64 execution; KernelSU and
  APatch root/service policies. All ABI bytes are verified, execution was ARM64 only.
- Power loss, OS process death during an actual native write, foreground-service timeout
  and reboot behavior on hardware. The durable interruption contract is tested through
  reopening journals; no power loss or boot-critical write was induced.
- SAF provider diversity, low/storage-exhaustion conditions, battery gates and revoked
  permissions on devices. Limits and null streams are covered with fixtures, not every
  provider implementation or storage race.
- Complete corresponding BusyBox source/build-input publication and verified upstream
  binary/source correspondence as described in [runtime.md](runtime.md).

Raw-image/LKM/OTA workflows are deferred scope and are not claimed as implemented or
validated. This PR is for review; these remaining checks must not be represented as
successful device validation.
