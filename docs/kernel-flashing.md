# Reliable AnyKernel flashing

Bolt supports script-based AnyKernel ZIPs with root `anykernel.sh` and the canonical
`META-INF/com/google/android/update-binary`. It executes the original installer under
its pinned BusyBox ash, with `ASH_STANDALONE=1`, `AKHOME`, recovery API `3` and output
FD `1`. Package scripts and tools are not patched or replaced. A compatible package
is still responsible for its device checks and partition choices.

## Operation and recovery contract

1. Copy a single document into a generated private workspace. Provider names are
   display metadata. Check the bounded ZIP, paths, duplicate entries, special files,
   original installer, digest, runtime, battery and available storage.
2. Show a separate device/recovery review. Receiving or sharing a ZIP never starts
   privileged work. Require root and a discovered boot, init_boot or recovery target.
3. Acquire application-wide operation ownership and start foreground execution before
   work. Backups, restores and flashes cannot run together. A native advisory lock
   also prevents another Bolt writer after app process death; recovery preparation
   checks that the previous native writer has exited.
4. Snapshot discovered active-slot and shared boot/init_boot/vendor_boot/dtbo/vbmeta/
   recovery targets, then atomically commit the complete manifest before installer
   execution. All snapshots remain in private `files/recovery/<operation-id>`.
5. Record the real installer exit, output and immutable result. Exit zero means
   installer completion, **not verified bootability**. A failure after execution
   starts is possibly partial and blocks new operations until explicitly reviewed.

Navigation and activity recreation do not cancel application-owned work. Interrupted
or malformed durable records reopen as Unknown and are never automatically replayed.
Android foreground-service limits and process termination can still interrupt the
app; the native child may continue. Acknowledgment never verifies or undoes a write.

Logs retain up to 8 MiB per operation (plus the final limit marker), a 32 KiB UI tail,
and the most recent 20 job logs. Export logs before starting enough new jobs to evict
them. The default **Readable log** view decodes recovery `ui_print` commands into the
kernel provider's messages, preserving indentation and artwork while omitting empty
flush markers. Ordinary diagnostics remain visible. **Raw log** and **Export full raw
log** retain the original output. Both existing saved logs and live output use the
readable view; formatting does not change installer execution or its exit status.

Recovery snapshots are never automatically deleted. Failed snapshots without a
complete manifest cannot be restored. Users should keep additional off-device backups;
device-bound snapshots/journals/logs are excluded from Android cloud backup and device transfer. Private snapshots are lost if Bolt's data is cleared or the app is uninstalled.

## Backup and restore

SAF exports publish a complete manifest only after every image was copied and its
exported digest re-read. Restore checks the installation/device/build identity,
canonical live block target, exact capacity, slot, image size and SHA-256 before
**any** write. Each write is fsynced and its whole-target digest verified afterward.
Per-target outcomes retain verified, failed/possibly partial and unattempted targets.

Both Slots requires a known A/B slot and both original slot images in the same
manifest. An active image is never cloned onto the other slot. Shared non-A/B targets
need no slot suffix. Legacy `.img` folders without this manifest are inspectable but
unverified and cannot be restored; create a new backup with this version.

The manifest is an integrity/device check, not a cryptographic signature from a
trusted party. An arbitrary kernel installer can modify targets beyond Bolt's
snapshot inventory. External root tools do not participate in Bolt's lock.

## Audit implementation coverage

| Findings | Change |
| --- | --- |
| B01, B02, B06 | Checked exits, one environment/process, streamed merged stdout/stderr, bounded post-exit drain |
| B03, B09, B20 | Generated paths, bounded copying and hashing, structural ZIP validation, job cleanup/storage checks |
| B04, B05, B15, B16 | Device/target/slot/size/digest manifests, all-source validation and retained partial outcomes |
| B07, B18 | Application-owned coordinator, foreground service/wake lock, native writer lock, durable Unknown state |
| B08, B12, B13, B14 | Consumed restore confirmation, retained/exportable logs, Change ZIP/New job, truthful result history |
| B10, B11 | Unmodified original installer, pinned four-ABI BusyBox, removal of the opaque mkbootfs patch |
| B17, B19 | Validated inventory with explicit unknown-slot handling; real reboot command with explicit confirmation |

Raw boot-image flashing, KernelSU LKM installation/patching, KMI acquisition and
inactive-slot OTA preparation remain separate future work. No KernelSU manager code
or binaries were copied. Build-toolchain modernization is also separate.

See [runtime provenance](runtime.md) and [validation](testing.md).
