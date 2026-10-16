import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import sharp from "sharp";
const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../fastlane/metadata/android/en-US",
);
for (const [file, limit] of [
  ["title.txt", 30],
  ["short_description.txt", 80],
  ["full_description.txt", 4000],
]) {
  const text = (await fs.readFile(path.join(root, file), "utf8")).trim();
  if (!text || [...text].length > limit)
    throw new Error(file + " exceeds " + limit);
  console.log(file + ": " + [...text].length + "/" + limit);
}
for (const [file, width, height] of [
  ["icon.png", 512, 512],
  ["featureGraphic.png", 1024, 500],
]) {
  const image = await sharp(path.join(root, "images", file)).metadata();
  if (image.width !== width || image.height !== height)
    throw new Error("Incorrect dimensions: " + file);
}
const screens = (
  await fs.readdir(path.join(root, "images/phoneScreenshots"))
).filter((f) => /\.(png|jpg|jpeg)$/i.test(f));
if (screens.length !== 8) throw new Error("Expected eight current screenshots");
for (const file of screens) {
  const image = await sharp(
    path.join(root, "images/phoneScreenshots", file),
  ).metadata();
  if (image.width !== 1080 || image.height !== 1920)
    throw new Error("Incorrect screenshot dimensions: " + file);
}
console.log("Icon, feature graphic and eight phone screenshots validated.");
