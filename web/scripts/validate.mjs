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
  if (
    $('link[rel="describedby"][type="text/plain"]').attr("href") !== "/llms.txt"
  )
    throw new Error("Missing LLM documentation discovery: " + file);
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
const xml = load(sitemap, { xmlMode: true });
const expectedUrls = pages
  .filter(([file]) => file !== "404.html")
  .map(([, route]) => origin + route)
  .sort();
const sitemapUrls = xml("urlset > url > loc")
  .map((_, el) => xml(el).text().trim())
  .get()
  .sort();
if (
  xml("urlset").length !== 1 ||
  xml("urlset").attr("xmlns") !==
    "http://www.sitemaps.org/schemas/sitemap/0.9" ||
  JSON.stringify(sitemapUrls) !== JSON.stringify(expectedUrls)
)
  throw new Error("Invalid canonical sitemap URLs or namespace");

const robots = (await fs.readFile(path.join(dist, "robots.txt"), "utf8"))
  .split(/\r?\n/)
  .map((line) => line.replace(/#.*/, "").trim())
  .filter(Boolean);
if (
  !robots.some((line) => /^user-agent:\s*\*$/i.test(line)) ||
  !robots.some((line) => /^allow:\s*\/$/i.test(line)) ||
  robots.some((line) => /^disallow:\s*\S/i.test(line)) ||
  robots.filter((line) => /^sitemap:/i.test(line)).length !== 1 ||
  !robots.includes("Sitemap: " + origin + "/sitemap.xml")
)
  throw new Error("Invalid robots crawler policy or sitemap URL");

for (const file of ["llms.txt", "llms-full.txt"]) {
  const markdown = await fs.readFile(path.join(dist, file), "utf8");
  if (
    !markdown.startsWith("# Bolt") ||
    (markdown.match(/^# /gm) || []).length !== 1 ||
    !/^> \S/m.test(markdown) ||
    /<!doctype html|<html[\s>]/i.test(markdown) ||
    !markdown.includes(origin + "/")
  )
    throw new Error("Invalid Markdown documentation: " + file);
  if (
    file === "llms.txt" &&
    !markdown.includes("(" + origin + "/llms-full.txt)")
  )
    throw new Error("LLM index does not link to full documentation");
  for (const [, href] of markdown.matchAll(/\[[^\]]+\]\(([^\s)]+)\)/g)) {
    const url = new URL(href, origin);
    if (
      url.protocol !== "https:" ||
      url.hostname === "localhost" ||
      url.hostname === "127.0.0.1" ||
      (url.hostname.endsWith("trinadhthatakula.com") && url.origin !== origin)
    )
      throw new Error("Invalid documentation URL: " + href);
    if (url.origin !== origin) continue;
    if (url.search || url.hash)
      throw new Error("Noncanonical documentation URL: " + href);
    await fs.access(
      path.join(
        dist,
        url.pathname,
        url.pathname.endsWith("/") ? "index.html" : "",
      ),
    );
  }
}
console.log(
  "Production pages, assets, metadata, schema, download fallback, sitemap, robots and LLM documentation validated.",
);
