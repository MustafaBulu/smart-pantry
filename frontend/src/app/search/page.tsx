"use client";

import { useMemo, useState } from "react";
import { API_BASE, request } from "@/lib/api";
import type { ProductDetailResponse, ProductResponse } from "@/lib/types";

type MarketplaceCode = "YS" | "MG" | "";

export default function SearchPage() {
  const [marketplaceCode, setMarketplaceCode] = useState<MarketplaceCode>("");
  const [categoryName, setCategoryName] = useState("");
  const [results, setResults] = useState<ProductResponse[]>([]);
  const [selected, setSelected] = useState<Record<number, boolean>>({});
  const [details, setDetails] = useState<Record<number, ProductDetailResponse>>(
    {}
  );
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    if (marketplaceCode) {
      params.set("marketplaceCode", marketplaceCode);
    }
    if (categoryName.trim()) {
      params.set("categoryName", categoryName.trim());
    }
    return params.toString();
  }, [marketplaceCode, categoryName]);

  const handleSearch = async () => {
    setBusy(true);
    setStatusMessage(null);
    try {
      const path = `/marketplaces/products${queryString ? `?${queryString}` : ""}`;
      const data = await request<ProductResponse[]>(path);
      setResults(data);
      setStatusMessage(`${data.length} urun bulundu.`);
    } catch (err) {
      setStatusMessage(`Arama hatasi: ${(err as Error).message}`);
    } finally {
      setBusy(false);
    }
  };

  const handleToggleProduct = async (productId: number) => {
    setSelected((prev) => ({ ...prev, [productId]: !prev[productId] }));
    if (!details[productId]) {
      try {
        const data = await request<ProductDetailResponse>(`/products/${productId}`);
        setDetails((prev) => ({ ...prev, [productId]: data }));
      } catch (err) {
        setStatusMessage(`Urun detayi cekilemedi: ${(err as Error).message}`);
      }
    }
  };

  return (
    <div className="flex flex-col gap-8">
      <section className="rounded-3xl border border-black/10 bg-white/80 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
            Urun Arama
          </p>
          <h2 className="display text-3xl font-semibold">
            Marketplace urunlerini hizla bulun
          </h2>
          <p className="mt-2 text-sm text-[#6b655c]">
            Marketplace veya kategori filtresi kullanarak arama yapin.
          </p>
        </div>
        <div className="mt-5 grid gap-3 md:grid-cols-[1fr_1fr_auto]">
          <select
            className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
            value={marketplaceCode}
            onChange={(event) => setMarketplaceCode(event.target.value as MarketplaceCode)}
          >
            <option value="">Marketplace (hepsi)</option>
            <option value="YS">YS - Yemeksepeti</option>
            <option value="MG">MG - Migros</option>
          </select>
          <input
            className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-[var(--ring)]"
            placeholder="Kategori adi"
            value={categoryName}
            onChange={(event) => setCategoryName(event.target.value)}
          />
          <button
            className="rounded-xl bg-[#111] px-5 py-2 text-sm font-semibold text-white transition hover:bg-black/80"
            type="button"
            onClick={handleSearch}
            disabled={busy}
          >
            Ara
          </button>
        </div>
        <div className="mt-4 text-xs text-[#6b655c]">API: {API_BASE}</div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <div className="flex items-center justify-between">
            <h3 className="display text-xl">Arama Sonuclari</h3>
            <span className="text-xs text-[#6b655c]">
              {results.length} kayit
            </span>
          </div>
          <div className="mt-4 space-y-2">
            {results.length === 0 ? (
              <p className="text-sm text-[#6b655c]">
                Sonuc goruntulemek icin arama yapin.
              </p>
            ) : (
              results.map((product) => (
                <div
                  key={product.id}
                  className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2"
                >
                  <button
                    type="button"
                    className="flex w-full items-center justify-between text-left text-sm"
                    onClick={() => handleToggleProduct(product.id)}
                  >
                    <span>{product.name}</span>
                    <div className="flex items-center gap-3 text-xs text-[#6b655c]">
                      <span>
                        {product.price !== null
                          ? `${product.price.toFixed(2)} TL`
                          : "-"}
                      </span>
                      <span>#{product.id}</span>
                    </div>
                  </button>
                  {selected[product.id] && (
                    <div className="mt-2 rounded-xl border border-black/5 bg-white px-3 py-2 text-xs text-[#6b655c]">
                      {details[product.id] ? (
                        <div className="space-y-1">
                          <p className="text-sm font-semibold text-[#111]">
                            {details[product.id].name}
                          </p>
                          <p>Marka: {details[product.id].brand ?? "-"}</p>
                          <p>
                            Birim: {details[product.id].unitValue ?? "?"}
                            {details[product.id].unit ?? ""}
                          </p>
                          <p>
                            Kategori: {details[product.id].categoryName ?? "-"}
                          </p>
                        </div>
                      ) : (
                        <p>Detay yukleniyor...</p>
                      )}
                    </div>
                  )}
                </div>
              ))
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
              Marketplace filtreleri istege bagli. Kategori adini dogru yazdiginizdan emin olun.
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
