# Bundled runtime provenance

Bolt executes BusyBox as a separate native program, packaged as
`libbolt_busybox.so` so Android extracts it into the executable native-library
directory. It is not loaded through JNI. Legacy native packaging and keeping its
symbols preserve the pinned binary bytes. ARM64, ARM32, x86 and x86-64 are packaged;
runtime integrity and required applets are checked before privileged work.

## Bolt 1.02.0 source build

The release distributes `1.36.1.1 topjohnwu (Bolt source build)`. It is built from:

- Unmodified upstream BusyBox 1.36.1 source archive, SHA-256
  `b8cc24c9574d809e7279c3be349795c5d5ceb6fdf19ca709f80cde50e47de314`.
- All 18 patches, configuration and Android makefile inputs from
  [ndk-box-kitchen](https://github.com/topjohnwu/ndk-box-kitchen/tree/14d189ea3070a8167b3576bf83fe070d4a3441af),
  commit `14d189ea3070a8167b3576bf83fe070d4a3441af`.
- Android SELinux commit `8d5c7f06d074449dbb3dad7fcb531ec02ff0c0d1` and PCRE commit
  `8a8f7153df8d9582b9ce930eb9beacef4840a19e` from Android 15's pinned source tag.
- Android NDK r30-beta1, revision `30.0.14904198-beta1`, with Clang 21.0.0.

The upstream kitchen's Android platform 35, static linking and flexible-page-size
settings are retained. Bolt omits the unused RISC-V ABI, normalizes source paths,
fixes the configuration timestamp, identifies the version banner as a Bolt build,
and disables path-dependent ELF build IDs. Shipped outputs are identified by SHA-256.
The pinned PCRE configuration header is supplied at the NDK makefile's include path.

Full input revisions, official NDK download URLs/checksums, output digests and the
modification list are in [runtime-build-provenance.json](runtime-build-provenance.json).
The APK's [busybox-manifest.json](../app/src/main/assets/busybox-manifest.json) pins
all four binaries and both release archives.

## Release assets and rebuild

[Bolt 1.02.0](https://github.com/trinadhthatakula/Bolt/releases/tag/Bolt_v1.02.0)
includes:

- `BusyBox-runtime-Bolt-1.02.0.zip`: the four exact runtime executables.
- `BusyBox-source-Bolt-1.02.0.tar.xz`: full patched BusyBox source, generated headers,
  configuration, upstream patches/scripts, complete SELinux/PCRE sources and
  licenses, toolchain provenance, build recipe and output verifier.
- `SHA256SUMS.txt`: hashes for the distributed assets.

Extract the source archive, install the pinned NDK, set `ANDROID_NDK_HOME` to its
directory and run `./build.sh`. The generated headers are already included; the
rebuild needs no source generation or dependency download. The script builds all
four executables and compares them with the expected SHA-256 values.

An independent extraction and clean rebuild on macOS reproduced every shipped ABI
byte for byte. The rebuilt ARM64 executable exposes the same 358 applets as the
previous prebuilt runtime. ARM64 operation fixtures and the application suite run
on the rooted API 37 emulator. This does not establish hardware kernel compatibility,
other ABI execution or identical rebuild output on other host operating systems.

`python3 scripts/sync-busybox.py` verifies packaged assets offline.
`python3 scripts/sync-busybox.py --download` verifies the pinned runtime ZIP and all
four executables before reproducing the vendored files. It is a binary fetch lane;
the source archive's build recipe is the source rebuild lane.

## Licensing and historical provenance

BusyBox is GPL-2.0-only; its license remains included at
[BusyBox-GPL-2.0.txt](../app/src/main/assets/licenses/BusyBox-GPL-2.0.txt).
Bolt application source remains GPL-3.0. Dependencies retain the licenses supplied
with their full source in the corresponding-source archive.

Earlier Cockpit source used topjohnwu's prebuilt BusyBox 1.36.1.1 ZIP, also shipped
in Magisk v30.7. Its verified archive/APK/binary hashes remain in
[runtime-upstream-provenance.json](runtime-upstream-provenance.json) as historical
references. The previously recorded ndk-busybox commit used an older configuration
and was not verified to correspond to that prebuilt ZIP. Bolt 1.02.0 replaces those
prebuilt bytes with the independently rebuilt, source-backed outputs above.
