import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { load } from "cheerio";
const dist = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../dist",
);
const origin = "https://bolt.trinadhthatakula.com";
const pages = [
  ["index.html", "/"],
  ["privacy/index.html", "/privacy/"],
  ["404.html", "/404/"],
];
for (const [file, route] of pages) {
  const $ = load(await fs.readFile(path.join(dist, file), "utf8"));
  if (
    $("h1").length !== 1 ||
    $("main#main").length !== 1 ||
    $("html").attr("lang") !== "en"
  )
    throw new Error("Invalid page structure: " + file);
  if ($("link[rel=canonical]").attr("href") !== origin + route)
    throw new Error("Invalid canonical: " + file);
  if (file !== "404.html" && $("meta[name=robots][content*=noindex]").length)
    throw new Error("Indexable page marked noindex");
  for (const el of $("img").toArray())
    if ($(el).attr("alt") === undefined) throw new Error("Image missing alt");
  for (const el of $("[src], link[href], a[href]").toArray()) {
    const value = $(el).attr("src") || $(el).attr("href");
    if (!value || !value.startsWith("/") || value.startsWith("//")) continue;
    const parsed = new URL(value, origin);
    if (parsed.pathname.endsWith("/"))
      await fs.access(path.join(dist, parsed.pathname, "index.html"));
    else await fs.access(path.join(dist, parsed.pathname));
    if (parsed.hash && parsed.pathname === route && !$(parsed.hash).length)
      throw new Error("Broken anchor: " + value);
  }
  for (const el of $('script[type="application/ld+json"]').toArray())
    JSON.parse($(el).text());
}
const $ = load(await fs.readFile(path.join(dist, "index.html"), "utf8"));
if (
  $(".screenshot-link").length !== 8 ||
  $("#release-page").attr("href") !==
    "https://github.com/trinadhthatakula/Bolt/releases/latest"
)
  throw new Error("Missing gallery or download fallback");
const sitemap = await fs.readFile(path.join(dist, "sitemap.xml"), "utf8");
if (!sitemap.includes(origin + "/privacy/"))
  throw new Error("Incomplete sitemap");
console.log(
  "Production pages, local assets, metadata, schema and no-JS download fallback validated.",
);
