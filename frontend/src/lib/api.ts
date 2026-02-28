const rewriteLocalhostBase = (rawBase: string) => {
  const base = rawBase.trim();
  if (base.length === 0) {
    return base;
  }
  try {
    const url = new URL(base);
    if (
      typeof window !== "undefined" &&
      (url.hostname === "localhost" || url.hostname === "127.0.0.1")
    ) {
      url.hostname = window.location.hostname;
    }
    return url.toString().replace(/\/$/, "");
  } catch {
    return base;
  }
};

export const API_BASE = rewriteLocalhostBase(
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080"
);
const API_BASE_FALLBACKS = [API_BASE, rewriteLocalhostBase("http://localhost:8081")];
const GATEWAY_ONLY = (process.env.NEXT_PUBLIC_GATEWAY_ONLY ?? "false")
  .trim()
  .toLowerCase() === "true";
const MARKETPLACE_DIRECT_BASE: Record<MarketplaceCode, string> = {
  MG: rewriteLocalhostBase(process.env.NEXT_PUBLIC_MG_API_BASE ?? "http://localhost:8081"),
  YS: rewriteLocalhostBase(process.env.NEXT_PUBLIC_YS_API_BASE ?? "http://localhost:8082"),
};
const MARKETPLACE_API_MODE = (process.env.NEXT_PUBLIC_MARKETPLACE_API_MODE ?? "auto")
  .trim()
  .toLowerCase();
export type MarketplaceCode = "YS" | "MG";

const MARKETPLACE_PREFIX: Record<MarketplaceCode, string> = {
  MG: process.env.NEXT_PUBLIC_MG_API_PREFIX ?? "/mg",
  YS: process.env.NEXT_PUBLIC_YS_API_PREFIX ?? "/ys",
};

const API_LOG_ENABLED = (
  process.env.NEXT_PUBLIC_API_LOG ??
  (process.env.NODE_ENV === "production" ? "false" : "true")
)
  .trim()
  .toLowerCase() === "true";
let apiRequestSequence = 0;

const normalizePrefix = (value: string) => {
  const trimmed = value.trim();
  if (trimmed.length === 0 || trimmed === "/") {
    return "";
  }
  return `/${trimmed.replace(/^\/+|\/+$/g, "")}`;
};

const normalizePath = (value: string) => {
  const trimmed = value.trim();
  if (trimmed.startsWith("/")) {
    return trimmed;
  }
  return `/${trimmed}`;
};

const withBaseUrlForBase = (base: string, path: string, marketplace: MarketplaceCode) => {
  const prefix = normalizePrefix(MARKETPLACE_PREFIX[marketplace]);
  return `${base}${prefix}${normalizePath(path)}`;
};
const withDirectBaseUrl = (base: string, path: string) => `${base}${normalizePath(path)}`;

const isNetworkFetchError = (error: unknown) => {
  if (!(error instanceof Error)) {
    return false;
  }
  if (error.name === "AbortError") {
    return false;
  }
  const message = error.message.toLowerCase();
  return message.includes("failed to fetch") || message.includes("networkerror");
};

const buildUniqueBaseCandidates = (primaryBase: string, includeFallbacks = true) => {
  const candidates = includeFallbacks ? [primaryBase, ...API_BASE_FALLBACKS] : [primaryBase];
  return Array.from(
    new Set(
      candidates.map((value) => value.trim()).filter((value) => value.length > 0)
    )
  );
};

const buildMarketplaceUrlCandidates = (marketplace: MarketplaceCode, path: string) => {
  const gatewayCandidates: string[] = [];
  const directCandidates: string[] = [];
  const baseCandidates = buildUniqueBaseCandidates(API_BASE, !GATEWAY_ONLY);

  // Gateway style: {base}/{mg|ys}/...
  for (const base of baseCandidates) {
    gatewayCandidates.push(withBaseUrlForBase(base, path, marketplace));
  }

  // Direct service style: http://localhost:8081/... or 8082/...
  const directBase = MARKETPLACE_DIRECT_BASE[marketplace]?.trim();
  if (directBase) {
    directCandidates.push(withDirectBaseUrl(directBase, path));
  }

  // If API_BASE already points to a single service, try without prefix too.
  directCandidates.push(withDirectBaseUrl(API_BASE, path));

  const inferredMode =
    GATEWAY_ONLY
      ? "gateway"
      :
    MARKETPLACE_API_MODE === "gateway" || MARKETPLACE_API_MODE === "direct"
      ? MARKETPLACE_API_MODE
      : API_BASE.includes(":8080")
        ? "gateway"
        : "direct";
  const ordered =
    inferredMode === "gateway"
      ? [...gatewayCandidates, ...directCandidates]
      : [...directCandidates, ...gatewayCandidates];

  return Array.from(new Set(ordered));
};

const nextApiRequestId = () => {
  apiRequestSequence += 1;
  return `api-${apiRequestSequence}`;
};

const logApi = (
  level: "info" | "warn" | "error",
  message: string,
  details: Record<string, unknown>
) => {
  if (!API_LOG_ENABLED || typeof window === "undefined") {
    return;
  }
  // In Next.js dev, console.error triggers runtime error overlay.
  const safeLevel = level === "error" ? "warn" : level;
  const logger = console[safeLevel] ?? console.info;
  logger(`[api] ${message}`, details);
};

class ApiHttpError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiHttpError";
    this.status = status;
  }
}

async function requestWithUrl<T>(url: string, options?: RequestInit) {
  const requestId = nextApiRequestId();
  const method = (options?.method ?? "GET").toUpperCase();
  const startedAt = Date.now();
  const headers = new Headers(options?.headers);
  const hasBody = options?.body !== undefined && options?.body !== null;
  if (hasBody && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  logApi("info", "request:start", {
    requestId,
    method,
    url,
    hasBody,
  });

  const response = await fetch(url, {
    ...options,
    headers,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const bodyText = await response.text();
  let data: unknown = bodyText;
  if (contentType.includes("application/json") && bodyText.length > 0) {
    try {
      data = JSON.parse(bodyText);
    } catch {
      logApi("warn", "request:invalid-json-response", {
        requestId,
        method,
        url,
        status: response.status,
      });
      data = bodyText;
    }
  }
  if (!response.ok) {
    const isPriceHistoryUrl = /\/products\/[^/]+\/prices(?:\?|$)/.test(url);
    if (response.status === 404 && method === "GET" && isPriceHistoryUrl) {
      logApi("warn", "request:fallback-empty-price-history", {
        requestId,
        method,
        url,
        status: response.status,
        durationMs: Date.now() - startedAt,
      });
      return [] as T;
    }
    const errorData =
      typeof data === "string" ? undefined : (data as Record<string, unknown>);
    const message =
      typeof data === "string"
        ? data
        : (errorData?.message as string | undefined) ||
          (errorData?.error as string | undefined) ||
          (errorData?.code as string | undefined) ||
          `Islem basarisiz (${response.status})`;
    logApi("warn", "request:error", {
      requestId,
      method,
      url,
      status: response.status,
      message,
      durationMs: Date.now() - startedAt,
    });
    throw new ApiHttpError(message, response.status);
  }
  logApi("info", "request:success", {
    requestId,
    method,
    url,
    status: response.status,
    durationMs: Date.now() - startedAt,
  });
  return data as T;
}

export async function requestForMarketplace<T>(
  marketplace: MarketplaceCode,
  path: string,
  options?: RequestInit
) {
  const candidates = buildMarketplaceUrlCandidates(marketplace, path);
  let lastRetryableError: unknown = null;
  logApi("info", "marketplace:resolve-candidates", {
    marketplace,
    path,
    candidateCount: candidates.length,
    candidates,
  });

  for (const url of candidates) {
    try {
      return await requestWithUrl<T>(url, options);
    } catch (error) {
      const retryableHttpError = error instanceof ApiHttpError && error.status === 404;
      if (isNetworkFetchError(error) || retryableHttpError) {
        logApi("warn", "marketplace:retry-next-candidate", {
          marketplace,
          path,
          url,
          reason:
            error instanceof ApiHttpError
              ? `HTTP_${error.status}`
              : error instanceof Error
                ? error.message
                : "unknown",
        });
        lastRetryableError = error;
        continue;
      }
      logApi("warn", "marketplace:non-retryable-error", {
        marketplace,
        path,
        url,
        reason: error instanceof Error ? error.message : "unknown",
      });
      throw error;
    }
  }

  if (lastRetryableError instanceof Error) {
    throw lastRetryableError;
  }
  throw new Error(
    `API'ye baglanilamadi. Backend adresini kontrol edin (ornek: ${API_BASE} veya http://localhost:8081).`
  );
}

export async function requestMergedArrayFromBoth<T>(
  path: string,
  options?: RequestInit,
  keyResolver?: (item: T) => string
) {
  const [ys, mg] = await Promise.all([
    requestForMarketplace<T[]>("YS", path, options),
    requestForMarketplace<T[]>("MG", path, options),
  ]);
  const merged = [...ys, ...mg];
  if (!keyResolver) {
    return merged;
  }
  const seen = new Set<string>();
  return merged.filter((item) => {
    const key = keyResolver(item);
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

export async function request<T>(path: string, options?: RequestInit) {
  const directPrimaryUrl = withDirectBaseUrl(API_BASE, path);
  const shouldRetryWithFallback = (error: unknown) =>
    isNetworkFetchError(error) ||
    (error instanceof ApiHttpError && error.status === 404);
  if (GATEWAY_ONLY) {
    return requestWithUrl<T>(directPrimaryUrl, options);
  }
  try {
    return await requestWithUrl<T>(directPrimaryUrl, options);
  } catch (error) {
    if (!shouldRetryWithFallback(error)) {
      throw error;
    }

    const baseCandidates = buildUniqueBaseCandidates(API_BASE);
    for (const base of baseCandidates) {
      const retryUrl = withDirectBaseUrl(base, path);
      if (retryUrl === directPrimaryUrl) {
        continue;
      }
      try {
        return await requestWithUrl<T>(retryUrl, options);
      } catch (retryError) {
        if (!shouldRetryWithFallback(retryError)) {
          throw retryError;
        }
      }
    }

    throw new Error(
      `API'ye baglanilamadi. Backend adresini kontrol edin (ornek: ${API_BASE} veya http://localhost:8081).`
    );
  }
}
