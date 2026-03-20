"use client";

import { createPortal } from "react-dom";
import { useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import ProductInfoModal from "@/components/ProductInfoModal";
import { request, requestForMarketplace } from "@/lib/api";
import type { MarketplaceProductEntryResponse, NeedListItemDto, PriceHistoryResponse } from "@/lib/types";

type NeedItem = NeedListItemDto;
type HistoryRangeFilter = "1M" | "3M" | "1Y";
type HistoryPoint = {
  recordedAt: string;
  marketplaceCode: string;
  price: number;
  availabilityScore: number | null;
  opportunityLevel: string | null;
};
type HoveredHistoryPoint = {
  x: number;
  y: number;
  point: HistoryPoint & { ts: number };
};
type PreviewItem = {
  key: string;
  productId: number | null;
  categoryId: number;
  name: string;
  imageUrl: string;
  brandName: string | null;
  marketplaceCode: "YS" | "MG" | null;
  externalId: string | null;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  opportunityLevel: string | null;
};

const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";

const urgencyLabel = (urgency: NeedItem["urgency"]) => {
  if (urgency === "VERY_URGENT") {
    return "Acil";
  }
  if (urgency === "URGENT") {
    return "Normal";
  }
  return "Acil Degil";
};

const urgencyRank = (urgency: NeedItem["urgency"]) => {
  if (urgency === "VERY_URGENT") {
    return 3;
  }
  if (urgency === "URGENT") {
    return 2;
  }
  return 1;
};

const resolveAvailabilityStatus = (score: number | null): "Uygun" | "Normal" | "Pahali" => {
  if (score === null || Number.isNaN(score)) {
    return "Normal";
  }
  if (score >= 70) {
    return "Uygun";
  }
  if (score >= 50) {
    return "Normal";
  }
  return "Pahali";
};

const formatPrice = (value: number | null) => (value === null ? "-" : `${value.toFixed(2)} TL`);
const formatTl = (value: number | null, digits = 2) => (value === null ? "-" : `${value.toFixed(digits)} TL`);
const formatMonthTick = (timestamp: number) => {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = String(date.getFullYear()).slice(-2);
  return `${month}/${year}`;
};
const buildNicePriceAxis = (maxValue: number, targetTickCount = 5) => {
  const safeMax = Number.isFinite(maxValue) && maxValue > 0 ? maxValue : 1;
  const roughStep = safeMax / targetTickCount;
  const exponent = Math.floor(Math.log10(roughStep));
  const magnitude = 10 ** exponent;
  const fraction = roughStep / magnitude;
  let niceFraction = 1;
  if (fraction > 1 && fraction <= 2) {
    niceFraction = 2;
  } else if (fraction > 2 && fraction <= 5) {
    niceFraction = 5;
  } else if (fraction > 5) {
    niceFraction = 10;
  }
  const step = niceFraction * magnitude;
  const maxTick = Math.ceil(safeMax / step) * step;
  const ticks: number[] = [];
  for (let value = 0; value <= maxTick + step / 2; value += step) {
    ticks.push(Number(value.toFixed(4)));
  }
  return { step, maxTick, ticks };
};
const formatAxisTickLabel = (value: number, step: number) => {
  if (Number.isInteger(value)) {
    return value.toFixed(0);
  }
  if (step >= 1) {
    return value.toFixed(1);
  }
  return value.toFixed(2);
};

export default function NeedsPage() {
  const [items, setItems] = useState<NeedItem[]>([]);
  const [expandedCategoryKeys, setExpandedCategoryKeys] = useState<Record<string, boolean>>({});
  const [selectedPreviewItem, setSelectedPreviewItem] = useState<PreviewItem | null>(null);
  const [selectedNeedUrgency, setSelectedNeedUrgency] = useState<NeedItem["urgency"]>("NOT_URGENT");
  const [selectedProductHistory, setSelectedProductHistory] = useState<PriceHistoryResponse[]>([]);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [historyMarketplaceFilter, setHistoryMarketplaceFilter] = useState<"ALL" | "YS" | "MG">("ALL");
  const [historyRangeFilter, setHistoryRangeFilter] = useState<HistoryRangeFilter>("1M");
  const [hoveredHistoryPoint, setHoveredHistoryPoint] = useState<HoveredHistoryPoint | null>(null);

  useEffect(() => {
    void request<NeedListItemDto[]>("/needs")
      .then((rows) => setItems((Array.isArray(rows) ? rows : []) as NeedItem[]))
      .catch(() => {
        try {
          const raw = window.localStorage.getItem(NEED_LIST_STORAGE_KEY);
          const parsed = raw ? (JSON.parse(raw) as NeedItem[]) : [];
          setItems(Array.isArray(parsed) ? parsed : []);
        } catch {
          setItems([]);
        }
      });
  }, []);

  const isClient = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false
  );
  const headerActionsEl = isClient ? document.getElementById("header-actions") : null;

  const productItemsByCategory = useMemo(() => {
    const map = new Map<number, NeedItem[]>();
    items
      .filter((item) => item.type === "PRODUCT")
      .forEach((item) => {
        const list = map.get(item.categoryId) ?? [];
        list.push(item);
        map.set(item.categoryId, list);
      });
    return map;
  }, [items]);

  const groupedEntries = useMemo(() => {
    const entries: Array<
      | { kind: "single"; key: string; item: NeedItem }
      | {
          kind: "group";
          key: string;
          categoryId: number;
          categoryName: string;
          products: NeedItem[];
        }
    > = [];
    const groupedCategoryIds = new Set<number>();
    items.forEach((item) => {
      if (item.type === "PRODUCT") {
        const categoryProducts = productItemsByCategory.get(item.categoryId) ?? [];
        if (categoryProducts.length > 1) {
          if (!groupedCategoryIds.has(item.categoryId)) {
            groupedCategoryIds.add(item.categoryId);
            entries.push({
              kind: "group",
              key: `group:${item.categoryId}`,
              categoryId: item.categoryId,
              categoryName: item.categoryName,
              products: categoryProducts,
            });
          }
          return;
        }
      }
      entries.push({ kind: "single", key: item.key, item });
    });
    return entries;
  }, [items, productItemsByCategory]);

  const removeItem = (key: string) => {
    setItems((prev) => {
      const next = prev.filter((item) => item.key !== key);
      persistNeedList(next);
      return next;
    });
  };

  const removeCategoryProducts = (categoryId: number) => {
    setItems((prev) => {
      const next = prev.filter(
        (item) => !(item.type === "PRODUCT" && item.categoryId === categoryId)
      );
      persistNeedList(next);
      return next;
    });
  };

  const persistNeedList = (next: NeedItem[]) => {
    window.localStorage.setItem(NEED_LIST_STORAGE_KEY, JSON.stringify(next));
    void request<NeedListItemDto[]>("/needs", {
      method: "PUT",
      body: JSON.stringify(next),
    }).catch(() => undefined);
  };

  const toggleCategory = (key: string) => {
    setExpandedCategoryKeys((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const openProductInfo = async (item: NeedItem) => {
    if (item.type !== "PRODUCT" || !item.marketplaceCode) {
      return;
    }
    setSelectedProductHistory([]);
    setHistoryMarketplaceFilter("ALL");
    setHistoryRangeFilter("1M");
    setHoveredHistoryPoint(null);
    setSelectedNeedUrgency(item.urgency);
    let detail: MarketplaceProductEntryResponse | null = null;
    try {
      const rows = await requestForMarketplace<MarketplaceProductEntryResponse[]>(
        item.marketplaceCode,
        `/categories/${item.categoryId}/marketplace-products/added`
      );
      const list = Array.isArray(rows) ? rows : [];
      detail =
        list.find((candidate) => candidate.externalId === item.externalId) ??
        list.find((candidate) => candidate.name === item.name) ??
        null;
    } catch {
      detail = null;
    }
    setSelectedPreviewItem({
      key: item.key,
      productId: detail?.productId ?? null,
      categoryId: item.categoryId,
      name: detail?.name || item.name,
      imageUrl: detail?.imageUrl || item.imageUrl,
      brandName: detail?.brandName ?? null,
      marketplaceCode: item.marketplaceCode,
      externalId: detail?.externalId ?? item.externalId,
      price: detail?.price ?? item.price,
      moneyPrice: detail?.moneyPrice ?? item.moneyPrice,
      basketDiscountThreshold: detail?.basketDiscountThreshold ?? item.basketDiscountThreshold,
      basketDiscountPrice: detail?.basketDiscountPrice ?? item.basketDiscountPrice,
      campaignBuyQuantity: detail?.campaignBuyQuantity ?? item.campaignBuyQuantity,
      campaignPayQuantity: detail?.campaignPayQuantity ?? item.campaignPayQuantity,
      effectivePrice: detail?.effectivePrice ?? item.effectivePrice,
      opportunityLevel: item.opportunityLevel,
    });
    setHistoryBusy((detail?.productId ?? null) !== null);
  };

  useEffect(() => {
    let cancelled = false;
    if (!selectedPreviewItem?.productId || !selectedPreviewItem.marketplaceCode) {
      return;
    }
    const query = new URLSearchParams({
      marketplaceCode: selectedPreviewItem.marketplaceCode,
      useMoneyPrice: "false",
      useEffectivePrice: "false",
    });
    void request<PriceHistoryResponse[]>(
      `/products/${selectedPreviewItem.productId}/prices?${query.toString()}`
    )
      .then((rows) => {
        if (cancelled) {
          return;
        }
        setSelectedProductHistory(Array.isArray(rows) ? rows : []);
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        setSelectedProductHistory([]);
      })
      .finally(() => {
        if (!cancelled) {
          setHistoryBusy(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [selectedPreviewItem]);

  const selectedOpportunityLevel = useMemo(() => {
    if (!selectedPreviewItem) {
      return "-";
    }
    const latest = [...selectedProductHistory]
      .filter((item) => item.marketplaceCode === selectedPreviewItem.marketplaceCode)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    return latest?.opportunityLevel ?? selectedPreviewItem.opportunityLevel ?? "Normal";
  }, [selectedPreviewItem, selectedProductHistory]);

  const selectedTodayRecommendation = useMemo(() => {
    if (!selectedPreviewItem) {
      return { label: "-", reason: "Secili urun yok." };
    }
    const latest = [...selectedProductHistory]
      .filter((item) => item.marketplaceCode === selectedPreviewItem.marketplaceCode)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    const status = resolveAvailabilityStatus(latest?.availabilityScore ?? null);
    const shouldBuy =
      status === "Uygun" || (status === "Normal" && selectedOpportunityLevel === "Yuksek");
    return {
      label: shouldBuy ? "Bugun Alinabilir" : "Bekletilebilir",
      reason: shouldBuy
        ? `${status} fiyat seviyesi ve firsat durumu uygun.`
        : `${status} fiyat seviyesi nedeniyle beklemek daha iyi olabilir.`,
    };
  }, [selectedPreviewItem, selectedProductHistory, selectedOpportunityLevel]);

  const compactHistory = useMemo<HistoryPoint[]>(() => {
    const grouped = new Map<
      string,
      {
        recordedAt: string;
        marketplaceCode: string;
        totalPrice: number;
        count: number;
        availabilityScore: number | null;
        opportunityLevel: string | null;
      }
    >();
    selectedProductHistory.forEach((row) => {
      const key = `${row.recordedAt}:${row.marketplaceCode}`;
      const existing = grouped.get(key);
      if (existing) {
        existing.totalPrice += row.price;
        existing.count += 1;
        if (existing.availabilityScore === null && row.availabilityScore !== null) {
          existing.availabilityScore = row.availabilityScore;
        }
        if (existing.opportunityLevel === null && row.opportunityLevel !== null) {
          existing.opportunityLevel = row.opportunityLevel;
        }
      } else {
        grouped.set(key, {
          recordedAt: row.recordedAt,
          marketplaceCode: row.marketplaceCode,
          totalPrice: row.price,
          count: 1,
          availabilityScore: row.availabilityScore,
          opportunityLevel: row.opportunityLevel,
        });
      }
    });
    return Array.from(grouped.values()).map((item) => ({
      recordedAt: item.recordedAt,
      marketplaceCode: item.marketplaceCode,
      price: item.totalPrice / Math.max(item.count, 1),
      availabilityScore: item.availabilityScore,
      opportunityLevel: item.opportunityLevel,
    }));
  }, [selectedProductHistory]);

  const filteredHistory = useMemo(() => {
    if (historyMarketplaceFilter === "ALL") {
      return compactHistory;
    }
    return compactHistory.filter((item) => item.marketplaceCode === historyMarketplaceFilter);
  }, [compactHistory, historyMarketplaceFilter]);

  const rangeFilteredHistory = useMemo(() => {
    if (filteredHistory.length === 0) {
      return [];
    }
    const parsedPoints = filteredHistory
      .map((item) => ({ item, ts: Date.parse(item.recordedAt) }))
      .filter((entry) => Number.isFinite(entry.ts));
    if (parsedPoints.length === 0) {
      return filteredHistory;
    }
    const latestTs = Math.max(...parsedPoints.map((entry) => entry.ts));
    const latestDate = new Date(latestTs);
    const startDate = new Date(latestDate);
    if (historyRangeFilter === "1M") {
      startDate.setMonth(startDate.getMonth() - 1);
    } else if (historyRangeFilter === "3M") {
      startDate.setMonth(startDate.getMonth() - 3);
    } else {
      startDate.setFullYear(startDate.getFullYear() - 1);
    }
    const startTs = startDate.getTime();
    return parsedPoints
      .filter((entry) => entry.ts >= startTs && entry.ts <= latestTs)
      .map((entry) => entry.item);
  }, [filteredHistory, historyRangeFilter]);

  const historyBandSummary = useMemo(() => {
    const baseBands = [
      { label: "Pahali", color: "#fed7aa", count: 0 },
      { label: "Normal", color: "#bae6fd", count: 0 },
      { label: "Uygun", color: "#bbf7d0", count: 0 },
    ];
    rangeFilteredHistory.forEach((point) => {
      const status = resolveAvailabilityStatus(point.availabilityScore ?? null);
      const target =
        status === "Uygun" ? baseBands[2] : status === "Pahali" ? baseBands[0] : baseBands[1];
      target.count += 1;
    });
    const total = baseBands.reduce((sum, band) => sum + band.count, 0);
    const normalizedTotal = total === 0 ? 3 : total;
    if (total === 0) {
      baseBands.forEach((band) => {
        band.count = 1;
      });
    }
    return { bands: baseBands, normalizedTotal };
  }, [rangeFilteredHistory]);

  const historyContent: ReactNode = historyBusy ? (
    <p className="mt-3 text-sm text-[#6b655c]">Yukleniyor...</p>
  ) : selectedProductHistory.length === 0 ? (
    <p className="mt-3 text-sm text-[#6b655c]">Fiyat gecmisi yok.</p>
  ) : filteredHistory.length === 0 ? (
    <p className="mt-3 text-sm text-[#6b655c]">Secili market icin kayit yok.</p>
  ) : rangeFilteredHistory.length === 0 ? (
    <p className="mt-3 text-sm text-[#6b655c]">Secili zaman araliginda kayit yok.</p>
  ) : (
    <div className="mt-3 space-y-3">
      <div className="flex flex-wrap items-center gap-2 text-[11px] text-[#6b655c]">
        <span className="text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">Marketler</span>
        <button
          type="button"
          className={`rounded-full border px-2 py-1 transition ${
            historyMarketplaceFilter === "ALL"
              ? "border-black/20 bg-white text-[#111]"
              : "border-black/10 bg-[#f9f4ee] text-[#6b655c]"
          }`}
          onClick={() => setHistoryMarketplaceFilter("ALL")}
        >
          Tum
        </button>
        <button
          type="button"
          className={`inline-flex items-center gap-1 rounded-full border px-2 py-1 transition ${
            historyMarketplaceFilter === "YS"
              ? "border-rose-300 bg-rose-50 text-rose-700"
              : "border-black/10 bg-[#f9f4ee] text-[#6b655c]"
          }`}
          onClick={() => setHistoryMarketplaceFilter("YS")}
        >
          <span className="h-2 w-2 rounded-full bg-rose-500" />
          Yemeksepeti
        </button>
        <button
          type="button"
          className={`inline-flex items-center gap-1 rounded-full border px-2 py-1 transition ${
            historyMarketplaceFilter === "MG"
              ? "border-amber-300 bg-amber-50 text-amber-700"
              : "border-black/10 bg-[#f9f4ee] text-[#6b655c]"
          }`}
          onClick={() => setHistoryMarketplaceFilter("MG")}
        >
          <span className="h-2 w-2 rounded-full bg-amber-500" />
          Migros
        </button>
        <span className="inline-flex items-center gap-1">
          <span className="h-[2px] w-4 border-t-2 border-dashed border-slate-400" />
          Aylik Ortalama
        </span>
        <span className="ml-2 text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">Zaman</span>
        <select
          className="rounded-xl border border-black/10 bg-white px-2 py-1 text-[11px] text-[#111] focus:border-amber-400 focus:outline-none focus:ring-2 focus:ring-amber-100"
          value={historyRangeFilter}
          onChange={(event) => setHistoryRangeFilter(event.target.value as HistoryRangeFilter)}
        >
          <option value="1M">Aylik</option>
          <option value="3M">3 Aylik</option>
          <option value="1Y">Yillik</option>
        </select>
      </div>
      <div className="flex w-full flex-col gap-3 lg:flex-row">
        <div className="w-full rounded-2xl border border-black/10 bg-[#f9f4ee] p-3 lg:w-36">
          <p className="text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">Durum Dagilimi</p>
          <div className="mt-2 space-y-2">
            {historyBandSummary.bands.map((band) => {
              const ratio = Math.round((band.count / historyBandSummary.normalizedTotal) * 100);
              return (
                <div key={`band-${band.label}`} className="rounded-xl border border-black/5 bg-white px-2 py-1.5 text-[11px]">
                  <div className="flex items-center justify-between">
                    <span className="inline-flex items-center gap-1 text-[#334155]">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: band.color }} />
                      {band.label}
                    </span>
                    <span className="font-semibold text-[#111]">{ratio}%</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
        <div className="h-80 flex-1 lg:h-[26rem]">
          <svg viewBox="0 0 420 300" className="h-full w-full">
            {(() => {
              const axisLeft = 50;
              const axisRight = 390;
              const axisTop = 20;
              const axisBottom = 220;
              const xPadding = 18;
              const plotLeft = axisLeft + xPadding;
              const plotRight = axisRight - xPadding;
              const plotWidth = Math.max(plotRight - plotLeft, 1);
              const ySpan = axisBottom - axisTop;
              const points = [...rangeFilteredHistory]
                .map((point, index) => {
                  const parsed = Date.parse(point.recordedAt);
                  return { ...point, ts: Number.isFinite(parsed) ? parsed : index };
                })
                .sort((left, right) => left.ts - right.ts);
              const prices = points.map((point) => point.price);
              const max = Math.max(...prices, 0);
              const axis = buildNicePriceAxis(max);
              const range = Math.max(axis.maxTick, 1);
              const minTs = Math.min(...points.map((point) => point.ts));
              const maxTs = Math.max(...points.map((point) => point.ts));
              const timeRange = maxTs - minTs || 1;
              const toX = (ts: number) => ((ts - minTs) / timeRange) * plotWidth + plotLeft;
              const ysCoords = points
                .filter((point) => point.marketplaceCode === "YS")
                .map((point) => ({
                  x: toX(point.ts),
                  y: axisBottom - (point.price / range) * ySpan,
                  point,
                }));
              const mgCoords = points
                .filter((point) => point.marketplaceCode === "MG")
                .map((point) => ({
                  x: toX(point.ts),
                  y: axisBottom - (point.price / range) * ySpan,
                  point,
                }));
              const allCoords = [...ysCoords, ...mgCoords];
              const monthlyBuckets = points.reduce<Record<string, number[]>>((acc, point) => {
                const monthKey = point.recordedAt.slice(0, 7);
                if (!acc[monthKey]) {
                  acc[monthKey] = [];
                }
                acc[monthKey].push(point.price);
                return acc;
              }, {});
              const monthAverages = Object.values(monthlyBuckets).map((values) => {
                const total = values.reduce((sum, value) => sum + value, 0);
                return total / values.length;
              });
              const monthlyAverage = monthAverages.reduce((sum, value) => sum + value, 0) / Math.max(monthAverages.length, 1);
              const averageY = axisBottom - (monthlyAverage / range) * ySpan;
              const ysLine = ysCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
              const mgLine = mgCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
              const tickValues = axis.ticks;
              const monthTickMap = new Map<string, number>();
              points.forEach((point) => {
                const date = new Date(point.ts);
                const monthKey = `${date.getFullYear()}-${date.getMonth()}`;
                if (!monthTickMap.has(monthKey)) {
                  monthTickMap.set(monthKey, point.ts);
                }
              });
              const monthTicksRaw = Array.from(monthTickMap.values()).map((ts) => ({
                ts,
                x: toX(ts),
                label: formatMonthTick(ts),
              }));
              const monthTickSkip = Math.max(1, Math.ceil(monthTicksRaw.length / 8));
              const monthTicks = monthTicksRaw.filter((_, index) => index % monthTickSkip === 0);
              if (
                monthTicksRaw.length > 0 &&
                monthTicks[monthTicks.length - 1]?.ts !== monthTicksRaw[monthTicksRaw.length - 1]?.ts
              ) {
                monthTicks.push(monthTicksRaw[monthTicksRaw.length - 1]);
              }
              const pointsByDate = points.reduce<
                Record<string, { ys?: typeof points[number]; mg?: typeof points[number] }>
              >((acc, point) => {
                if (!acc[point.recordedAt]) {
                  acc[point.recordedAt] = {};
                }
                if (point.marketplaceCode === "YS") {
                  acc[point.recordedAt].ys = point;
                }
                if (point.marketplaceCode === "MG") {
                  acc[point.recordedAt].mg = point;
                }
                return acc;
              }, {});
              const hoveredDate = hoveredHistoryPoint?.point.recordedAt ?? null;
              const hoveredPair = hoveredDate ? pointsByDate[hoveredDate] : null;
              const availabilityStatus = resolveAvailabilityStatus(
                hoveredHistoryPoint?.point.availabilityScore ?? null
              );
              const availabilityValue =
                hoveredHistoryPoint?.point.availabilityScore !== null &&
                hoveredHistoryPoint?.point.availabilityScore !== undefined
                  ? hoveredHistoryPoint.point.availabilityScore.toFixed(1)
                  : "-";
              const hoverPanelWidth = axisRight - axisLeft;
              return (
                <>
                  <line x1={axisLeft} y1={axisTop} x2={axisLeft} y2={axisBottom} stroke="#94a3b8" strokeWidth="1" />
                  <line x1={axisLeft} y1={axisBottom} x2={axisRight} y2={axisBottom} stroke="#94a3b8" strokeWidth="1" />
                  {tickValues.map((value, index) => {
                    const y = axisBottom - (value / range) * ySpan;
                    return (
                      <g key={`tick-${index}`}>
                        <line x1={axisLeft} y1={y} x2={axisRight} y2={y} stroke="#cbd5e1" strokeWidth="1" opacity="0.45" />
                        <text x={axisLeft - 6} y={y + 3} textAnchor="end" fontSize="9" fill="#6b7280">
                          {formatAxisTickLabel(value, axis.step)}
                        </text>
                      </g>
                    );
                  })}
                  {monthTicks.map((tick, index) => (
                    <g key={`month-${index}`}>
                      <line x1={tick.x} y1={axisBottom} x2={tick.x} y2={axisBottom + 4} stroke="#94a3b8" strokeWidth="1" />
                      <text x={tick.x} y={axisBottom + 16} textAnchor="middle" fontSize="9" fill="#6b7280">
                        {tick.label}
                      </text>
                    </g>
                  ))}
                  <line x1={axisLeft} y1={averageY} x2={axisRight} y2={averageY} stroke="#94a3b8" strokeDasharray="6 5" strokeWidth="2" />
                  {ysLine && <polyline points={ysLine} fill="none" stroke="#f43f5e" strokeWidth="2" />}
                  {mgLine && <polyline points={mgLine} fill="none" stroke="#d97706" strokeWidth="2" />}
                  <rect
                    x={plotLeft}
                    y={axisTop}
                    width={plotWidth}
                    height={ySpan}
                    fill="transparent"
                    onMouseMove={(event) => {
                      if (allCoords.length === 0) {
                        return;
                      }
                      const rect = event.currentTarget.getBoundingClientRect();
                      const scaleX = plotWidth / rect.width;
                      const mouseX = (event.clientX - rect.left) * scaleX + plotLeft;
                      const nearest = allCoords.reduce((best, current) => {
                        if (!best) {
                          return current;
                        }
                        const bestDistance = Math.abs(best.x - mouseX);
                        const currentDistance = Math.abs(current.x - mouseX);
                        return currentDistance < bestDistance ? current : best;
                      }, allCoords[0]);
                      setHoveredHistoryPoint(nearest);
                    }}
                    onMouseLeave={() => setHoveredHistoryPoint(null)}
                  />
                  {ysCoords.map((coord, index) => (
                    <circle
                      key={`ys-hit-${coord.x}-${index}`}
                      cx={coord.x}
                      cy={coord.y}
                      r="9"
                      fill="transparent"
                      onMouseEnter={() => setHoveredHistoryPoint(coord)}
                      onMouseLeave={() => setHoveredHistoryPoint(null)}
                    />
                  ))}
                  {mgCoords.map((coord, index) => (
                    <circle
                      key={`mg-hit-${coord.x}-${index}`}
                      cx={coord.x}
                      cy={coord.y}
                      r="9"
                      fill="transparent"
                      onMouseEnter={() => setHoveredHistoryPoint(coord)}
                      onMouseLeave={() => setHoveredHistoryPoint(null)}
                    />
                  ))}
                  {hoveredHistoryPoint && (
                    <circle
                      cx={hoveredHistoryPoint.x}
                      cy={hoveredHistoryPoint.y}
                      r="4.5"
                      fill={hoveredHistoryPoint.point.marketplaceCode === "YS" ? "#e11d48" : "#d97706"}
                      stroke="#ffffff"
                      strokeWidth="1.2"
                    />
                  )}
                  {hoveredHistoryPoint && (
                    <line
                      x1={hoveredHistoryPoint.x}
                      y1={axisTop}
                      x2={hoveredHistoryPoint.x}
                      y2={axisBottom}
                      stroke="#334155"
                      strokeWidth="1"
                      strokeDasharray="4 4"
                      opacity="0.7"
                    />
                  )}
                  {hoveredHistoryPoint && hoveredPair && (
                    <g transform={`translate(${axisLeft}, ${axisBottom + 26})`}>
                      <rect
                        width={hoverPanelWidth}
                        height="32"
                        rx="8"
                        fill="#111827"
                        fillOpacity="0.92"
                        stroke="#374151"
                        strokeWidth="1"
                      />
                      <text x="8" y="14" fontSize="9.2" fill="#cbd5e1">
                        {hoveredDate ?? "-"}
                      </text>
                      <image
                        href="/yemeksepeti-logo.png"
                        x="78"
                        y="6"
                        width="10"
                        height="10"
                        preserveAspectRatio="xMidYMid meet"
                      />
                      <text x="92" y="14" fontSize="9.2" fill="#f9fafb">
                        {hoveredPair.ys ? `${hoveredPair.ys.price.toFixed(2)} TL` : "-"}
                      </text>
                      <image
                        href="/migros-logo.png"
                        x="150"
                        y="6"
                        width="10"
                        height="10"
                        preserveAspectRatio="xMidYMid meet"
                      />
                      <text x="164" y="14" fontSize="9.2" fill="#f9fafb">
                        {hoveredPair.mg ? `${hoveredPair.mg.price.toFixed(2)} TL` : "-"}
                      </text>
                      <text x="8" y="28" fontSize="9.2" fill="#cbd5e1">
                        Alinabilirlik: {availabilityValue} ({availabilityStatus})
                      </text>
                    </g>
                  )}
                </>
              );
            })()}
          </svg>
        </div>
      </div>
    </div>
  );

  return (
    <div className="mx-auto w-full max-w-5xl overflow-x-hidden px-3 py-8 sm:px-4">
      <div className="rounded-3xl border border-black/10 bg-white p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <h1 className="display text-3xl">Ihtiyac Listesi</h1>
        <p className="mt-1 text-sm text-[#6b655c]">Urun ve kategori bazli ihtiyac kayitlari.</p>
        <button
          type="button"
          className="mt-4 rounded-xl border border-black/10 bg-[#f9f4ee] px-4 py-2 text-sm font-semibold text-[#6b655c] transition hover:bg-amber-50"
          onClick={() => {
            window.location.href = "/";
          }}
        >
          Buraya ihtiyac ekle
        </button>
        <div className="mt-5 space-y-2">
          {groupedEntries.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-black/10 bg-[#f9f4ee] px-4 py-4 text-sm text-[#6b655c]">
              Liste bos.
            </div>
          ) : (
            groupedEntries.map((entry) => {
              if (entry.kind === "group") {
                const highestUrgency = [...entry.products]
                  .sort((left, right) => urgencyRank(right.urgency) - urgencyRank(left.urgency))[0]
                  ?.urgency ?? "NOT_URGENT";
                const groupUrgency = urgencyLabel(highestUrgency);
                return (
                  <div
                    key={entry.key}
                    className="rounded-2xl border border-black/10 bg-[#f9f4ee] px-4 py-3"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <button
                        type="button"
                        className="min-w-0 flex-1 text-left"
                        onClick={() => toggleCategory(entry.key)}
                      >
                        <p className="break-words text-base font-semibold text-[#111]">
                          {entry.categoryName} ({entry.products.length} urun)
                        </p>
                        <p className="text-xs text-[#6b655c]">
                          Kategori Gruplandirildi | {groupUrgency}
                        </p>
                      </button>
                      <button
                        type="button"
                        className="rounded-lg border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-700 transition hover:bg-rose-100"
                        onClick={() => removeCategoryProducts(entry.categoryId)}
                      >
                        Tumunu Sil
                      </button>
                    </div>
                    {expandedCategoryKeys[entry.key] && (
                      <div className="mt-3 rounded-xl border border-black/10 bg-white p-3">
                        <p className="text-[11px] uppercase tracking-[0.14em] text-[#9a5c00]">
                          Kategori Urunleri
                        </p>
                        <div className="mt-2 space-y-1">
                          {entry.products.map((product) => (
                            <div
                              key={`cat-product-${product.key}`}
                              className="flex items-center justify-between gap-2 text-xs text-[#334155]"
                            >
                              <button
                                type="button"
                                className="text-left transition hover:text-[#111]"
                                onClick={() => void openProductInfo(product)}
                              >
                                {product.name} | {urgencyLabel(product.urgency)}
                              </button>
                              <button
                                type="button"
                                className="rounded-md border border-black/10 bg-[#f9f4ee] px-2 py-0.5 text-[11px] text-[#6b655c] transition hover:bg-amber-50"
                                onClick={() => removeItem(product.key)}
                              >
                                Sil
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                );
              }

              const item = entry.item;
              return (
                <div
                  key={item.key}
                  className="rounded-2xl border border-black/10 bg-[#f9f4ee] px-4 py-3"
                >
                  <div className="flex items-start justify-between gap-3">
                    <button
                      type="button"
                      className="min-w-0 flex-1 text-left"
                      onClick={() =>
                        item.type === "CATEGORY" ? toggleCategory(item.key) : void openProductInfo(item)
                      }
                    >
                      <p className="break-words text-base font-semibold text-[#111]">{item.name}</p>
                      <p className="text-xs text-[#6b655c]">
                        {item.type === "CATEGORY" ? "Kategori Bazli" : "Urun Bazli"} |{" "}
                        {item.categoryName} | {urgencyLabel(item.urgency)}
                      </p>
                    </button>
                    <button
                      type="button"
                      className="rounded-lg border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-700 transition hover:bg-rose-100"
                      onClick={() => removeItem(item.key)}
                    >
                      Sil
                    </button>
                  </div>
                  {item.type === "CATEGORY" && expandedCategoryKeys[item.key] && (
                    <div className="mt-3 rounded-xl border border-black/10 bg-white p-3">
                      <p className="text-[11px] uppercase tracking-[0.14em] text-[#9a5c00]">
                        Kategori Urunleri
                      </p>
                      <div className="mt-2 space-y-1">
                        {(productItemsByCategory.get(item.categoryId) ?? []).length === 0 ? (
                          <p className="text-xs text-[#6b655c]">Bu kategori icin urun bazli kayit yok.</p>
                        ) : (
                          (productItemsByCategory.get(item.categoryId) ?? []).map((product) => (
                            <p key={`cat-product-${product.key}`} className="text-xs text-[#334155]">
                              {product.name} | {urgencyLabel(product.urgency)}
                            </p>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
      {headerActionsEl &&
        createPortal(
          <div className="group relative">
            <button
              type="button"
              className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
              onClick={() => {
                window.location.href = "/needs";
              }}
              title="Ihtiyac Listesi"
            >
              {"\u{1F4DD}"}
            </button>
            <div className="absolute right-0 top-12 hidden w-[min(18rem,calc(100vw-1.5rem))] rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] group-hover:block">
              <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
                Ihtiyac Listesi
              </p>
              <div className="mt-2 space-y-1">
                {items.length === 0 ? (
                  <p className="text-[#6b655c]">Liste bos.</p>
                ) : (
                  items.slice(0, 5).map((item) => (
                    <p key={`need-preview-${item.key}`} className="text-[#334155]">
                      {item.name}
                    </p>
                  ))
                )}
              </div>
              <button
                type="button"
                className="mt-2 rounded-lg border border-black/10 bg-[#f9f4ee] px-2 py-1 text-[11px] text-[#6b655c]"
                onClick={() => {
                  window.location.href = "/needs";
                }}
              >
                Tum Listeyi Gor
              </button>
            </div>
          </div>,
          headerActionsEl
        )}
      <ProductInfoModal
        isOpen={selectedPreviewItem !== null}
        product={
          selectedPreviewItem
            ? {
                name: selectedPreviewItem.name,
                imageUrl: selectedPreviewItem.imageUrl,
                brandName: selectedPreviewItem.brandName || "Marka yok",
                marketplaceCode: selectedPreviewItem.marketplaceCode || "",
              }
            : null
        }
        onCloseAction={() => {
          setSelectedPreviewItem(null);
          setSelectedProductHistory([]);
          setHistoryBusy(false);
          setHistoryMarketplaceFilter("ALL");
          setHistoryRangeFilter("1M");
          setHoveredHistoryPoint(null);
        }}
        priceLabel={`Guncel fiyat: ${formatPrice(selectedPreviewItem?.price ?? null)}`}
        showMoneyBadge={
          selectedPreviewItem?.marketplaceCode === "MG" &&
          selectedPreviewItem.moneyPrice !== null
        }
        moneyBadgeText={formatPrice(selectedPreviewItem?.moneyPrice ?? null)}
        showBasketDiscount={
          selectedPreviewItem?.marketplaceCode === "MG" &&
          selectedPreviewItem?.basketDiscountThreshold !== null &&
          selectedPreviewItem?.basketDiscountPrice !== null
        }
        basketDiscountThresholdText={formatTl(selectedPreviewItem?.basketDiscountThreshold ?? null, 0)}
        basketDiscountPriceText={formatTl(selectedPreviewItem?.basketDiscountPrice ?? null)}
        showEffectiveCampaign={
          selectedPreviewItem?.marketplaceCode === "MG" &&
          selectedPreviewItem?.campaignBuyQuantity !== null &&
          selectedPreviewItem?.campaignPayQuantity !== null &&
          selectedPreviewItem.campaignBuyQuantity > selectedPreviewItem.campaignPayQuantity &&
          selectedPreviewItem?.effectivePrice !== null
        }
        effectiveCampaignText={
          selectedPreviewItem &&
          selectedPreviewItem.campaignBuyQuantity !== null &&
          selectedPreviewItem.campaignPayQuantity !== null &&
          selectedPreviewItem.effectivePrice !== null
            ? `${selectedPreviewItem.campaignBuyQuantity} al ${selectedPreviewItem.campaignPayQuantity} ode - Efektif ${selectedPreviewItem.effectivePrice.toFixed(2)} TL`
            : null
        }
        selectedOpportunityLevel={selectedOpportunityLevel}
        todayRecommendationLabel={selectedTodayRecommendation.label}
        todayRecommendationReason={selectedTodayRecommendation.reason}
        selectedNeedUrgency={selectedNeedUrgency}
        urgencyLabelAction={urgencyLabel}
        onSelectNeedUrgencyAction={setSelectedNeedUrgency}
        onAddSelectedToNeedListAction={() => undefined}
        selectedInNeedList
        showNeedActions={false}
        showOnlySelectedMarketplace
        showAddCategoryButton={false}
        onAddActiveCategoryToNeedListAction={() => undefined}
        historyCountLabel={`${rangeFilteredHistory.length}/${compactHistory.length} kayit`}
        historyContent={historyContent}
      />
    </div>
  );
}
