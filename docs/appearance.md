# Cockpit and appearance

Bolt's Cockpit keeps Overview, Recovery and Activity available while installation and
manual backup use dedicated screens. Choosing a ZIP or backup directory does not start
an operation: installation, backup and restore each require their own review.

Overview and install review use the current product's marketing name, such as **POCO F7**,
when Android provides it. Devices without a marketing property use their brand and
model. This display name does not change the identifiers used for recovery validation.

Recovery shows one card per bundle, for example **AutoRecovery · 6 partitions**, with
partition badges, its creation time and the associated kernel ZIP when recorded. Open
the card to choose an original image and review a restore. Other partitions and manifest
details are disclosed on demand.

Installer completion, device restart and kernel health are separate observations.
Bolt records the device and boot identity before installation, then checks for a changed
boot identity when it resumes after a completed installer. It shows **Device restarted**
and asks whether the kernel is working. A detected restart does not verify every kernel
feature. Old installs without a pre-flash identity can be confirmed explicitly by the
user. Install details remain accessible from Activity after starting a new selection.

## Theme settings

The Appearance button opens persistent controls for:

- System, Light or Dark theme; System is the default.
- AMOLED backgrounds in dark mode.
- Wallpaper-derived dynamic colors on Android 12 and newer; disabled by default.
- Asgard or system fonts; Asgard is the default, with role previews.

The Asgardian palette and typography are adapted from Thor's
`app/src/main/java/com/valhalla/thor/presentation/theme/{Color,Theme,Type}.kt`.
The copied Kotlin source retains Thor's GPL-3.0-or-later attribution. Thor's checkout
is a reference source and is not needed to build Bolt.

## Fonts and weights

Outfit is copied from Thor's `app/src/main/assets/fonts/outfit_variable.ttf`; Fira Code
is copied from its `app/src/main/res/font/firacode_variable.ttf`. Their SIL Open Font
License files are included under `app/src/main/assets/licenses/`. Fira Code's license
comes from the official [6.2 release](https://github.com/tonsky/FiraCode/blob/6.2/LICENSE).

Outfit supplies headings and body text; Fira Code supplies labels, partition identifiers
and logs. All nine Outfit weights from 100 through 900 have explicit normal and italic
declarations. As in Thor, italic requests alias upright outlines because the bundled
Outfit file has no italic axis. The native loader sets both `Typeface.Builder.setWeight`
and the `wght` variation to preserve actual weight rendering on custom Android skins.
Emphasized Material typography roles inherit the selected font too.

Android 7.0/7.1 (API 24/25) uses system typography because this native variable-font
loader requires API 26. The minimum supported Android version remains unchanged.
System font mode uses the platform's ordinary family and monospace for technical text.

Material Icons Extended supplies the Cockpit icons. Release builds enable R8 and
resource shrinking; unused icon code is removed and used factory code may be inlined.
The current universal unsigned release is approximately 5.1 MiB, including the four
BusyBox ABIs and the two selected font files.
