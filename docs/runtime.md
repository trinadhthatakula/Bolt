# Bundled runtime provenance

Bolt executes BusyBox as a separate native program, packaged as
`libbolt_busybox.so` so Android extracts it into the executable native-library
directory. It is not loaded through JNI. Legacy native packaging and keeping its
symbols preserve the pinned binary bytes. There are ARM64, ARM32, x86 and x86-64
assets; runtime integrity and required applets are checked before privileged work.

The unmodified assets come from topjohnwu's official
[BusyBox 1.36.1.1 archive](https://github.com/topjohnwu/magisk-files/releases/download/files/busybox-1.36.1.1.zip),
also used by Magisk v30.7. The archive SHA-256 is
`b4d0551feabaf314e53c79316c980e8f66432e9fb91a69dbbf10a93564b40951`.
All four asset digests and the verified Magisk APK provenance are recorded in
[`busybox-manifest.json`](../app/src/main/assets/busybox-manifest.json).

Run `python3 scripts/sync-busybox.py` to verify packaged assets offline, or
`python3 scripts/sync-busybox.py --download` to fetch the pinned archive, verify the
archive and **all** binaries, and reproduce the assets. This reproduces the vendored
files; it does not claim to rebuild them from source.

BusyBox is GPL-2.0-only; its license is included at
[`BusyBox-GPL-2.0.txt`](../app/src/main/assets/licenses/BusyBox-GPL-2.0.txt).
Bolt's own application source remains GPL-3.0. The separate executable retains its
own license. Its Android source fork is
[topjohnwu/ndk-busybox](https://github.com/topjohnwu/ndk-busybox/tree/android-ndk-1.36.1),
with the inspected branch commit `1c0ca97aafb9698ab7770ce1f67af1a84b469cdb`.
The source contains generated configuration/headers and `Android.mk` with a static
`libselinux` dependency. The upstream Android build needs that dependency and an
NDK project around this makefile; plain standalone `ndk-build` is not a complete
build recipe.

Before distributing a release APK, publish the complete corresponding source and
build inputs for this runtime alongside the release, and verify the upstream
binary-to-source correspondence. A source rebuild and exact release toolchain
reproduction have not been performed in this change. Do not treat a source link or
a successful asset hash check as proof of source reproducibility.
