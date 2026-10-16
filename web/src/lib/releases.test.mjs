import { test } from "node:test";
import assert from "node:assert/strict";
import { parseRelease, fetchLatestRelease } from "./releases.mjs";

const release = (assets) => ({
  tag_name: "v2",
  html_url: "https://github.com/trinadhthatakula/Bolt/releases/tag/v2",
  assets,
});
const apk = (name) => ({
  name,
  size: 5242880,
  state: "uploaded",
  browser_download_url:
    "https://github.com/trinadhthatakula/Bolt/releases/download/v2/" +
    encodeURIComponent(name),
});
test("offers every stable APK and rejects development or unrelated files", () => {
  assert.deepEqual(
    parseRelease(
      release(
        [
          "Bolt-universal.apk",
          "Bolt-arm64.apk",
          "Bolt-debug.apk",
          "Bolt-unsigned.apk",
          "Bolt-androidTest.apk",
          "source.zip",
        ].map(apk),
      ),
    ).assets.map((a) => a.name),
    ["Bolt-universal.apk", "Bolt-arm64.apk"],
  );
});
test("missing APKs stay on the release page", () =>
  assert.equal(parseRelease(release([])).assets.length, 0));
test("rejects prereleases, drafts and foreign release pages", () => {
  for (const patch of [
    { prerelease: true },
    { draft: true },
    { html_url: "https://evil.example/release" },
  ])
    assert.throws(() => parseRelease({ ...release([]), ...patch }));
});
test("rejects foreign, credentialed, mismatched and invalid-size asset URLs", () => {
  const a = apk("Bolt.apk");
  const bad = [
    "https://github.com.evil.example/trinadhthatakula/Bolt/releases/download/v2/Bolt.apk",
    "https://user@github.com/trinadhthatakula/Bolt/releases/download/v2/Bolt.apk",
    a.browser_download_url.replace("/v2/", "/v1/"),
  ].map((browser_download_url) => ({ ...a, browser_download_url }));
  assert.equal(
    parseRelease(release([...bad, { ...a, size: -1 }])).assets.length,
    0,
  );
});
test("network and rate-limit failures reach the fallback", async () => {
  await assert.rejects(
    fetchLatestRelease(async () => {
      throw new Error("offline");
    }),
  );
  await assert.rejects(
    fetchLatestRelease(async () => ({ ok: false, status: 403 })),
  );
});
test("latest release resolves without authentication", async () => {
  let options;
  const parsed = await fetchLatestRelease(async (_url, init) => {
    options = init;
    return { ok: true, json: async () => release([apk("Bolt.apk")]) };
  });
  assert.equal(parsed.tag, "v2");
  assert.equal(options.headers.Authorization, undefined);
});
