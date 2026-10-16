export const RELEASES_URL =
  "https://github.com/trinadhthatakula/Bolt/releases/latest";
export const RELEASES_API =
  "https://api.github.com/repos/trinadhthatakula/Bolt/releases/latest";

function trustedUrl(value, prefix) {
  try {
    const url = new URL(value);
    if (
      url.protocol !== "https:" ||
      url.hostname !== "github.com" ||
      url.port ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    )
      return null;
    return url.pathname.startsWith(prefix) ? url.href : null;
  } catch {
    return null;
  }
}

/** GitHub is an external response: accept only this repository's stable APK assets. */
export function parseRelease(data) {
  if (
    !data ||
    data.draft ||
    data.prerelease ||
    typeof data.tag_name !== "string"
  )
    throw new Error("No stable release");
  const page = trustedUrl(
    data.html_url,
    "/trinadhthatakula/Bolt/releases/tag/",
  );
  if (!page) throw new Error("Unexpected release URL");
  const assets = (Array.isArray(data.assets) ? data.assets : []).flatMap(
    (asset) => {
      if (
        typeof asset.name !== "string" ||
        !/\.apk$/i.test(asset.name) ||
        /(^|[._-])(debug|unsigned|androidtest|test)([._-]|$)/i.test(asset.name)
      )
        return [];
      const url = trustedUrl(
        asset.browser_download_url,
        "/trinadhthatakula/Bolt/releases/download/",
      );
      if (
        !url ||
        !Number.isSafeInteger(asset.size) ||
        asset.size <= 0 ||
        (asset.state && asset.state !== "uploaded")
      )
        return [];
      const segments = new URL(url).pathname.split("/");
      try {
        if (
          decodeURIComponent(segments.at(-2)) !== data.tag_name ||
          decodeURIComponent(segments.at(-1)) !== asset.name
        )
          return [];
      } catch {
        return [];
      }
      return [{ name: asset.name, url, size: asset.size }];
    },
  );
  return {
    tag: data.tag_name,
    page,
    assets,
    legacy: data.tag_name === "Bolt_v1.01.0",
  };
}

export async function fetchLatestRelease(fetcher = fetch) {
  const response = await fetcher(RELEASES_API, {
    headers: { Accept: "application/vnd.github+json" },
    signal: AbortSignal.timeout(8000),
  });
  if (!response.ok) throw new Error("GitHub release lookup unavailable");
  return parseRelease(await response.json());
}
