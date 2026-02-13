"use client";

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { request } from "@/lib/api";
import type {
  Category,
  MarketplaceProductCandidateResponse,
  MarketplaceProductEntryResponse,
  PriceHistoryResponse,
  ProductDetailResponse,
  ProductResponse,
} from "@/lib/types";

type CategoryProducts = {
  YS: ProductResponse[];
  MG: ProductResponse[];
};

type MarketplaceCode = "YS" | "MG";

type Notice = { id: string; message: string; tone: "success" | "error" | "info" };

const MARKETPLACES: { code: MarketplaceCode; name: string }[] = [
  { code: "YS", name: "Yemeksepeti" },
  { code: "MG", name: "Migros" },
];

const CATEGORY_VISIBLE_COUNT = 6;
const LIST_VISIBLE_COUNT = 6;

const formatPriceSuffix = (price: number | null) => {
  if (price === null) {
    return "";
  }
  return ` - ${price.toFixed(2)} TL`;
};

const formatPriceOrDash = (price: number | null) => {
  if (price === null) {
    return "-";
  }
  return `${price.toFixed(2)} TL`;
};

const formatPriceOrLabel = (price: number | null) => {
  if (price === null) {
    return "Fiyat yok";
  }
  return `${price.toFixed(2)} TL`;
};

const getNoticeToneClass = (tone: Notice["tone"]) => {
  if (tone === "success") {
    return "border-emerald-200 bg-emerald-50 text-emerald-800";
  }
  if (tone === "error") {
    return "border-rose-200 bg-rose-50 text-rose-700";
  }
  return "border-black/10 bg-white text-[#6b655c]";
};

type SuggestedProductsProps = Readonly<{
  category: Category;
  sectionCandidates: MarketplaceProductCandidateResponse[];
  visibleSectionCandidates: MarketplaceProductCandidateResponse[];
  suggestedListKey: string;
  candidateBusy: boolean;
  addedCandidateIds: Set<string>;
  addedCandidateKeys: Record<string, boolean>;
  expandedLists: Record<string, boolean>;
  onAddCandidate: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  onToggleList: (key: string) => void;
}>;

function SuggestedProducts({
  category,
  sectionCandidates,
  visibleSectionCandidates,
  suggestedListKey,
  candidateBusy,
  addedCandidateIds,
  addedCandidateKeys,
  expandedLists,
  onAddCandidate,
  onToggleList,
}: SuggestedProductsProps) {
  let suggestedContent: ReactNode;
  if (candidateBusy) {
    suggestedContent = (
      <p className="text-xs text-[#6b655c]">
        Yukleniyor...
      </p>
    );
  } else if (sectionCandidates.length === 0) {
    suggestedContent = (
      <p className="text-xs text-[#6b655c]">
        Oneri bulunamadi.
      </p>
    );
  } else {
    suggestedContent = (
      <>
        {visibleSectionCandidates.map((item, index) => {
          const isAdded =
            addedCandidateIds.has(item.externalId) ||
            addedCandidateKeys[`${item.marketplaceCode}:${item.externalId}`];
          return (
            <div
              key={`${item.externalId}-${index}`}
              className={`flex items-center gap-3 rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 ${
                isAdded ? "opacity-40" : ""
              }`}
            >
              <div className="h-10 w-10 overflow-hidden rounded-xl border border-black/10 bg-white">
                {item.imageUrl ? (
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-[10px] text-[#6b655c]">
                    Gorsel yok
                  </div>
                )}
              </div>
              <div className="flex-1">
                <p className="text-sm font-semibold text-[#111]">
                  {item.name}
                </p>
                <p className="text-xs text-[#6b655c]">
                  {item.brandName || "Marka yok"}
                  {formatPriceSuffix(item.price)}
                </p>
              </div>
              {isAdded ? null : (
                <button
                  type="button"
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-[#d97706] text-lg font-semibold text-white transition hover:bg-[#b45309]"
                  onClick={() => onAddCandidate(category, item)}
                  disabled={candidateBusy}
                  title="Urun ekle"
                >
                  +
                </button>
              )}
            </div>
          );
        })}
        {sectionCandidates.length > LIST_VISIBLE_COUNT && (
          <button
            type="button"
            className="w-full rounded-2xl border border-black/10 bg-white px-3 py-2 text-xs uppercase tracking-[0.2em] text-[#9a5c00] transition hover:bg-amber-50"
            onClick={() => onToggleList(suggestedListKey)}
          >
            {expandedLists[suggestedListKey]
              ? "Daha Az Goster"
              : "Tumunu Goster"}
          </button>
        )}
      </>
    );
  }

  return (
    <div className="border-t border-black/5 pt-3">
      <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
        Onerilen Urunler
      </p>
      <div className="mt-2 grid gap-2">
        {suggestedContent}
      </div>
    </div>
  );
}

export default function HomePage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryName, setCategoryName] = useState("");
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [candidateBusy, setCandidateBusy] = useState(false);
  const [candidateItemsByCategory, setCandidateItemsByCategory] = useState<
    Record<number, MarketplaceProductCandidateResponse[]>
  >({});
  const [addedProductsByCategory, setAddedProductsByCategory] = useState<
    Record<number, MarketplaceProductEntryResponse[]>
  >({});
  const [expandedCategoryId, setExpandedCategoryId] = useState<number | null>(
    null
  );
  const [productsByCategory, setProductsByCategory] = useState<
    Record<number, CategoryProducts>
  >({});
  const [productDetails, setProductDetails] = useState<
    Record<number, ProductDetailResponse>
  >({});
  const [expandedProducts, setExpandedProducts] = useState<
    Record<number, boolean>
  >({});
  const [expandedLists, setExpandedLists] = useState<Record<string, boolean>>({});
  const [pendingScrollId, setPendingScrollId] = useState<number | null>(null);
  const categoryRefs = useRef<Map<number, HTMLDivElement>>(new Map());
  const [addedCandidateKeysByCategory, setAddedCandidateKeysByCategory] = useState<
    Record<number, Record<string, boolean>>
  >({});
  const [selectedAddedProduct, setSelectedAddedProduct] =
    useState<MarketplaceProductEntryResponse | null>(null);
  const [selectedProductHistory, setSelectedProductHistory] = useState<
    { recordedAt: string; price: number }[]
  >([]);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notices, setNotices] = useState<Notice[]>([]);

  const removeNoticeById = (id: string) => {
    setNotices((prev) => prev.filter((notice) => notice.id !== id));
  };

  const addNotice = (message: string, tone: Notice["tone"] = "info") => {
    const id = `${Date.now()}-${Math.random()}`;
    setNotices((prev) => [{ id, message, tone }, ...prev].slice(0, 5));
    setTimeout(() => {
      removeNoticeById(id);
    }, 2800);
  };

  const loadCategories = async () => {
    const data = await request<Category[]>("/categories");
    setCategories(data);
  };

  useEffect(() => {
    loadCategories().catch((err) =>
      addNotice(`Kategoriler yuklenemedi: ${err.message}`, "error")
    );
  }, []);

  useEffect(() => {
    if (pendingScrollId === null) {
      return;
    }
    const node = categoryRefs.current.get(pendingScrollId);
    if (node) {
      node.scrollIntoView({ behavior: "smooth", block: "start" });
    }
    setPendingScrollId(null);
  }, [pendingScrollId, categories]);

  const handleCreateCategory = async () => {
    if (!categoryName.trim()) {
      addNotice("Kategori adi gerekli.", "error");
      return;
    }
    setBusy(true);
    try {
      const created = await request<Category>("/categories", {
        method: "POST",
        body: JSON.stringify({ name: categoryName }),
      });
      setCategoryName("");
      setShowAddCategory(false);
      setShowAllCategories(true);
      await loadCategories();
      addNotice("Kategori eklendi.", "success");
      setExpandedCategoryId(created.id);
      setPendingScrollId(created.id);
      setProductsByCategory((prev) => ({
        ...prev,
        [created.id]: { YS: [], MG: [] },
      }));
      try {
        await fetchCategoryProducts(created);
      } catch (err) {
        addNotice(`Urunler yuklenemedi: ${(err as Error).message}`, "error");
      }
      await loadCandidates(created);
      await loadAddedProducts(created, "MG");
    } catch (err) {
      addNotice(`Kategori eklenemedi: ${(err as Error).message}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const toggleList = (key: string) => {
    setExpandedLists((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const setCandidateAddedState = (
    categoryId: number,
    candidate: MarketplaceProductCandidateResponse,
    isAdded: boolean
  ) => {
    const key = `${candidate.marketplaceCode}:${candidate.externalId}`;
    setAddedCandidateKeysByCategory((prev) => {
      const current = prev[categoryId] ?? {};
      if (isAdded) {
        return {
          ...prev,
          [categoryId]: { ...current, [key]: true },
        };
      }
      const rest = { ...current };
      delete rest[key];
      return {
        ...prev,
        [categoryId]: rest,
      };
    });
  };

  const fetchCategoryProducts = async (category: Category) => {
    const categoryNameParam = encodeURIComponent(category.name);
    const [ys, mg] = await Promise.all([
      request<ProductResponse[]>(
        `/marketplaces/products?marketplaceCode=YS&categoryName=${categoryNameParam}`
      ),
      request<ProductResponse[]>(
        `/marketplaces/products?marketplaceCode=MG&categoryName=${categoryNameParam}`
      ),
    ]);

    setProductsByCategory((prev) => ({
      ...prev,
      [category.id]: { YS: ys, MG: mg },
    }));
  };

  const loadCandidates = async (category: Category) => {
    setCandidateBusy(true);
    try {
      const candidates = await request<MarketplaceProductCandidateResponse[]>(
        `/categories/${category.id}/marketplace-products`
      );
      setCandidateItemsByCategory((prev) => ({
        ...prev,
        [category.id]: candidates,
      }));
    } catch (err) {
      addNotice(`Urun listesi alinamadi: ${(err as Error).message}`, "error");
    } finally {
      setCandidateBusy(false);
    }
  };

  const loadAddedProducts = async (
    category: Category,
    marketplaceCode: MarketplaceCode
  ) => {
    try {
      const products = await request<MarketplaceProductEntryResponse[]>(
        `/categories/${category.id}/marketplace-products/added?marketplaceCode=${marketplaceCode}`
      );
      setAddedProductsByCategory((prev) => ({
        ...prev,
        [category.id]: products,
      }));
    } catch (err) {
      addNotice(`Eklenen urunler alinamadi: ${(err as Error).message}`, "error");
    }
  };

  const handleToggleCategory = async (category: Category) => {
    if (expandedCategoryId === category.id) {
      setExpandedCategoryId(null);
      setSelectedAddedProduct(null);
      setSelectedProductHistory([]);
      return;
    }
    setExpandedCategoryId(category.id);
    setSelectedAddedProduct(null);
    setSelectedProductHistory([]);
    if (!productsByCategory[category.id]) {
      setBusy(true);
      try {
        await fetchCategoryProducts(category);
      } catch (err) {
        addNotice(`Urunler yuklenemedi: ${(err as Error).message}`, "error");
      } finally {
        setBusy(false);
      }
    }
    if (!candidateItemsByCategory[category.id]) {
      await loadCandidates(category);
    }
    if (!addedProductsByCategory[category.id]) {
      await loadAddedProducts(category, "MG");
    }
  };

  const refreshCategoryMarketplaceData = async (category: Category) => {
    await fetchCategoryProducts(category);
    await loadAddedProducts(category, "MG");
  };

  const mutateCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse,
    method: "POST" | "DELETE",
    successMessage: string,
    isAdded: boolean
  ) => {
    setCandidateBusy(true);
    try {
      const endpoint =
        method === "POST"
          ? `/marketplaces/${candidate.marketplaceCode}/categories/${encodeURIComponent(
              category.name
            )}/addproduct`
          : `/marketplaces/${candidate.marketplaceCode}/products/${encodeURIComponent(
              candidate.externalId
            )}?categoryName=${encodeURIComponent(category.name)}`;
      const message = await request<string>(endpoint, {
        method,
        body:
          method === "POST"
            ? JSON.stringify({ productId: candidate.externalId })
            : undefined,
      });
      addNotice(message || successMessage, "success");
      setCandidateAddedState(category.id, candidate, isAdded);
      await refreshCategoryMarketplaceData(category);
    } catch (err) {
      const actionText = method === "POST" ? "eklenemedi" : "silinemedi";
      addNotice(`Urun ${actionText}: ${(err as Error).message}`, "error");
    } finally {
      setCandidateBusy(false);
    }
  };

  const handleAddCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    await mutateCandidateProduct(category, candidate, "POST", "Urun eklendi.", true);
  };

  const handleRemoveCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    await mutateCandidateProduct(category, candidate, "DELETE", "Urun silindi.", false);
  };

  const handleSelectAddedProduct = async (
    product: MarketplaceProductEntryResponse
  ) => {
    setSelectedAddedProduct(product);
    setSelectedProductHistory([]);
    if (product.productId) {
      setHistoryBusy(true);
      try {
        const data = await request<PriceHistoryResponse[]>(
          `/products/${product.productId}/prices`
        );
        const history = data.map((item) => ({
          recordedAt: item.recordedAt,
          price: Number(item.price),
        }));
        setSelectedProductHistory(history);
      } catch (err) {
        addNotice(`Fiyat gecmisi alinamadi: ${(err as Error).message}`, "error");
      } finally {
        setHistoryBusy(false);
      }
    }
  };

  const handleToggleProduct = async (productId: number) => {
    setExpandedProducts((prev) => ({
      ...prev,
      [productId]: !prev[productId],
    }));

    if (!productDetails[productId]) {
      try {
        const data = await request<ProductDetailResponse>(`/products/${productId}`);
        setProductDetails((prev) => ({ ...prev, [productId]: data }));
      } catch (err) {
        addNotice(`Urun detaylari alinamadi: ${(err as Error).message}`, "error");
      }
    }
  };

  const onSelectAddedProductClick = (item: MarketplaceProductEntryResponse) => {
    return () => {
      void handleSelectAddedProduct(item);
    };
  };

  const onSelectAddedProductKeyDown = (item: MarketplaceProductEntryResponse) => {
    return (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      event.preventDefault();
      void handleSelectAddedProduct(item);
    };
  };

  const onRemoveAddedProductClick = (
    category: Category,
    item: MarketplaceProductEntryResponse
  ) => {
    return (event: React.MouseEvent<HTMLButtonElement>) => {
      event.stopPropagation();
      void handleRemoveCandidateProduct(category, {
        marketplaceCode: "MG",
        externalId: item.externalId,
        name: item.name,
        brandName: item.brandName,
        imageUrl: item.imageUrl,
        price: item.price,
      });
    };
  };

  const onToggleProductClick = (productId: number) => {
    return () => {
      void handleToggleProduct(productId);
    };
  };

  const activeCategory = categories.find((cat) => cat.id === expandedCategoryId);
  const activeProducts = activeCategory ? productsByCategory[activeCategory.id] : null;
  const activeCandidates = activeCategory
    ? candidateItemsByCategory[activeCategory.id] ?? []
    : [];
  const addedProducts = useMemo(() => {
    if (!activeCategory) {
      return [];
    }
    return addedProductsByCategory[activeCategory.id] ?? [];
  }, [activeCategory, addedProductsByCategory]);
  const addedCandidateIds = useMemo(
    () => new Set(addedProducts.map((item) => item.externalId)),
    [addedProducts]
  );
  const addedCandidateKeys =
    activeCategory && addedCandidateKeysByCategory[activeCategory.id]
      ? addedCandidateKeysByCategory[activeCategory.id]
      : {};
  const visibleCategories = showAllCategories
    ? categories
    : categories.slice(0, CATEGORY_VISIBLE_COUNT);

  const marketplaceSections = useMemo(() => {
    if (!activeProducts) {
      return [];
    }
    return MARKETPLACES.map((marketplace) => ({
      ...marketplace,
      products: activeProducts[marketplace.code],
    }));
  }, [activeProducts]);

  let historyContent: ReactNode;
  if (historyBusy) {
    historyContent = (
      <p className="mt-3 text-sm text-[#6b655c]">Yukleniyor...</p>
    );
  } else if (selectedProductHistory.length === 0) {
    historyContent = (
      <p className="mt-3 text-sm text-[#6b655c]">
        Fiyat gecmisi yok.
      </p>
    );
  } else {
    historyContent = (
      <div className="mt-3 space-y-3">
        <div className="h-40 w-full">
          <svg viewBox="0 0 400 160" className="h-full w-full">
            <defs>
              <linearGradient id="priceLine" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor="#d97706" />
                <stop offset="100%" stopColor="#fbbf24" />
              </linearGradient>
            </defs>
            {(() => {
              const points = [...selectedProductHistory].reverse();
              const prices = points.map((point) => point.price);
              const min = Math.min(...prices);
              const max = Math.max(...prices);
              const range = max - min || 1;
              const coords = points.map((point, index) => {
                const x = (index / Math.max(points.length - 1, 1)) * 360 + 20;
                const y = 140 - ((point.price - min) / range) * 110;
                return { x, y };
              });
              const line = coords
                .map((coord) => `${coord.x},${coord.y}`)
                .join(" ");
              return (
                <>
                  <polyline
                    fill="none"
                    stroke="url(#priceLine)"
                    strokeWidth="3"
                    strokeLinejoin="round"
                    strokeLinecap="round"
                    points={line}
                  />
                  {coords.map((coord, index) => (
                    <circle
                      key={`${coord.x}-${index}`}
                      cx={coord.x}
                      cy={coord.y}
                      r="3.5"
                      fill="#d97706"
                    />
                  ))}
                </>
              );
            })()}
          </svg>
        </div>
        <div className="max-h-40 overflow-y-auto rounded-xl border border-black/5">
          <table className="w-full text-xs text-[#6b655c]">
            <thead className="sticky top-0 bg-white text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
              <tr>
                <th className="px-3 py-2 text-left">Tarih</th>
                <th className="px-3 py-2 text-right">Fiyat</th>
              </tr>
            </thead>
            <tbody>
              {selectedProductHistory.map((item) => (
                <tr key={`${item.recordedAt}-${item.price}`}>
                  <td className="border-t border-black/5 px-3 py-2">
                    {item.recordedAt}
                  </td>
                  <td className="border-t border-black/5 px-3 py-2 text-right">
                    {item.price}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <section className="rounded-3xl border border-black/10 bg-white/70 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
              Kategori Merkezi
            </p>
            <h2 className="display text-3xl font-semibold">
              Kategoriler ve urun baglantilari
            </h2>
            <p className="mt-2 text-sm text-[#6b655c]">
              Kategori ekleyin, urun baglayin ve marketplace bazinda urunleri
              inceleyin.
            </p>
          </div>
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-amber-700">
              Toplam kategori
            </p>
            <p className="display text-2xl">{categories.length}</p>
          </div>
        </div>
        <div className="mt-5 flex flex-wrap items-center gap-2 text-xs text-[#6b655c]">
          <span className="rounded-full border border-black/10 bg-white px-3 py-1">
            Kategorileri secip urunleri goruntuleyin.
          </span>
        </div>
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.1fr_1fr]">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="display text-xl">Kategori Listesi</h3>
              <p className="text-xs text-[#6b655c]">
                Bir kategoriyi secerek urunlerini goruntuleyin.
              </p>
            </div>
            <button
              className="flex h-10 w-10 items-center justify-center rounded-full bg-[#d97706] text-xl font-semibold text-white transition hover:bg-[#b45309]"
              type="button"
              onClick={() => setShowAddCategory(true)}
              title="Kategori ekle"
            >
              +
            </button>
          </div>

          <div className="mt-6 space-y-3">
            {visibleCategories.map((category) => (
              <div
                key={category.id}
                ref={(node) => {
                  if (node) {
                    categoryRefs.current.set(category.id, node);
                  } else {
                    categoryRefs.current.delete(category.id);
                  }
                }}
                className={`rounded-2xl border border-black/5 bg-[#f9f4ee] p-4 transition ${
                  expandedCategoryId === category.id
                    ? "shadow-[0_20px_40px_-35px_rgba(0,0,0,0.5)]"
                    : ""
                }`}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <button
                    type="button"
                    className="flex items-center gap-3 text-left"
                    onClick={() => handleToggleCategory(category)}
                  >
                    <span className="display text-lg">{category.name}</span>
                    <span className="rounded-full border border-black/10 bg-white px-3 py-1 text-xs text-[#6b655c]">
                      #{category.id}
                    </span>
                  </button>
                </div>

                {expandedCategoryId === category.id && (
                  <div className="mt-4 space-y-4">
                    {activeProducts ? (
                      marketplaceSections.map((section) => {
                        const sectionCandidates = activeCandidates.filter(
                          (item) => item.marketplaceCode === section.code
                        );
                        const suggestedListKey = `${category.id}:suggested:${section.code}`;
                        const addedListKey = `${category.id}:added`;
                        const showAllAdded =
                          expandedLists[addedListKey] ||
                          addedProducts.length <= LIST_VISIBLE_COUNT;
                        const visibleAddedProducts = showAllAdded
                          ? addedProducts
                          : addedProducts.slice(0, LIST_VISIBLE_COUNT);
                        const showAllSuggested =
                          expandedLists[suggestedListKey] ||
                          sectionCandidates.length <= LIST_VISIBLE_COUNT;
                        const visibleSectionCandidates = showAllSuggested
                          ? sectionCandidates
                          : sectionCandidates.slice(0, LIST_VISIBLE_COUNT);
                        return (
                        <div
                          key={section.code}
                          className="rounded-2xl border border-black/5 bg-white p-3"
                        >
                          <div className="flex items-center justify-between">
                            <div>
                              <p className="display text-lg">{section.name}</p>
                            </div>
                            <span className="text-xs text-[#6b655c]">
                              {section.code === "MG"
                                ? addedProducts.length
                                : section.products.length}{" "}
                              urun
                            </span>
                          </div>
                          <div className="mt-3 space-y-3">
                            {section.code === "MG" ? (
                              <>
                                <div>
                                  <div className="flex items-center justify-between">
                                    <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                                      Eklenen Urunler
                                    </p>
                                    <span className="text-xs text-[#6b655c]">
                                      {addedProducts.length}
                                    </span>
                                  </div>
                                  <div className="mt-2 space-y-2">
                                    {addedProducts.length === 0 ? (
                                      <p className="text-xs text-[#6b655c]">
                                        Henuz urun eklenmedi.
                                      </p>
                                    ) : (
                                      visibleAddedProducts.map((item, index) => (
                                        <div
                                          key={`${item.externalId}-${index}`}
                                          role="button"
                                          tabIndex={0}
                                          className="flex w-full items-center gap-3 rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 text-left transition hover:bg-white"
                                          onClick={onSelectAddedProductClick(item)}
                                          onKeyDown={onSelectAddedProductKeyDown(item)}
                                        >
                                          <div className="h-10 w-10 overflow-hidden rounded-xl border border-black/10 bg-white">
                                            {item.imageUrl ? (
                                              <img
                                                src={item.imageUrl}
                                                alt={item.name}
                                                className="h-full w-full object-cover"
                                              />
                                            ) : (
                                              <div className="flex h-full w-full items-center justify-center text-[10px] text-[#6b655c]">
                                                Gorsel yok
                                              </div>
                                            )}
                                          </div>
                                          <div className="flex-1">
                                            <p className="text-sm font-semibold text-[#111]">
                                              {item.name || `Urun ${item.externalId}`}
                                            </p>
                                            <p className="text-xs text-[#6b655c]">
                                              {item.brandName || item.externalId}
                                              {formatPriceSuffix(item.price)}
                                            </p>
                                          </div>
                                          <button
                                            type="button"
                                            className="flex h-8 w-8 items-center justify-center rounded-full border border-black/10 bg-white text-lg font-semibold text-[#9a5c00] transition hover:bg-amber-50"
                                            onClick={onRemoveAddedProductClick(category, item)}
                                            disabled={candidateBusy}
                                            title="Urun sil"
                                          >
                                            -
                                          </button>
                                        </div>
                                      ))
                                    )}
                                    {addedProducts.length > LIST_VISIBLE_COUNT && (
                                      <button
                                        type="button"
                                        className="w-full rounded-2xl border border-black/10 bg-white px-3 py-2 text-xs uppercase tracking-[0.2em] text-[#9a5c00] transition hover:bg-amber-50"
                                        onClick={() =>
                                          toggleList(addedListKey)
                                        }
                                      >
                                        {expandedLists[addedListKey]
                                          ? "Daha Az Goster"
                                          : "Tumunu Goster"}
                                      </button>
                                    )}
                                  </div>
                                </div>

                                <SuggestedProducts
                                  category={category}
                                  sectionCandidates={sectionCandidates}
                                  visibleSectionCandidates={visibleSectionCandidates}
                                  suggestedListKey={suggestedListKey}
                                  candidateBusy={candidateBusy}
                                  addedCandidateIds={addedCandidateIds}
                                  addedCandidateKeys={addedCandidateKeys}
                                  expandedLists={expandedLists}
                                  onAddCandidate={handleAddCandidateProduct}
                                  onToggleList={toggleList}
                                />
                              </>
                            ) : (
                              <>
                                <SuggestedProducts
                                  category={category}
                                  sectionCandidates={sectionCandidates}
                                  visibleSectionCandidates={visibleSectionCandidates}
                                  suggestedListKey={suggestedListKey}
                                  candidateBusy={candidateBusy}
                                  addedCandidateIds={addedCandidateIds}
                                  addedCandidateKeys={addedCandidateKeys}
                                  expandedLists={expandedLists}
                                  onAddCandidate={handleAddCandidateProduct}
                                  onToggleList={toggleList}
                                />

                                {section.products.length === 0 ? (
                                  <p className="text-xs text-[#6b655c]">
                                    Bu marketplace icin urun yok.
                                  </p>
                                ) : (
                                  section.products.map((product) => (
                                    <div
                                      key={product.id}
                                      className="rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2"
                                    >
                                  <button
                                    type="button"
                                    className="flex w-full items-center justify-between text-left text-sm"
                                    onClick={onToggleProductClick(product.id)}
                                  >
                                    <span>{product.name}</span>
                                    <div className="flex items-center gap-3 text-xs text-[#6b655c]">
                                      <span>
                                        {formatPriceOrDash(product.price)}
                                      </span>
                                      <span>#{product.id}</span>
                                    </div>
                                  </button>
                                      {expandedProducts[product.id] && (
                                        <div className="mt-2 rounded-xl border border-black/5 bg-white px-3 py-2 text-xs text-[#6b655c]">
                                          {productDetails[product.id] ? (
                                            <div className="space-y-1">
                                              <p className="text-sm font-semibold text-[#111]">
                                                {productDetails[product.id].name}
                                              </p>
                                              <p>
                                                Marka:{" "}
                                                {productDetails[product.id].brand ?? "Bilinmiyor"}
                                              </p>
                                              <p>
                                                Birim:{" "}
                                                {productDetails[product.id].unitValue ?? "?"}
                                                {productDetails[product.id].unit ?? ""}
                                              </p>
                                              <p>
                                                Kategori:{" "}
                                                {productDetails[product.id].categoryName ?? "-"}
                                              </p>
                                              <p>
                                                Eklenme:{" "}
                                                {productDetails[product.id].createdAt ?? "-"}
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
                              </>
                            )}
                          </div>
                        </div>
                      );
                      })
                    ) : (
                      <p className="text-xs text-[#6b655c]">
                        Urunler yukleniyor...
                      </p>
                    )}
                  </div>
                )}
              </div>
            ))}
            {categories.length > CATEGORY_VISIBLE_COUNT && (
              <button
                type="button"
                className="w-full rounded-2xl border border-black/10 bg-white px-4 py-2 text-xs uppercase tracking-[0.2em] text-[#9a5c00] transition hover:bg-amber-50"
                onClick={() => setShowAllCategories((prev) => !prev)}
              >
                {showAllCategories ? "Daha Az Goster" : "Tumunu Goster"}
              </button>
            )}
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
            <h3 className="display text-xl">Urun Bilgisi</h3>
            <p className="mt-1 text-xs text-[#6b655c]">
              Eklenen bir urune tiklayarak detaylarini gorun.
            </p>
            {selectedAddedProduct ? (
              <div className="mt-4 rounded-2xl border border-black/5 bg-[#f9f4ee] p-4">
                <div className="flex items-center gap-3">
                  <div className="h-14 w-14 overflow-hidden rounded-2xl border border-black/10 bg-white">
                    {selectedAddedProduct.imageUrl ? (
                      <img
                        src={selectedAddedProduct.imageUrl}
                        alt={selectedAddedProduct.name}
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="flex h-full w-full items-center justify-center text-xs text-[#6b655c]">
                        Gorsel yok
                      </div>
                    )}
                  </div>
                  <div>
                    <p className="text-base font-semibold text-[#111]">
                      {selectedAddedProduct.name || "Isimsiz urun"}
                    </p>
                    <p className="text-xs text-[#6b655c]">
                      {selectedAddedProduct.brandName ||
                        selectedAddedProduct.externalId}
                    </p>
                    <p className="text-xs text-[#6b655c]">
                      {formatPriceOrLabel(selectedAddedProduct.price)}
                    </p>
                  </div>
                </div>
              <div className="mt-3 grid gap-2 text-xs text-[#6b655c]">
                <div className="flex items-center gap-2 rounded-xl border border-black/5 bg-white px-3 py-2">
                  <span>Marketplace:</span>
                  {selectedAddedProduct.marketplaceCode === "MG" && (
                    <img
                      src="/migros-logo.png"
                      alt="Migros"
                      className="h-4 w-4"
                    />
                  )}
                  <span>
                    {selectedAddedProduct.marketplaceCode === "MG"
                      ? "Migros"
                      : selectedAddedProduct.marketplaceCode}
                  </span>
                </div>
              </div>
              <div className="mt-4 rounded-2xl border border-black/5 bg-white p-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                    Fiyat Gecmisi
                  </p>
                  <span className="text-xs text-[#6b655c]">
                    {selectedProductHistory.length} kayit
                  </span>
                </div>
                {historyContent}
              </div>
            </div>
          ) : (
              <div className="mt-4 rounded-2xl border border-dashed border-black/10 bg-white px-4 py-6 text-sm text-[#6b655c]">
                Secili urun yok.
              </div>
            )}
          </div>
        </div>
      </section>

      {showAddCategory && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-md rounded-3xl border border-black/10 bg-white p-6 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.55)]">
            <div className="flex items-center justify-between">
              <h3 className="display text-xl">Yeni Kategori</h3>
              <button
                className="text-sm text-[#6b655c] transition hover:text-[#111]"
                type="button"
                onClick={() => setShowAddCategory(false)}
              >
                Kapat
              </button>
            </div>
            <p className="mt-1 text-xs text-[#6b655c]">
              Eklemek istediginiz kategori adini yazin.
            </p>
            <input
              className="mt-4 w-full rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
              placeholder="Kategori adi"
              value={categoryName}
              onChange={(event) => setCategoryName(event.target.value)}
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                className="rounded-xl border border-black/10 px-4 py-2 text-sm text-[#6b655c] transition hover:bg-[#f4ede3]"
                type="button"
                onClick={() => setShowAddCategory(false)}
              >
                Iptal
              </button>
              <button
                className="rounded-xl bg-[#d97706] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#b45309]"
                type="button"
                onClick={handleCreateCategory}
                disabled={busy}
              >
                Kaydet
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="pointer-events-none fixed right-6 top-6 z-50 flex w-[320px] flex-col gap-2">
        {notices.map((notice) => (
          <div
            key={notice.id}
            className={`pointer-events-auto rounded-2xl border px-4 py-3 text-sm shadow-[0_12px_30px_-20px_rgba(0,0,0,0.4)] ${getNoticeToneClass(
              notice.tone
            )}`}
          >
            {notice.message}
          </div>
        ))}
      </div>
    </div>
  );
}
