"use client";

import {useMemo, useState} from "react";
import {API_BASE, requestForMarketplace} from "@/lib/api";
import type {BulkAddResponse} from "@/lib/types";
import Image from "next/image";

type MarketplaceCode = "YS" | "MG";
type Notice = { id: string; message: string; tone: "success" | "error" | "info" };
type MarketplaceState = {
    categoryName: string;
    productId: string;
    bulkIds: string;
    deleteExternalId: string;
    deleteCategory: string;
    bulkResult: BulkAddResponse | null;
    busyAction: string | null;
    notices: Notice[];
};

const MARKETPLACES: { code: MarketplaceCode; name: string; iconSrc: string }[] = [
    {code: "YS", name: "Yemeksepeti", iconSrc: "/yemeksepeti-logo.png"},
    {code: "MG", name: "Migros", iconSrc: "/migros-logo.png"},
];

const splitIds = (value: string) =>
    value
        .split(/[\n,]/)
        .map((item) => item.trim())
        .filter(Boolean);

const createInitialState = (): MarketplaceState => ({
    categoryName: "",
    productId: "",
    bulkIds: "",
    deleteExternalId: "",
    deleteCategory: "",
    bulkResult: null,
    busyAction: null,
    notices: [],
});

const getNoticeToneClass = (tone: Notice["tone"]) => {
    if (tone === "success") {
        return "border-emerald-200 bg-emerald-50 text-emerald-800";
    }
    if (tone === "error") {
        return "border-rose-200 bg-rose-50 text-rose-700";
    }
    return "border-black/10 bg-[#f9f4ee] text-[#6b655c]";
};

export default function MarketplacePage() {
    const [marketplaceCode, setMarketplaceCode] = useState<MarketplaceCode>("YS");
    const [marketplaceStates, setMarketplaceStates] = useState<
        Record<MarketplaceCode, MarketplaceState>
    >(() => ({
        YS: createInitialState(),
        MG: createInitialState(),
    }));

    const addNoticeFor = (
        code: MarketplaceCode,
        message: string,
        tone: Notice["tone"] = "info"
    ) => {
        const id = `${Date.now()}-${Math.random()}`;
        setMarketplaceStates((prev) => {
            const current = prev[code];
            return {
                ...prev,
                [code]: {
                    ...current,
                    notices: [{id, message, tone}, ...current.notices].slice(0, 5),
                },
            };
        });
    };

    const updateStateFor = (
        code: MarketplaceCode,
        patch: Partial<MarketplaceState>
    ) => {
        setMarketplaceStates((prev) => ({
            ...prev,
            [code]: {...prev[code], ...patch},
        }));
    };

    const updateState = (patch: Partial<MarketplaceState>) => {
        updateStateFor(marketplaceCode, patch);
    };

    const currentState = marketplaceStates[marketplaceCode];
    const isBusy = (action: string) => currentState.busyAction === action;

    const handleAddProduct = async () => {
        const activeCode = marketplaceCode;
        if (!currentState.categoryName.trim()) {
            addNoticeFor(activeCode, "Kategori adi gerekli.", "error");
            return;
        }
        if (!currentState.productId.trim()) {
            addNoticeFor(activeCode, "Urun ID gerekli.", "error");
            return;
        }
        updateStateFor(activeCode, {busyAction: "add"});
        try {
            const message = await requestForMarketplace<string>(
                activeCode,
                `/marketplaces/${activeCode}/categories/${encodeURIComponent(
                    currentState.categoryName
                )}/addproduct`,
                {method: "POST", body: JSON.stringify({productId: currentState.productId})}
            );
            addNoticeFor(activeCode, message || "Urun eklendi.", "success");
            updateStateFor(activeCode, {productId: ""});
        } catch (err) {
            addNoticeFor(
                activeCode,
                `Urun eklenemedi: ${(err as Error).message}`,
                "error"
            );
        } finally {
            updateStateFor(activeCode, {busyAction: null});
        }
    };

    const handleBulkAdd = async () => {
        const activeCode = marketplaceCode;
        if (!currentState.categoryName.trim()) {
            addNoticeFor(activeCode, "Kategori adi gerekli.", "error");
            return;
        }
        const ids = splitIds(currentState.bulkIds);
        if (ids.length === 0) {
            addNoticeFor(activeCode, "En az bir urun ID girmelisiniz.", "error");
            return;
        }
        updateStateFor(activeCode, {busyAction: "bulk"});
        try {
            const response = await requestForMarketplace<BulkAddResponse>(
                activeCode,
                `/marketplaces/${activeCode}/categories/${encodeURIComponent(
                    currentState.categoryName
                )}/products:bulk`,
                {method: "POST", body: JSON.stringify({productIds: ids})}
            );
            updateStateFor(activeCode, {bulkResult: response});
            addNoticeFor(activeCode, "Toplu ekleme tamamlandi.", "success");
        } catch (err) {
            addNoticeFor(
                activeCode,
                `Toplu ekleme hatasi: ${(err as Error).message}`,
                "error"
            );
        } finally {
            updateStateFor(activeCode, {busyAction: null});
        }
    };

    const handleDeleteMarketplaceProduct = async () => {
        const activeCode = marketplaceCode;
        if (!currentState.deleteExternalId.trim()) {
            addNoticeFor(activeCode, "Marketplace urun ID gerekli.", "error");
            return;
        }
        updateStateFor(activeCode, {busyAction: "delete"});
        try {
            const params = new URLSearchParams();
            if (currentState.deleteCategory.trim()) {
                params.set("categoryName", currentState.deleteCategory.trim());
            }
            const suffix = params.toString() ? `?${params.toString()}` : "";
            const message = await requestForMarketplace<string>(
                activeCode,
                `/marketplaces/${activeCode}/products/${encodeURIComponent(
                    currentState.deleteExternalId
                )}${suffix}`,
                {method: "DELETE"}
            );
            addNoticeFor(activeCode, message || "Urun kaldirildi.", "success");
            updateStateFor(activeCode, {deleteExternalId: "", deleteCategory: ""});
        } catch (err) {
            addNoticeFor(
                activeCode,
                `Silme basarisiz: ${(err as Error).message}`,
                "error"
            );
        } finally {
            updateStateFor(activeCode, {busyAction: null});
        }
    };

    const bulkSummary = useMemo(() => {
        if (!currentState.bulkResult) {
            return null;
        }
        return [
            {label: "Istenen", value: currentState.bulkResult.requested},
            {label: "Olusturulan", value: currentState.bulkResult.created},
            {label: "Guncellenen", value: currentState.bulkResult.updated},
            {label: "Basarisiz", value: currentState.bulkResult.failed},
        ];
    }, [currentState.bulkResult]);

    return (
        <div className="flex flex-col gap-8">
            <section
                className="rounded-3xl border border-black/10 bg-white/80 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                        <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
                            Marketplace Isleri
                        </p>
                        <h2 className="display text-3xl font-semibold">
                            Urunleri kategorilere ekle, toplu yukle
                        </h2>
                        <p className="mt-2 text-sm text-[#6b655c]">
                            Marketplace urunlerini hizli bir sekilde takip edin.
                        </p>
                    </div>
                    <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm">
                        <p className="text-xs uppercase tracking-[0.2em] text-amber-700">
                            API Uc
                        </p>
                        <p className="text-xs">{API_BASE}</p>
                    </div>
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-[#6b655c]">
                    {MARKETPLACES.map((market) => (
                        <button
                            key={market.code}
                            className={`rounded-full px-3 py-1 text-xs font-semibold uppercase tracking-wide transition ${
                                marketplaceCode === market.code
                                    ? "bg-[#111] text-white"
                                    : "border border-black/10 text-[#6b655c]"
                            }`}
                            type="button"
                            onClick={() => setMarketplaceCode(market.code)}
                        >
                            <span className="flex items-center gap-2">
                                <Image
                                    src={market.iconSrc}
                                    alt={market.name}
                                    width={16}
                                    height={16}
                                    className="h-4 w-4 rounded-sm object-contain"
                                />
                                <span>{market.name}</span>
                            </span>
                        </button>
                    ))}
                </div>
            </section>

            <section className="grid gap-6 lg:grid-cols-[1fr_1fr]">
                <div className="flex flex-col gap-6">
                    <div
                        className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
                        <h3 className="display text-xl">Urun Ekle</h3>
                        <p className="mt-1 text-xs text-[#6b655c]">
                            Marketplace urun ID bilgisini bir kategoriye baglayin.
                        </p>
                        <div className="mt-4 grid gap-3">
                            <input
                                className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="Kategori adi"
                                value={currentState.categoryName}
                                onChange={(event) => updateState({categoryName: event.target.value})}
                            />
                            <input
                                className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="Marketplace urun ID"
                                value={currentState.productId}
                                onChange={(event) => updateState({productId: event.target.value})}
                            />
                            <button
                                className="rounded-xl bg-[#d97706] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#b45309]"
                                type="button"
                                onClick={handleAddProduct}
                                disabled={isBusy("add")}
                            >
                                Urun Ekle
                            </button>
                        </div>
                    </div>

                    <div
                        className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
                        <h3 className="display text-xl">Toplu Ekle</h3>
                        <p className="mt-1 text-xs text-[#6b655c]">
                            Birden fazla marketplace ID girdiginizde hepsi eklenir.
                        </p>
                        <div className="mt-4 grid gap-3">
                            <input
                                className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="Kategori adi"
                                value={currentState.categoryName}
                                onChange={(event) => updateState({categoryName: event.target.value})}
                            />
                            <textarea
                                className="min-h-30 rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="ID listesi (virgul veya satir ayraci)"
                                value={currentState.bulkIds}
                                onChange={(event) => updateState({bulkIds: event.target.value})}
                            />
                            <button
                                className="rounded-xl border border-[#d97706] px-4 py-2 text-sm font-semibold text-[#9a5c00] transition hover:bg-amber-50"
                                type="button"
                                onClick={handleBulkAdd}
                                disabled={isBusy("bulk")}
                            >
                                Toplu Ekle
                            </button>
                        </div>
                    </div>
                </div>

                <div className="flex flex-col gap-6">
                    <div
                        className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
                        <h3 className="display text-xl">Urun Sil</h3>
                        <p className="mt-1 text-xs text-[#6b655c]">
                            Marketplace urununu takip listesinden cikarin.
                        </p>
                        <div className="mt-4 grid gap-3">
                            <input
                                className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="Marketplace urun ID"
                                value={currentState.deleteExternalId}
                                onChange={(event) => updateState({deleteExternalId: event.target.value})}
                            />
                            <input
                                className="rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                                placeholder="Kategori adi (opsiyonel)"
                                value={currentState.deleteCategory}
                                onChange={(event) => updateState({deleteCategory: event.target.value})}
                            />
                            <button
                                className="rounded-xl border border-black/10 px-4 py-2 text-sm font-semibold text-[#6b655c] transition hover:bg-[#f4ede3]"
                                type="button"
                                onClick={handleDeleteMarketplaceProduct}
                                disabled={isBusy("delete")}
                            >
                                Sil
                            </button>
                        </div>
                    </div>

                    <div
                        className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
                        <h3 className="display text-xl">Toplu Islem Ozeti</h3>
                        <div className="mt-3 space-y-2 text-sm text-[#6b655c]">
                            {bulkSummary ? (
                                <div className="grid grid-cols-2 gap-3">
                                    {bulkSummary.map((item) => (
                                        <div
                                            key={item.label}
                                            className="rounded-2xl border border-black/5 bg-[#f9f4ee] p-3"
                                        >
                                            <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                                                {item.label}
                                            </p>
                                            <p className="display text-xl text-[#111]">
                                                {item.value}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p>Henuz toplu islem yapilmadi.</p>
                            )}
                            {currentState.bulkResult &&
                                currentState.bulkResult.results.length > 0 && (
                                    <div className="mt-3 space-y-2">
                                        {currentState.bulkResult.results
                                            .slice(0, 6)
                                            .map((item, index) => (
                                                <div
                                                    key={`${item.productId}-${index}`}
                                                    className="rounded-xl border border-black/5 bg-white px-3 py-2 text-xs"
                                                >
                      <span className="font-semibold text-[#111]">
                        {item.productId}
                      </span>{" "}
                                                    - {item.message} ({item.status})
                                                </div>
                                            ))}
                                    </div>
                                )}
                        </div>
                    </div>

                    <div
                        className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
                        <h3 className="display text-xl">Bildirimler</h3>
                        <div className="mt-3 space-y-2 text-sm">
                            {currentState.notices.length === 0 ? (
                                <p className="text-[#6b655c]">Henuz bildirim yok.</p>
                            ) : (
                                currentState.notices.map((notice) => (
                                    <div
                                        key={notice.id}
                                        className={`rounded-2xl border px-3 py-2 ${getNoticeToneClass(
                                            notice.tone
                                        )}`}
                                    >
                                        {notice.message}
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </section>
        </div>
    );
}
