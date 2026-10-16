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

Host coverage (26 tests including the existing example): real process nonzero exits,
single-process environment, shell quoting, stderr flooding, timeout and inherited
pipe handling; bounded copies/hashes; AnyKernel structure, traversal, duplicate and
symlink rejection; original installer preservation; independent original slot images;
all-source integrity validation; wrong device/unknown slot/missing partner/remapped
target rejection; first/second write failures and backup/manifest-commit failure before
installer launch; same/changed boot identity, missing or wrong-device evidence,
interrupted installation and independent health feedback. Partition mutations use a fake backend.

Instrumentation coverage (21 tests including the existing example): pinned runtime
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
install details. Actual dark/light/AMOLED screen captures are generated in app-private
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
- Cockpit/Asgard implementation: all 26 host tests and 21 emulator tests passed. Native
  Outfit weights 100–900, upright/italic aliases, emphasized typography and painted
  bold/regular differences were verified on API 37. Dark, light and AMOLED screenshots
  were inspected. This is emulator evidence; no new Xiaomi font-weight check occurred.
- The user reported a successful physical-device BakaSU install and reboot using the
  preceding reliability implementation. This confirms that reported install, without
  substituting for broader hardware restore, font rendering or failure-mode acceptance.

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
