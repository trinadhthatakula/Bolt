import type { APIRoute } from "astro";
export const GET: APIRoute = () =>
  new Response(
    '<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://bolt.trinadhthatakula.com/</loc></url><url><loc>https://bolt.trinadhthatakula.com/privacy/</loc></url></urlset>',
    { headers: { "Content-Type": "application/xml" } },
  );
