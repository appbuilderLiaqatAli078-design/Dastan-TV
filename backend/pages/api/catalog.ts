import type { NextApiRequest, NextApiResponse } from "next";

const WP_BASE_URL = "https://giveme5.co/wp-json/wp/v2";
const CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes server-side cache

interface CacheEntry {
  timestamp: number;
  payload: unknown;
}

const memoryCache = new Map<string, CacheEntry>();

async function fetchWithRetry(url: string, retries = 3, delayMs = 600): Promise<Response> {
  let lastError: Error | null = null;
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const response = await fetch(url, {
        headers: { Accept: "application/json" },
      });
      if (response.ok) return response;
      lastError = new Error(`HTTP ${response.status} from ${url}`);
    } catch (err) {
      lastError = err instanceof Error ? err : new Error(String(err));
    }
    if (attempt < retries) {
      await new Promise((r) => setTimeout(r, delayMs * attempt));
    }
  }
  throw lastError ?? new Error("Upstream API request failed");
}

function extractEmbedUrls(html: string): string[] {
  const regex = /<iframe[^>]+src=["']([^"']+)["']/gi;
  const urls: string[] = [];
  let match: RegExpExecArray | null;
  while ((match = regex.exec(html)) !== null) {
    let src = (match[1] || "").trim();
    if (src.startsWith("//")) src = `https:${src}`;
    const lower = src.toLowerCase();
    const isAd =
      lower.includes("bicea.org") ||
      lower.includes("doubleclick") ||
      lower.includes("googlesyndication");
    if (src.startsWith("https://") && !isAd) {
      urls.push(src);
    }
  }
  return Array.from(new Set(urls));
}

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  if (req.method !== "GET") {
    return res.status(405).json({ error: "Method Not Allowed" });
  }

  const categoryId = req.query.categoryId ? Number(req.query.categoryId) : null;
  const page = req.query.page ? Math.max(1, Number(req.query.page)) : 1;
  const forceRefresh = req.query.refresh === "true";

  const cacheKey = `cat_${categoryId ?? "all"}_p_${page}`;
  const now = Date.now();
  const cached = memoryCache.get(cacheKey);
  if (!forceRefresh && cached && now - cached.timestamp < CACHE_TTL_MS) {
    res.setHeader("X-Cache", "HIT");
    return res.status(200).json(cached.payload);
  }

  try {
    const startMs = Date.now();
    const categoriesRes = await fetchWithRetry(`${WP_BASE_URL}/categories?per_page=100`);
    const categoriesRaw = await categoriesRes.json();

    const postsEndpoint = categoryId
      ? `${WP_BASE_URL}/posts?categories=${categoryId}&per_page=50&page=${page}&_embed=1`
      : `${WP_BASE_URL}/posts?per_page=40&page=${page}&_embed=1`;

    const postsRes = await fetchWithRetry(postsEndpoint);
    const postsRaw = await postsRes.json();

    const seenIds = new Set<number>();
    let duplicateSkippedCount = 0;
    let missingPosterCount = 0;
    let missingVideoCount = 0;

    const episodes = (Array.isArray(postsRaw) ? postsRaw : [])
      .filter((post: any) => {
        if (!post?.id || seenIds.has(post.id)) {
          duplicateSkippedCount++;
          return false;
        }
        seenIds.add(post.id);
        return true;
      })
      .map((post: any) => {
        const htmlContent = post?.content?.rendered || "";
        const embeds = extractEmbedUrls(htmlContent);
        const poster =
          post?._embedded?.["wp:featuredmedia"]?.[0]?.source_url || "";
        if (!poster) missingPosterCount++;
        if (embeds.length === 0) missingVideoCount++;

        return {
          postId: post.id,
          slug: post.slug,
          title: post?.title?.rendered || "Episode",
          publishedDate: post.date,
          categoryIds: post.categories || [],
          posterUrl: poster,
          primaryEmbedUrl: embeds[0] || "",
          backupEmbedUrls: embeds.slice(1),
          isPlayable: embeds.length > 0,
        };
      });

    const payload = {
      status: "ok",
      latencyMs: Date.now() - startMs,
      totalCategories: Array.isArray(categoriesRaw) ? categoriesRaw.length : 0,
      episodesCount: episodes.length,
      diagnostics: {
        duplicateSkippedCount,
        missingPosterCount,
        missingVideoCount,
      },
      categories: categoriesRaw,
      episodes,
    };

    memoryCache.set(cacheKey, { timestamp: now, payload });
    res.setHeader("X-Cache", "MISS");
    return res.status(200).json(payload);
  } catch (error: any) {
    return res.status(502).json({
      status: "error",
      message: error?.message || "Upstream WordPress API error",
    });
  }
}
