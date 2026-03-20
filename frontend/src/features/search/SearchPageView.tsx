"use client";

import { useMemo, useState } from "react";
import { API_BASE, request, requestForMarketplace } from "@/lib/api";
import type {
  Category,
  MarketplaceProductCandidateResponse,
  MarketplaceProductMatchPairResponse,
} from "@/lib/types";
import Image from "next/image";

type MarketplaceCode = "YS" | "MG";

type MatchInfo = {
  target: MarketplaceProductCandidateResponse;
  score: number;
  autoLinkEligible: boolean;
  manualMatch: boolean;
};

const normalize = (value: string) => value.trim().toLocaleLowerCase("tr-TR");

const candidateKey = (candidate: MarketplaceProductCandidateResponse) =>
  `${candidate.marketplaceCode}:${candidate.externalId}`;

export default function SearchPage() {
  const [marketplaceCode, setMarketplaceCode] = useState<MarketplaceCode>("MG");
  const [categoryName, setCategoryName] = useState("");
  const [query, setQuery] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [ysCandidates, setYsCandidates] = useState<MarketplaceProductCandidateResponse[]>([]);
  const [mgCandidates, setMgCandidates] = useState<MarketplaceProductCandidateResponse[]>([]);
  const [matchInfoByKey, setMatchInfoByKey] = useState<Record<string, MatchInfo>>({});
  const [addedKeySet, setAddedKeySet] = useState<Set<string>>(new Set());
  const [activeCandidate, setActiveCandidate] = useState<MarketplaceProductCandidateResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [addingKey, setAddingKey] = useState<string | null>(null);

  const visibleCandidates = useMemo(() => {
    const source = marketplaceCode === "YS" ? ysCandidates : mgCandidates;
    const normalizedQuery = normalize(query);
    if (!normalizedQuery) {
      return source;
    }
    return source.filter((candidate) => normalize(candidate.name).includes(normalizedQuery));
  }, [marketplaceCode, ysCandidates, mgCandidates, query]);

  const handleSearch = async () => {
    const categoryRaw = categoryName.trim();
    if (!categoryRaw) {
      setStatusMessage("Lutfen kategori adi girin.");
      return;
    }
    setBusy(true);
    setStatusMessage(null);
    try {
      const [mgCategories, ysCategories] = await Promise.all([
        requestForMarketplace<Category[]>("MG", "/categories"),
        requestForMarketplace<Category[]>("YS", "/categories"),
      ]);
      const normalizedName = normalize(categoryRaw);
      const mgCategory = mgCategories.find((item) => normalize(item.name) === normalizedName);
      const ysCategory = ysCategories.find((item) => normalize(item.name) === normalizedName);
      if (!mgCategory || !ysCategory) {
        setSelectedCategory(null);
        setYsCandidates([]);
        setMgCandidates([]);
        setMatchInfoByKey({});
        setAddedKeySet(new Set());
        setStatusMessage(`Kategori bulunamadi: ${categoryRaw}`);
        return;
      }

      const [ys, mg] = await Promise.all([
        requestForMarketplace<MarketplaceProductCandidateResponse[]>(
          "YS",
          `/categories/${ysCategory.id}/marketplace-products`
        ).then((items) => items.filter((item) => item.marketplaceCode === "YS")),
        requestForMarketplace<MarketplaceProductCandidateResponse[]>(
          "MG",
          `/categories/${mgCategory.id}/marketplace-products`
        ).then((items) => items.filter((item) => item.marketplaceCode === "MG")),
      ]);

      const pairs = await request<MarketplaceProductMatchPairResponse[]>(
        "/categories/marketplace-products/match",
        {
          method: "POST",
          body: JSON.stringify({
            categoryId: mgCategory.id,
            ys,
            mg,
            minScore: 0.68,
          }),
        }
      );

      const nextMatchInfoByKey: Record<string, MatchInfo> = {};
      const upsert = (key: string, info: MatchInfo) => {
        const existing = nextMatchInfoByKey[key];
        if (!existing || info.score > existing.score) {
          nextMatchInfoByKey[key] = info;
        }
      };
      for (const pair of pairs) {
        upsert(candidateKey(pair.ys), {
          target: pair.mg,
          score: pair.score.score,
          autoLinkEligible: pair.autoLinkEligible,
          manualMatch: pair.manualMatch,
        });
        upsert(candidateKey(pair.mg), {
          target: pair.ys,
          score: pair.score.score,
          autoLinkEligible: pair.autoLinkEligible,
          manualMatch: pair.manualMatch,
        });
      }

      const [ysAdded, mgAdded] = await Promise.all([
        requestForMarketplace<{ marketplaceCode: string; externalId: string }[]>(
          "YS",
          `/categories/${ysCategory.id}/marketplace-products/added`
        ),
        requestForMarketplace<{ marketplaceCode: string; externalId: string }[]>(
          "MG",
          `/categories/${mgCategory.id}/marketplace-products/added`
        ),
      ]);
      const addedKeys = new Set<string>();
      [...ysAdded, ...mgAdded].forEach((item) => {
        addedKeys.add(`${item.marketplaceCode}:${item.externalId}`);
      });

      setSelectedCategory(mgCategory);
      setYsCandidates(ys);
      setMgCandidates(mg);
      setMatchInfoByKey(nextMatchInfoByKey);
      setAddedKeySet(addedKeys);
      setActiveCandidate(null);
      setStatusMessage(
        `${mgCategory.name} icin ${ys.length + mg.length} aday, ${pairs.length} eslesme bulundu.`
      );
    } catch (err) {
      setStatusMessage(`Arama hatasi: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const addCandidateToCategory = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const code = candidate.marketplaceCode === "YS" ? "YS" : "MG";
    await requestForMarketplace<string>(
      code,
      `/marketplaces/${code}/categories/${encodeURIComponent(category.name)}/addproduct`,
      {
        method: "POST",
        body: JSON.stringify({ productId: candidate.externalId }),
      }
    );
    setAddedKeySet((prev) => {
      const next = new Set(prev);
      next.add(candidateKey(candidate));
      return next;
    });
  };

  const handleAddCandidate = async (candidate: MarketplaceProductCandidateResponse) => {
    if (!selectedCategory) {
      setStatusMessage("Once kategori ile arama yapin.");
      return;
    }
    const sourceKey = candidateKey(candidate);
    if (addedKeySet.has(sourceKey)) {
      setStatusMessage("Bu urun zaten ekli.");
      return;
    }
    setAddingKey(sourceKey);
    try {
      await addCandidateToCategory(selectedCategory, candidate);

      const matchInfo = matchInfoByKey[sourceKey];
      const shouldAutoAddTarget =
        !!matchInfo && (matchInfo.autoLinkEligible || matchInfo.manualMatch);
      if (shouldAutoAddTarget && !addedKeySet.has(candidateKey(matchInfo.target))) {
        await addCandidateToCategory(selectedCategory, matchInfo.target);
        setStatusMessage("Urun eklendi, eslesen karsi-market urunu da otomatik eklendi.");
      } else {
        setStatusMessage("Urun eklendi.");
      }
    } catch (err) {
      setStatusMessage(`Urun eklenemedi: ${(err as Error).message}`);
    } finally {
      setAddingKey(null);
    }
  };

  return (
    <div className="flex flex-col gap-8">
      <section className="rounded-3xl border border-black/10 bg-white/80 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">Urun Arama ve Eslestirme</p>
          <h2 className="display text-3xl font-semibold">Arama ekraninda anlik eslesme</h2>
          <p className="mt-2 text-sm text-[#6b655c]">
            Kategori bazli adaylari cek, eslesmeleri gor, urunu eklerken karsi marketteki esleseni de otomatik ekle.
          </p>
        </div>
        <div className="mt-5 grid gap-3 md:grid-cols-[1fr_1fr_1fr_auto]">
          <select
            className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
            value={marketplaceCode}
            onChange={(event) => setMarketplaceCode(event.target.value as MarketplaceCode)}
          >
            <option value="YS">YS - Yemeksepeti</option>
            <option value="MG">MG - Migros</option>
          </select>
          <input
            className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
            placeholder="Kategori adi (zorunlu)"
            value={categoryName}
            onChange={(event) => setCategoryName(event.target.value)}
          />
          <input
            className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
            placeholder="Urun adi filtre (opsiyonel)"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button
            className="rounded-xl bg-[#111] px-5 py-2 text-sm font-semibold text-white transition hover:bg-black/80 disabled:opacity-60"
            type="button"
            onClick={handleSearch}
            disabled={busy}
          >
            Ara ve Eslestir
          </button>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-[#6b655c]">
          <span className="flex items-center gap-2 rounded-full border border-black/10 bg-white px-3 py-1">
            <Image
              src="/yemeksepeti-logo.png"
              alt="Yemeksepeti"
              width={16}
              height={16}
              className="h-4 w-4 rounded-sm object-contain"
            />
            Yemeksepeti
          </span>
          <span className="flex items-center gap-2 rounded-full border border-black/10 bg-white px-3 py-1">
            <Image
              src="/migros-logo.png"
              alt="Migros"
              width={16}
              height={16}
              className="h-4 w-4 rounded-sm object-contain"
            />
            Migros
          </span>
        </div>
        <div className="mt-4 text-xs text-[#6b655c]">API: {API_BASE}</div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <div className="flex items-center justify-between">
            <h3 className="display text-xl">Arama Sonuclari</h3>
            <span className="text-xs text-[#6b655c]">{visibleCandidates.length} kayit</span>
          </div>
          <div className="mt-4 space-y-2">
            {visibleCandidates.length === 0 ? (
              <p className="text-sm text-[#6b655c]">Sonuc goruntulemek icin arama yapin.</p>
            ) : (
              visibleCandidates.map((candidate) => {
                const key = candidateKey(candidate);
                const matchInfo = matchInfoByKey[key];
                const alreadyAdded = addedKeySet.has(key);
                const busyAdd = addingKey === key;
                const autoAddTarget =
                  !!matchInfo && (matchInfo.autoLinkEligible || matchInfo.manualMatch);
                return (
                  <div
                    key={key}
                    className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 text-sm"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-[#111]">{candidate.name}</p>
                        <p className="text-xs text-[#6b655c]">
                          {candidate.brandName || "Marka yok"} | {candidate.externalId}
                        </p>
                        {matchInfo ? (
                          <p className="text-xs text-emerald-700">
                            Eslesti ({(matchInfo.score * 100).toFixed(1)}%) - hedef: {matchInfo.target.name}
                          </p>
                        ) : (
                          <p className="text-xs text-[#6b655c]">Bu urun icin eslesme bulunamadi.</p>
                        )}
                        {autoAddTarget && (
                          <p className="text-xs text-sky-700">
                            Eklenirse karsi market urunu da otomatik eklenecek.
                          </p>
                        )}
                      </div>
                      <div className="flex items-center gap-3 text-xs text-[#6b655c]">
                        <span>
                          {candidate.price !== null ? `${candidate.price.toFixed(2)} TL` : "-"}
                        </span>
                        <button
                          type="button"
                          className="rounded-lg border border-black/10 bg-white px-2 py-1 text-xs font-semibold text-[#111] transition hover:bg-[#f4ede3]"
                          onClick={() => setActiveCandidate(candidate)}
                        >
                          Detay
                        </button>
                        <button
                          type="button"
                          className="rounded-lg border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700 transition hover:bg-emerald-100 disabled:opacity-60"
                          onClick={() => void handleAddCandidate(candidate)}
                          disabled={busyAdd || alreadyAdded}
                        >
                          {alreadyAdded ? "Ekli" : busyAdd ? "Ekleniyor..." : "Ekle"}
                        </button>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <h3 className="display text-xl">Durum</h3>
          <div className="mt-3 space-y-2 text-sm text-[#6b655c]">
            <div className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2">
              {statusMessage ?? "Arama bekleniyor."}
            </div>
            <div className="rounded-2xl border border-black/5 bg-white px-3 py-2 text-xs">
              Eslesme listesi kategori bazli cekilir. Auto-link uygun eslesmelerde, urun eklendiginde
              karsi market urunu da eklenir.
            </div>
          </div>
        </div>
      </section>

      {activeCandidate !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.5)]">
            <div className="flex items-center justify-between">
              <h4 className="display text-xl">Urun Detayi</h4>
              <button
                type="button"
                className="rounded-lg border border-black/10 px-2 py-1 text-xs text-[#6b655c] hover:bg-[#f4ede3]"
                onClick={() => setActiveCandidate(null)}
              >
                Kapat
              </button>
            </div>
            <div className="mt-4 rounded-2xl border border-black/5 bg-[#f9f4ee] px-4 py-3 text-sm text-[#6b655c]">
              <div className="space-y-2">
                <p className="text-base font-semibold text-[#111]">{activeCandidate.name}</p>
                <p>Marketplace: {activeCandidate.marketplaceCode}</p>
                <p>External ID: {activeCandidate.externalId}</p>
                <p>Marka: {activeCandidate.brandName || "-"}</p>
                <p>
                  Birim: {activeCandidate.unitValue ?? "?"}
                  {activeCandidate.unit ?? ""}
                  {activeCandidate.packCount ? ` x${activeCandidate.packCount}` : ""}
                </p>
                <p>
                  Fiyat: {activeCandidate.price !== null ? `${activeCandidate.price.toFixed(2)} TL` : "-"}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
