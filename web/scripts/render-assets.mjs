import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";
import { openSync } from "fontkit";
import { screenshots } from "../src/data/screenshots.mjs";

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repo = path.resolve(web, "..");
const publicDir = path.join(web, "public");
const listing = path.join(repo, "fastlane/metadata/android/en-US/images");
const source = path.join(repo, "docs/marketing/captures");
const fontfile = path.join(publicDir, "fonts/outfit_variable.ttf");
const outfit = openSync(fontfile);
await fs.mkdir(listing, { recursive: true });
await fs.mkdir(path.join(listing, "phoneScreenshots"), { recursive: true });
await fs.mkdir(path.join(publicDir, "screenshots"), { recursive: true });

// Remove only the superseded listing screenshots, not unrelated image resources.
for (const file of await fs.readdir(path.join(listing, "phoneScreenshots")))
  if (/^[1-9]\.jpg$/.test(file))
    await fs.unlink(path.join(listing, "phoneScreenshots", file));
const manifest = [];
for (const screen of screenshots) {
  const input = path.join(source, screen.name + ".png");
  const metadata = await sharp(input).metadata();
  if (metadata.width !== 1080 || metadata.height !== 1920)
    throw new Error("Unexpected capture size: " + screen.name);
  await fs.copyFile(
    input,
    path.join(listing, "phoneScreenshots", screen.name + ".png"),
  );
  await sharp(input)
    .resize({ width: 720 })
    .webp({ quality: 86 })
    .toFile(path.join(publicDir, "screenshots", screen.name + ".webp"));
  manifest.push({
    name: screen.name,
    source: "captures/" + screen.name + ".png",
    width: metadata.width,
    height: metadata.height,
    fixture: true,
    alt: screen.alt,
  });
}
await fs.writeFile(
  path.join(repo, "docs/marketing/screenshots.json"),
  JSON.stringify(
    {
      capturedFrom: "Rooted ARM64 API 37 emulator",
      sourceCommit: "See docs/marketing/README.md",
      noPartitionWrites: true,
      screenshots: manifest,
    },
    null,
    2,
  ) + "\n",
);
const icon = path.join(repo, "app/src/main/launcher-playstore.png");
await sharp(icon).resize(512, 512).png().toFile(path.join(listing, "icon.png"));
for (const [name, size] of [
  ["favicon.png", 64],
  ["apple-touch-icon.png", 180],
  ["icon-192.png", 192],
  ["icon-512.png", 512],
])
  await sharp(icon).resize(size, size).png().toFile(path.join(publicDir, name));

async function textImage(text, size, color, weight = 600, width = 650) {
  // Render the actual variable-font outlines, avoiding OS font matching or synthesis.
  const font = outfit.getVariation({ wght: weight });
  const scale = size / font.unitsPerEm;
  const lines = text.split("\n");
  const height = Math.ceil(size * (1.26 + (lines.length - 1) * 1.1));
  let paths = "";
  for (let line = 0; line < lines.length; line++) {
    const run = font.layout(lines[line]);
    let x = 0;
    for (let i = 0; i < run.glyphs.length; i++) {
      const position = run.positions[i];
      const tx = (x + position.xOffset) * scale;
      const ty = size + line * size * 1.1 - position.yOffset * scale;
      paths +=
        '<path d="' +
        run.glyphs[i].path.toSVG() +
        '" transform="translate(' +
        tx +
        " " +
        ty +
        ") scale(" +
        scale +
        " " +
        -scale +
        ')"/>';
      x += position.xAdvance;
    }
    if (x * scale > width) throw new Error("Artwork text exceeds its frame");
  }
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="' +
    width +
    '" height="' +
    height +
    '"><g fill="' +
    color +
    '">' +
    paths +
    "</g></svg>";
  return sharp(Buffer.from(svg)).png().toBuffer();
}
async function artwork(width, height, output) {
  const scale = width / 1200;
  const background = Buffer.from(
    '<svg xmlns="http://www.w3.org/2000/svg" width="' +
      width +
      '" height="' +
      height +
      '"><rect width="100%" height="100%" fill="#0e0e0e"/><circle cx="' +
      width * 0.83 +
      '" cy="' +
      height * 0.54 +
      '" r="' +
      height * 0.48 +
      '" fill="none" stroke="#484848"/><path d="M0 ' +
      (height - 36) +
      "H" +
      width +
      '" stroke="#484848"/></svg>',
  );
  const s = (n) => Math.round(n * scale);
  const capture = await sharp(path.join(source, "01-overview.png"))
    .resize(s(230), s(409))
    .png()
    .toBuffer();
  await sharp(background)
    .composite([
      {
        input: await sharp(icon).resize(s(48), s(48)).png().toBuffer(),
        left: s(64),
        top: s(54),
      },
      {
        input: await textImage("Bolt", s(34), "#e5e5e5", 800),
        left: s(124),
        top: s(55),
      },
      {
        input: await textImage(
          "Your kernel.\nYour cockpit.",
          s(74),
          "#f0ffd7",
          600,
        ),
        left: s(64),
        top: s(164),
      },
      {
        input: await textImage(
          "AnyKernel · Recovery · Readable logs",
          s(23),
          "#ababab",
          400,
        ),
        left: s(66),
        top: s(385),
      },
      {
        input: await textImage(
          "Bolt Cockpit · bolt.trinadhthatakula.com",
          s(16),
          "#ababab",
          400,
        ),
        left: s(66),
        top: height - s(84),
      },
      {
        input: capture,
        left: width - s(325),
        top: Math.round((height - s(409)) / 2),
      },
    ])
    .png()
    .toFile(output);
}
await artwork(1200, 630, path.join(publicDir, "social-card.png"));
await artwork(1024, 500, path.join(listing, "featureGraphic.png"));
console.log(
  "Rendered web, listing and social assets from eight actual emulator captures.",
);
