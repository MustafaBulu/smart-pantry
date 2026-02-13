export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

export async function request<T>(path: string, options?: RequestInit) {
  const headers = new Headers(options?.headers);
  headers.set("Content-Type", "application/json");

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const bodyText = await response.text();
  const data =
    contentType.includes("application/json") && bodyText.length > 0
      ? JSON.parse(bodyText)
      : bodyText;
  if (!response.ok) {
    const message =
      typeof data === "string"
        ? data
        : data?.message || data?.error || "Islem basarisiz";
    throw new Error(message);
  }
  return data as T;
}
