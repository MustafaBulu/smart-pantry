"use client";

import { useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import Image from "next/image";
import { createPortal } from "react-dom";
import { request, requestForMarketplace } from "@/lib/api";
import type {
  BasketMinimumSettingsResponse,
  MarketplaceProductAddedResponse,
  MarketplaceProductEntryResponse,
  NeedListItemDto,
  PriceHistoryResponse,
} from "@/lib/types";
import ProductInfoModal from "@/components/ProductInfoModal";
import HistoryChart, { type HoveredHistoryPoint } from "@/features/basket/components/HistoryChart";
import NeedSuggestionsSection from "@/features/basket/components/NeedSuggestionsSection";
import AutoSuggestionsSection from "@/features/basket/components/AutoSuggestionsSection";

type NeedItem = {
  key: string;
  type?: NeedItemType;
  categoryId: number;
  categoryName?: string;
  name: string;
  externalId?: string | null;
  imageUrl: string;
  marketplaceCode: MarketplaceCode | null;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  urgency: NeedUrgency;
  availabilityScore?: number | null;
  historyDayCount: number | null;
  availabilityStatus: AvailabilityStatus;
  opportunityLevel: string | null;
};

type MarketplaceCode = "YS" | "MG";
type NeedItemType = "PRODUCT" | "CATEGORY";
type NeedUrgency = "VERY_URGENT" | "URGENT" | "NOT_URGENT";
type AvailabilityStatus = "Uygun" | "Normal" | "Pahali";

type OpportunityItem = {
  key: string;
  categoryId: number;
  name: string;
  availabilityScore: number | null;
  availabilityStatus: AvailabilityStatus;
  opportunityLevel: string;
};

type OpportunityDisplayItem = OpportunityItem & {
  imageUrl: string;
  marketplaceCode: MarketplaceCode | null;
  displayPrice: number | null;
  mgAveragePrice: number | null;
  ysAveragePrice: number | null;
  selectedAveragePrice: number | null;
  selectedDiscountRatio: number | null;
};
type CrossPlatformDiffNotice = {
  item: NeedItem;
  cheaperMarketplace: MarketplaceCode;
  expensiveMarketplace: MarketplaceCode;
  diff: number;
};

type HistoryRangeFilter = "1M" | "3M" | "1Y";

type HistoryPoint = {
  recordedAt: string;
  marketplaceCode: string;
  price: number;
  availabilityScore: number | null;
  opportunityLevel: string | null;
};

type BasketPreviewItem = {
  productId: number | null;
  externalId: string | null;
  categoryId: number;
  name: string;
  imageUrl: string;
  brandName: string | null;
  marketplaceCode: MarketplaceCode | null;
  price: number | null;
  basePrice: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  urgency: NeedUrgency | null;
  availabilityStatus: NeedItem["availabilityStatus"] | null;
  opportunityLevel: string | null;
};

const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";
const OPPORTUNITY_FEED_STORAGE_KEY = "smart-pantry:opportunity-feed";
const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const DEFAULT_MIGROS_BASKET_THRESHOLD = 50;
const toMarketplaceCode = (value: string | null | undefined): MarketplaceCode | null => {
  if (value === "YS" || value === "MG") {
    return value;
  }
  return null;
};

const readInitialOpportunityFeed = (): OpportunityItem[] => [];
const readInitialUserSettings = () => {
  if (globalThis.window === undefined) {
    return {
      migrosMoneyMember: false,
      ysMinimumBasketAmount: null as number | null,
      mgMinimumBasketAmount: null as number | null,
      considerEffectivePricing: false,
    };
  }
  try {
    const raw = globalThis.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
    const parsed = (raw === null ? {} : JSON.parse(raw)) as {
      migrosMoneyMember?: boolean;
      ysMinimumBasketAmount?: number;
      mgMinimumBasketAmount?: number;
      considerEffectivePricing?: boolean;
    };
    return {
      migrosMoneyMember: Boolean(parsed.migrosMoneyMember),
      ysMinimumBasketAmount:
        typeof parsed.ysMinimumBasketAmount === "number" ? parsed.ysMinimumBasketAmount : null,
      mgMinimumBasketAmount:
        typeof parsed.mgMinimumBasketAmount === "number" ? parsed.mgMinimumBasketAmount : null,
      considerEffectivePricing: Boolean(parsed.considerEffectivePricing),
    };
  } catch {
    return {
      migrosMoneyMember: false,
      ysMinimumBasketAmount: null as number | null,
      mgMinimumBasketAmount: null as number | null,
      considerEffectivePricing: false,
    };
  }
};

const resolveThresholdPrice = (
  item: NeedItem,
  considerEffectivePricing: boolean,
  migrosMoneyMember: boolean
) => {
  if (item.marketplaceCode !== "MG") {
    return item.price;
  }
  if (
    considerEffectivePricing &&
    item.effectivePrice !== null
  ) {
    return item.effectivePrice;
  }
  const options = [
    item.price,
    migrosMoneyMember ? item.moneyPrice : null,
    item.basketDiscountPrice,
  ].filter(
    (value): value is number => value !== null
  );
  if (options.length === 0) {
    return null;
  }
  return Math.min(...options);
};

const resolveMarketplaceProductPrice = (
  item: MarketplaceProductEntryResponse,
  considerEffectivePricing: boolean
) => {
  if (item.marketplaceCode === "MG" && considerEffectivePricing && item.effectivePrice !== null) {
    return item.effectivePrice;
  }
  const options = [item.price, item.moneyPrice, item.basketDiscountPrice].filter(
    (value): value is number => value !== null
  );
  if (options.length === 0) {
    return null;
  }
  return Math.min(...options);
};

const hasEffectiveCampaign = (item: NeedItem) =>
  item.marketplaceCode === "MG" &&
  item.campaignBuyQuantity !== null &&
  item.campaignPayQuantity !== null &&
  item.campaignBuyQuantity > item.campaignPayQuantity &&
  item.effectivePrice !== null;

const hasBasketDiscount = (
  marketplaceCode: "YS" | "MG" | null,
  basketDiscountThreshold: number | null,
  basketDiscountPrice: number | null
) =>
  marketplaceCode === "MG" &&
  basketDiscountThreshold !== null &&
  basketDiscountThreshold > 0 &&
  basketDiscountPrice !== null &&
  basketDiscountPrice > 0;

const formatPrice = (value: number | null) => (value === null ? "-" : `${value.toFixed(2)} TL`);
const formatTl = (value: number | null, digits = 2) => (value === null ? "-" : `${value.toFixed(digits)} TL`);
const resolveAvailabilityStatus = (score: number | null): AvailabilityStatus => {
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

const urgencyLabel = (urgency: NeedItem["urgency"]) => {
  if (urgency === "VERY_URGENT") {
    return "Acil";
  }
  if (urgency === "URGENT") {
    return "Normal";
  }
  return "Acil Degil";
};

const marketplaceLabel = (marketplaceCode: MarketplaceCode | null) => {
  if (marketplaceCode === "MG") {
    return "Migros";
  }
  return "Yemeksepeti";
};

const toNeedItems = (rows: NeedListItemDto[]): NeedItem[] =>
  rows.map((row) => ({ ...row }));

const isNeedListItemDto = (value: unknown): value is NeedListItemDto =>
  typeof value === "object" && value !== null;

const toNeedItemsFromUnknown = (value: unknown): NeedItem[] => {
  if (!Array.isArray(value)) {
    return [];
  }
  return toNeedItems(value.filter(isNeedListItemDto));
};

const normalizeName = (value: string) =>
  value
    .toLowerCase()
    .replaceAll(/[^a-z0-9\sçğıöşü]/gi, " ")
    .replaceAll(/\s+/g, " ")
    .trim();

const nameSimilarity = (left: string, right: string) => {
  const l = normalizeName(left);
  const r = normalizeName(right);
  if (l.length === 0 || r.length === 0) {
    return 0;
  }
  if (l === r) {
    return 1;
  }
  if (l.includes(r) || r.includes(l)) {
    return 0.85;
  }
  const lTokens = new Set(l.split(" ").filter((token) => token.length > 2));
  const rTokens = new Set(r.split(" ").filter((token) => token.length > 2));
  if (lTokens.size === 0 || rTokens.size === 0) {
    return 0;
  }
  let intersection = 0;
  lTokens.forEach((token) => {
    if (rTokens.has(token)) {
      intersection += 1;
    }
  });
  const union = lTokens.size + rTokens.size - intersection;
  return union === 0 ? 0 : intersection / union;
};

type QuantityInfo = {
  amount: number | null;
  unit: "g" | "ml" | null;
  packCount: number | null;
};

const parseQuantityInfo = (name: string): QuantityInfo => {
  const lower = name.toLocaleLowerCase("tr-TR");
  const packMatch = lower.match(/(\d+)\s*(li|lu|pack|paket)\b/);
  const amountMatch = lower.match(/(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|lt|l)\b/);
  let amount: number | null = null;
  let unit: "g" | "ml" | null = null;
  if (amountMatch) {
    const rawAmount = Number.parseFloat(amountMatch[1].replace(",", "."));
    const rawUnit = amountMatch[2];
    if (!Number.isNaN(rawAmount)) {
      if (rawUnit === "kg") {
        amount = rawAmount * 1000;
        unit = "g";
      } else if (rawUnit === "gr" || rawUnit === "g") {
        amount = rawAmount;
        unit = "g";
      } else if (rawUnit === "lt" || rawUnit === "l") {
        amount = rawAmount * 1000;
        unit = "ml";
      } else if (rawUnit === "ml") {
        amount = rawAmount;
        unit = "ml";
      }
    }
  }
  return {
    amount,
    unit,
    packCount: packMatch ? Number.parseInt(packMatch[1], 10) : null,
  };
};

const quantityCompatibilityScore = (leftName: string, rightName: string) => {
  const left = parseQuantityInfo(leftName);
  const right = parseQuantityInfo(rightName);
  if (left.packCount !== null && right.packCount !== null && left.packCount !== right.packCount) {
    return -1;
  }
  if (left.unit && right.unit) {
    if (left.unit !== right.unit) {
      return -1;
    }
    if (left.amount !== null && right.amount !== null) {
      const ratio = Math.min(left.amount, right.amount) / Math.max(left.amount, right.amount);
      if (ratio < 0.8) {
        return -1;
      }
      if (ratio >= 0.95) {
        return 1;
      }
      if (ratio >= 0.9) {
        return 0.8;
      }
      return 0.6;
    }
  }
  if (left.packCount !== null && right.packCount !== null) {
    return 0.8;
  }
  return 0.4;
};

const bestMarketplaceMatch = (
  products: MarketplaceProductEntryResponse[],
  sourceName: string
) => {
  const sourceNormalized = normalizeName(sourceName);
  const scored = products
    .map((product) => {
      const targetName = product.name ?? "";
      const targetNormalized = normalizeName(targetName);
      const base = sourceNormalized === targetNormalized ? 1 : nameSimilarity(targetName, sourceName);
      const quantityScore = quantityCompatibilityScore(sourceName, targetName);
      if (quantityScore < 0) {
        return { product, score: -1 };
      }
      const brand = normalizeName(product.brandName ?? "");
      const brandBonus =
        brand.length > 0 && sourceNormalized.includes(brand) ? 0.12 : 0;
      return {
        product,
        score: base + quantityScore * 0.2 + brandBonus,
      };
    })
    .filter((entry) => entry.score >= 0)
    .sort((left, right) => right.score - left.score);
  return scored[0]?.product ?? null;
};

const parseMarketplaceFromOpportunityKey = (key: string): MarketplaceCode | null => {
  const parts = key.split(":");
  if (parts.length < 3) {
    return null;
  }
  return toMarketplaceCode(parts[1]);
};

const formatAvailabilityScore = (value: number | null) => {
  if (typeof value === "number") {
    return value.toFixed(1);
  }
  return "-";
};

export default function BasketPage() {
  const initialUserSettings = readInitialUserSettings();
  const [needList, setNeedList] = useState<NeedItem[]>([]);
  const [opportunities, setOpportunities] = useState<OpportunityItem[]>(() => readInitialOpportunityFeed());
  const [migrosMoneyMember, setMigrosMoneyMember] = useState(initialUserSettings.migrosMoneyMember);
  const [ysMinimumBasketAmount, setYsMinimumBasketAmount] = useState<number | null>(
    initialUserSettings.ysMinimumBasketAmount
  );
  const [mgMinimumBasketAmount, setMgMinimumBasketAmount] = useState<number | null>(
    initialUserSettings.mgMinimumBasketAmount
  );
  const [considerEffectivePricing, setConsiderEffectivePricing] = useState(
    initialUserSettings.considerEffectivePricing
  );
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const [settingsLoaded, setSettingsLoaded] = useState(false);
  const [selectedPreviewItem, setSelectedPreviewItem] = useState<BasketPreviewItem | null>(null);
  const [selectedNeedUrgency, setSelectedNeedUrgency] = useState<"VERY_URGENT" | "URGENT" | "NOT_URGENT">(
    "NOT_URGENT"
  );
  const [selectedProductHistory, setSelectedProductHistory] = useState<PriceHistoryResponse[]>([]);
  const [historyBusy, setHistoryBusy] = useState(false);
  const [historyMarketplaceFilter, setHistoryMarketplaceFilter] = useState<"ALL" | "YS" | "MG">("ALL");
  const [historyRangeFilter, setHistoryRangeFilter] = useState<HistoryRangeFilter>("1M");
  const [hoveredHistoryPoint, setHoveredHistoryPoint] = useState<HoveredHistoryPoint | null>(null);
  const [categoryProductsById, setCategoryProductsById] = useState<
    Record<number, MarketplaceProductEntryResponse[]>
  >({});

  useEffect(() => {
    let cancelled = false;
    void request<BasketMinimumSettingsResponse>("/settings/basket-minimums").then((settings) => {
        if (cancelled) {
          return;
        }
        setYsMinimumBasketAmount(settings.ysMinimumBasketAmount);
        setMgMinimumBasketAmount(settings.mgMinimumBasketAmount);
      }).catch(() => undefined);

    void request<NeedListItemDto[]>("/needs")
      .then((rows) => {
        if (cancelled) {
          return;
        }
        setNeedList(Array.isArray(rows) ? toNeedItems(rows) : []);
      })
      .catch(() => {
        try {
          const raw = globalThis.localStorage.getItem(NEED_LIST_STORAGE_KEY);
          const parsed = raw === null ? [] : (JSON.parse(raw) as unknown);
          setNeedList(toNeedItemsFromUnknown(parsed));
        } catch {
          setNeedList([]);
        }
      })
      .finally(() => {
        if (cancelled) {
          return;
        }
        setSettingsLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadGeneralOpportunities = async () => {
      if (cancelled) {
        return;
      }
      setOpportunities([]);
      try {
        const [ysAll, mgAll] = await Promise.allSettled([
          requestForMarketplace<MarketplaceProductAddedResponse[]>(
            "YS",
            "/categories/marketplace-products/added"
          ),
          requestForMarketplace<MarketplaceProductAddedResponse[]>(
            "MG",
            "/categories/marketplace-products/added"
          ),
        ]);
        const ysRows = ysAll.status === "fulfilled" && Array.isArray(ysAll.value) ? ysAll.value : [];
        const mgRows = mgAll.status === "fulfilled" && Array.isArray(mgAll.value) ? mgAll.value : [];
        const allProducts = [...ysRows, ...mgRows];
        const collected: OpportunityItem[] = [];
        const seenKeys = new Set<string>();
        const targets = allProducts.filter(
          (item): item is MarketplaceProductAddedResponse & { productId: number } =>
            item.productId !== null &&
            (item.marketplaceCode === "YS" || item.marketplaceCode === "MG")
        );
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, "0");
        const dd = String(today.getDate()).padStart(2, "0");
        const todayKey = `${yyyy}-${mm}-${dd}`;
        const scoredResults = await Promise.allSettled(
          targets.map(async (target) => {
            const marketplaceCode = toMarketplaceCode(target.marketplaceCode);
            if (!marketplaceCode) {
              return null;
            }
            const query = new URLSearchParams({
              marketplaceCode,
              useMoneyPrice: "false",
              useEffectivePrice: "false",
            });
            const history = await requestForMarketplace<PriceHistoryResponse[]>(
              marketplaceCode,
              `/products/${target.productId}/prices?${query.toString()}`
            );
            const rows = Array.isArray(history) ? history : [];
            if (rows.length === 0) {
              return null;
            }
            const todaysRows = rows.filter((row) => row.recordedAt === todayKey);
            const sourceRows = todaysRows.length > 0 ? todaysRows : rows;
            const latestRow = [...sourceRows]
              .map((row, index) => ({
                row,
                ts: Number.isFinite(Date.parse(row.recordedAt)) ? Date.parse(row.recordedAt) : index,
              }))
              .sort((left, right) => right.ts - left.ts)[0]?.row;
            const availabilityScore = latestRow?.availabilityScore ?? null;
            return {
              target,
              availabilityScore,
              availabilityStatus: resolveAvailabilityStatus(availabilityScore),
              opportunityLevel: latestRow?.opportunityLevel ?? "Normal",
            };
          })
        );
        for (const result of scoredResults) {
          if (result.status !== "fulfilled" || !result.value) {
            continue;
          }
          const { target, availabilityScore, availabilityStatus, opportunityLevel } = result.value;
          const key = `${target.categoryId}:${target.marketplaceCode}:${target.externalId}`;
          if (seenKeys.has(key)) {
            continue;
          }
          seenKeys.add(key);
          collected.push({
            key,
            categoryId: target.categoryId,
            name: target.name || `Urun ${target.externalId}`,
            availabilityScore,
            availabilityStatus,
            opportunityLevel,
          });
        }
        if (cancelled) {
          return;
        }
        const scoreOf = (item: OpportunityItem) =>
          typeof item.availabilityScore === "number" ? item.availabilityScore : -1;
        const ranked = [...collected].sort(
          (left, right) => scoreOf(right) - scoreOf(left)
        );
        setOpportunities(ranked);
        globalThis.localStorage.setItem(OPPORTUNITY_FEED_STORAGE_KEY, JSON.stringify(ranked));
      } catch {
        // No-op: general opportunities are best-effort.
      }
    };

    if (settingsLoaded) {
      void loadGeneralOpportunities();
      return () => {
        cancelled = true;
      };
    }
    return () => {
      cancelled = true;
    };
  }, [settingsLoaded]);

  useEffect(() => {
    try {
      const raw = globalThis.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
      const parsed =
        raw === null || raw.trim().length === 0
          ? ({} as Record<string, unknown>)
          : (JSON.parse(raw) as Record<string, unknown>);
      globalThis.localStorage.setItem(
        USER_SETTINGS_STORAGE_KEY,
        JSON.stringify({
          ...parsed,
          migrosMoneyMember,
          considerEffectivePricing,
        })
      );
    } catch {
      // No-op: local settings persistence is best-effort.
    }
  }, [considerEffectivePricing, migrosMoneyMember]);

  useEffect(() => {
    let cancelled = false;
    const categoryIds = Array.from(
      new Set([...needList.map((item) => item.categoryId), ...opportunities.map((item) => item.categoryId)])
    ).filter((id) => Number.isFinite(id) && id > 0);
    if (categoryIds.length === 0) {
      return;
    }
    void Promise.all(
      categoryIds.map(async (categoryId) => {
        const [ysRows, mgRows] = await Promise.all([
          requestForMarketplace<MarketplaceProductEntryResponse[]>(
            "YS",
            `/categories/${categoryId}/marketplace-products/added`
          ),
          requestForMarketplace<MarketplaceProductEntryResponse[]>(
            "MG",
            `/categories/${categoryId}/marketplace-products/added`
          ),
        ]);
        const rows = [...ysRows, ...mgRows];
        return { categoryId, rows };
      })
    )
      .then((entries) => {
        if (cancelled) {
          return;
        }
        const next: Record<number, MarketplaceProductEntryResponse[]> = {};
        entries.forEach((entry) => {
          next[entry.categoryId] = entry.rows;
        });
        setCategoryProductsById(next);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [needList, opportunities]);

  const resolveMarketplaceDetail = (
    categoryId: number,
    name: string,
    marketplaceCode: "YS" | "MG" | null
  ) => {
    const categoryProducts = categoryProductsById[categoryId] ?? [];
    const scoped = marketplaceCode
      ? categoryProducts.filter((item) => item.marketplaceCode === marketplaceCode)
      : categoryProducts;
    if (scoped.length === 0) {
      return null;
    }
    return bestMarketplaceMatch(scoped, name);
  };

  const previewFromNeed = (item: NeedItem): BasketPreviewItem => {
    const detail = resolveMarketplaceDetail(item.categoryId, item.name, item.marketplaceCode);
    return {
      productId: detail?.productId ?? null,
      externalId: detail?.externalId ?? item.externalId ?? null,
      categoryId: item.categoryId,
      name: detail?.name || item.name,
      imageUrl: detail?.imageUrl || item.imageUrl,
      brandName: detail?.brandName ?? null,
      marketplaceCode: item.marketplaceCode,
      price: resolveThresholdPrice(item, considerEffectivePricing, migrosMoneyMember),
      basePrice: detail?.price ?? item.price,
      moneyPrice: detail?.moneyPrice ?? item.moneyPrice,
      basketDiscountThreshold: detail?.basketDiscountThreshold ?? item.basketDiscountThreshold,
      basketDiscountPrice: detail?.basketDiscountPrice ?? item.basketDiscountPrice,
      campaignBuyQuantity: detail?.campaignBuyQuantity ?? item.campaignBuyQuantity,
      campaignPayQuantity: detail?.campaignPayQuantity ?? item.campaignPayQuantity,
      effectivePrice: detail?.effectivePrice ?? item.effectivePrice,
      urgency: item.urgency,
      availabilityStatus: item.availabilityStatus,
      opportunityLevel: item.opportunityLevel,
    };
  };

  const previewFromOpportunity = (item: OpportunityDisplayItem): BasketPreviewItem => {
    const detail = resolveMarketplaceDetail(item.categoryId, item.name, item.marketplaceCode);
    return {
      productId: detail?.productId ?? null,
      externalId: detail?.externalId ?? null,
      categoryId: item.categoryId,
      name: detail?.name || item.name,
      imageUrl: detail?.imageUrl || item.imageUrl,
      brandName: detail?.brandName ?? null,
      marketplaceCode: item.marketplaceCode,
      price: item.displayPrice,
      basePrice: detail?.price ?? null,
      moneyPrice: detail?.moneyPrice ?? null,
      basketDiscountThreshold: detail?.basketDiscountThreshold ?? null,
      basketDiscountPrice: detail?.basketDiscountPrice ?? null,
      campaignBuyQuantity: detail?.campaignBuyQuantity ?? null,
      campaignPayQuantity: detail?.campaignPayQuantity ?? null,
      effectivePrice: detail?.effectivePrice ?? null,
      urgency: null,
      availabilityStatus: item.availabilityStatus,
      opportunityLevel: item.opportunityLevel,
    };
  };
  const openPreview = (item: BasketPreviewItem) => {
    setSelectedProductHistory([]);
    setHistoryBusy(Boolean(item.productId && item.marketplaceCode));
    setHistoryMarketplaceFilter("ALL");
    setHistoryRangeFilter("1M");
    setHoveredHistoryPoint(null);
    setSelectedPreviewItem(item);
  };
  const closePreview = () => {
    setSelectedPreviewItem(null);
    setSelectedProductHistory([]);
    setHistoryBusy(false);
    setHistoryMarketplaceFilter("ALL");
    setHistoryRangeFilter("1M");
    setHoveredHistoryPoint(null);
  };

  useEffect(() => {
    let cancelled = false;
    const previewProductId = selectedPreviewItem?.productId;
    const previewMarketplaceCode = selectedPreviewItem?.marketplaceCode;
    if (
      previewProductId === null ||
      previewProductId === undefined ||
      previewMarketplaceCode === null ||
      previewMarketplaceCode === undefined
    ) {
      return;
    }
    const query = new URLSearchParams({
      marketplaceCode: previewMarketplaceCode,
      useMoneyPrice: "false",
      useEffectivePrice: "false",
    });
    void request<PriceHistoryResponse[]>(
      `/products/${previewProductId}/prices?${query.toString()}`
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
        if (cancelled) {
          return;
        }
        setHistoryBusy(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedPreviewItem]);

  const suggestedFromNeeds = useMemo(() => {
    const migrosBasketTotal = needList
      .filter((item) => item.marketplaceCode === "MG")
      .reduce((sum, item) => {
        const price = resolveThresholdPrice(item, considerEffectivePricing, migrosMoneyMember);
        return sum + (price ?? 0);
      }, 0);

    return needList.filter((item) => {
      const isProductMarketplace =
        item.marketplaceCode === "YS" || item.marketplaceCode === "MG";
      if (isProductMarketplace === false) {
        return false;
      }
      const campaignThreshold = item.basketDiscountThreshold ?? DEFAULT_MIGROS_BASKET_THRESHOLD;
      const campaignEligible =
        item.marketplaceCode === "MG" &&
        item.basketDiscountPrice !== null &&
        migrosBasketTotal >= campaignThreshold;
      const effectiveCampaignEligible = considerEffectivePricing && hasEffectiveCampaign(item);
      if (item.urgency === "VERY_URGENT") {
        const urgentEligible =
          typeof item.availabilityScore === "number"
            ? item.availabilityScore >= 35
            : item.availabilityStatus !== "Pahali";
        return urgentEligible || campaignEligible || effectiveCampaignEligible;
      }
      if (item.urgency === "URGENT") {
        const normalEligible =
          typeof item.availabilityScore === "number"
            ? item.availabilityScore >= 50
            : item.availabilityStatus !== "Pahali";
        return normalEligible || campaignEligible || effectiveCampaignEligible;
      }
      const notUrgentEligible =
        typeof item.availabilityScore === "number"
          ? item.availabilityScore >= 70
          : item.availabilityStatus === "Uygun";
      return notUrgentEligible || campaignEligible || effectiveCampaignEligible;
    });
  }, [needList, considerEffectivePricing, migrosMoneyMember]);

  const optimizedNeedSuggestions = useMemo(() => {
    const groups: NeedItem[][] = [];
    const byMarketplace: { MG: NeedItem[]; YS: NeedItem[] } = { MG: [], YS: [] };
    const crossNotices: { MG: CrossPlatformDiffNotice[]; YS: CrossPlatformDiffNotice[] } = {
      MG: [],
      YS: [],
    };
    const pricedValue = (item: NeedItem) => {
      return resolveThresholdPrice(item, considerEffectivePricing, migrosMoneyMember) ?? Number.POSITIVE_INFINITY;
    };

    suggestedFromNeeds.forEach((item) => {
      const existing = groups.find((group) => {
        const sample = group[0];
        return (
          sample.categoryId === item.categoryId && nameSimilarity(sample.name, item.name) >= 0.82
        );
      });
      if (existing) {
        existing.push(item);
      } else {
        groups.push([item]);
      }
    });

    groups.forEach((group) => {
      const cheapest = [...group].sort((left, right) => pricedValue(left) - pricedValue(right))[0];
      if (cheapest === undefined) {
        return;
      }
      if (cheapest.marketplaceCode !== "YS" && cheapest.marketplaceCode !== "MG") {
        return;
      }
      byMarketplace[cheapest.marketplaceCode].push(cheapest);
      const cheapestPrice = pricedValue(cheapest);
      group.forEach((candidate) => {
        if (
          candidate.marketplaceCode !== "YS" &&
          candidate.marketplaceCode !== "MG"
        ) {
          return;
        }
        if (candidate.marketplaceCode === cheapest.marketplaceCode) {
          return;
        }
        const candidateMarketplace = toMarketplaceCode(candidate.marketplaceCode);
        const cheapestMarketplace = toMarketplaceCode(cheapest.marketplaceCode);
        if (candidateMarketplace === null || cheapestMarketplace === null) {
          return;
        }
        const candidatePrice = pricedValue(candidate);
        if (!Number.isFinite(cheapestPrice) || !Number.isFinite(candidatePrice)) {
          return;
        }
        const diff = Math.max(0, candidatePrice - cheapestPrice);
        crossNotices[candidateMarketplace].push({
          item: candidate,
          cheaperMarketplace: cheapestMarketplace,
          expensiveMarketplace: candidateMarketplace,
          diff,
        });
      });
    });

    crossNotices.MG.sort((left, right) => left.diff - right.diff);
    crossNotices.YS.sort((left, right) => left.diff - right.diff);

    return { byMarketplace, crossNotices };
  }, [suggestedFromNeeds, considerEffectivePricing, migrosMoneyMember]);

  const basketShortfall = useMemo(() => {
    const totals = { YS: 0, MG: 0 };
    [...optimizedNeedSuggestions.byMarketplace.MG, ...optimizedNeedSuggestions.byMarketplace.YS].forEach((item) => {
      if (item.marketplaceCode !== "YS" && item.marketplaceCode !== "MG") {
        return;
      }
      const price = resolveThresholdPrice(item, considerEffectivePricing, migrosMoneyMember) ?? 0;
      totals[item.marketplaceCode] += price;
    });
    const ysMin = ysMinimumBasketAmount ?? 0;
    const mgMin = mgMinimumBasketAmount ?? 0;
    return {
      ysShortfall: Math.max(0, ysMin - totals.YS),
      mgShortfall: Math.max(0, mgMin - totals.MG),
      ysTotal: totals.YS,
      mgTotal: totals.MG,
      ysMin,
      mgMin,
    };
  }, [optimizedNeedSuggestions, considerEffectivePricing, migrosMoneyMember, ysMinimumBasketAmount, mgMinimumBasketAmount]);

  const autoSuggestions = useMemo<OpportunityDisplayItem[]>(() => {
    const scoreOf = (item: OpportunityItem) =>
      typeof item.availabilityScore === "number" ? item.availabilityScore : -1;
    const resolveLocalDetail = (
      categoryId: number,
      name: string,
      marketplaceCode: MarketplaceCode | null
    ) => {
      const categoryProducts = categoryProductsById[categoryId] ?? [];
      const scoped = marketplaceCode
        ? categoryProducts.filter((product) => product.marketplaceCode === marketplaceCode)
        : categoryProducts;
      if (scoped.length === 0) {
        return null;
      }
      return bestMarketplaceMatch(scoped, name);
    };
    const mapped = [...opportunities]
      .sort((left, right) => scoreOf(right) - scoreOf(left))
      .filter((item) => typeof item.availabilityScore === "number" && item.availabilityScore > 60)
      .map((item) => {
        const marketplaceCode = parseMarketplaceFromOpportunityKey(item.key);
        const detail = resolveLocalDetail(item.categoryId, item.name, marketplaceCode);
        const displayPrice = detail ? resolveMarketplaceProductPrice(detail, true) : null;
        return {
          ...item,
          imageUrl: detail?.imageUrl ?? "",
          marketplaceCode,
          displayPrice,
          mgAveragePrice: null,
          ysAveragePrice: null,
          selectedAveragePrice: null,
          selectedDiscountRatio: null,
        };
      })
      .filter((item) => item.marketplaceCode !== null);
    const seen = new Set<string>();
    return mapped.filter((item) => {
      if (seen.has(item.key)) {
        return false;
      }
      seen.add(item.key);
      return true;
    });
  }, [opportunities, categoryProductsById]);
  const suggestedByMarketplace = useMemo(
    () => ({
      MG: optimizedNeedSuggestions.byMarketplace.MG,
      YS: optimizedNeedSuggestions.byMarketplace.YS,
    }),
    [optimizedNeedSuggestions]
  );
  const selectedOpportunityLevel = useMemo(() => {
    if (!selectedPreviewItem) {
      return "-";
    }
    const preferred = selectedProductHistory.find(
      (item) =>
        item.marketplaceCode === selectedPreviewItem.marketplaceCode &&
        item.opportunityLevel
    );
    if (preferred?.opportunityLevel) {
      return preferred.opportunityLevel;
    }
    const counts = new Map<string, number>();
    selectedProductHistory.forEach((item) => {
      if (!item.opportunityLevel) {
        return;
      }
      counts.set(item.opportunityLevel, (counts.get(item.opportunityLevel) ?? 0) + 1);
    });
    if (counts.size === 0) {
      return "-";
    }
    return Array.from(counts.entries()).sort((a, b) => b[1] - a[1])[0][0];
  }, [selectedPreviewItem, selectedProductHistory]);

  const selectedTodayRecommendation = useMemo(() => {
    if (!selectedPreviewItem) {
      return {
        label: "-",
        reason: "Secili urun yok.",
        availabilityStatus: "Normal" as NeedItem["availabilityStatus"],
      };
    }
    const latestPoint = [...selectedProductHistory]
      .filter((item) => item.marketplaceCode === selectedPreviewItem.marketplaceCode)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    const availabilityStatus = resolveAvailabilityStatus(latestPoint?.availabilityScore ?? null);
    const opportunityLevel = latestPoint?.opportunityLevel ?? selectedOpportunityLevel;
    const effectiveCampaignEligible =
      Boolean(considerEffectivePricing) &&
      selectedPreviewItem.marketplaceCode === "MG" &&
      selectedPreviewItem.campaignBuyQuantity !== null &&
      selectedPreviewItem.campaignPayQuantity !== null &&
      selectedPreviewItem.campaignBuyQuantity > selectedPreviewItem.campaignPayQuantity &&
      selectedPreviewItem.effectivePrice !== null;
    const shouldBuyToday =
      availabilityStatus === "Uygun" ||
      (availabilityStatus === "Normal" && opportunityLevel === "Yuksek") ||
      effectiveCampaignEligible;
    return {
      label: shouldBuyToday ? "Bugun Alinabilir" : "Bekletilebilir",
      reason: shouldBuyToday
        ? effectiveCampaignEligible
          ? "Efektif kampanya fiyati nedeniyle alinabilir."
          : `${availabilityStatus} fiyat seviyesi ve firsat durumu uygun.`
        : `${availabilityStatus} fiyat seviyesi nedeniyle beklemek daha iyi olabilir.`,
      availabilityStatus,
    };
  }, [selectedPreviewItem, selectedProductHistory, selectedOpportunityLevel, considerEffectivePricing]);

  const selectedInNeedList = useMemo(() => {
    if (!selectedPreviewItem) {
      return false;
    }
    return needList.some((item) => item.categoryId === selectedPreviewItem.categoryId);
  }, [needList, selectedPreviewItem]);

  const persistNeedList = (next: NeedItem[]) => {
    globalThis.localStorage.setItem(NEED_LIST_STORAGE_KEY, JSON.stringify(next));
    void request<NeedListItemDto[]>("/needs", {
      method: "PUT",
      body: JSON.stringify(next),
    }).catch(() => undefined);
  };

  const handleAddSelectedToNeedList = () => {
    if (!selectedPreviewItem || !selectedPreviewItem.marketplaceCode) {
      return;
    }
    const historyForMarketplace = selectedProductHistory.filter(
      (item) => item.marketplaceCode === selectedPreviewItem.marketplaceCode
    );
    const historyDayCount = new Set(historyForMarketplace.map((item) => item.recordedAt)).size;
    const latestPoint = [...historyForMarketplace]
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    const availabilityScore = latestPoint?.availabilityScore ?? null;
    const availabilityStatus = resolveAvailabilityStatus(availabilityScore);
    const opportunityLevel = latestPoint?.opportunityLevel ?? null;
    const knownCategoryName =
      needList.find((item) => item.categoryId === selectedPreviewItem.categoryId)?.categoryName ??
      `Kategori ${selectedPreviewItem.categoryId}`;
    const nextItem: NeedItem = {
      key: `category:${selectedPreviewItem.categoryId}`,
      type: "PRODUCT",
      categoryId: selectedPreviewItem.categoryId,
      categoryName: knownCategoryName,
      externalId: selectedPreviewItem.externalId ?? null,
      marketplaceCode: selectedPreviewItem.marketplaceCode,
      name: selectedPreviewItem.name,
      imageUrl: selectedPreviewItem.imageUrl,
      price: selectedPreviewItem.basePrice ?? selectedPreviewItem.price,
      moneyPrice: selectedPreviewItem.moneyPrice,
      basketDiscountThreshold: selectedPreviewItem.basketDiscountThreshold,
      basketDiscountPrice: selectedPreviewItem.basketDiscountPrice,
      campaignBuyQuantity: selectedPreviewItem.campaignBuyQuantity,
      campaignPayQuantity: selectedPreviewItem.campaignPayQuantity,
      effectivePrice: selectedPreviewItem.effectivePrice,
      urgency: selectedNeedUrgency,
      availabilityScore,
      historyDayCount,
      availabilityStatus,
      opportunityLevel,
    };
    setNeedList((prev) => {
      const withoutCategory = prev.filter((item) => item.categoryId !== selectedPreviewItem.categoryId);
      const next = [nextItem, ...withoutCategory];
      persistNeedList(next);
      return next;
    });
  };

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
    selectedProductHistory.forEach((item) => {
      const key = `${item.recordedAt}:${item.marketplaceCode}`;
      const existing = grouped.get(key);
      if (existing) {
        existing.totalPrice += item.price;
        existing.count += 1;
        if (existing.availabilityScore === null && item.availabilityScore !== null) {
          existing.availabilityScore = item.availabilityScore;
        }
        if (existing.opportunityLevel === null && item.opportunityLevel !== null) {
          existing.opportunityLevel = item.opportunityLevel;
        }
        return;
      }
      grouped.set(key, {
        recordedAt: item.recordedAt,
        marketplaceCode: item.marketplaceCode,
        totalPrice: item.price,
        count: 1,
        availabilityScore: item.availabilityScore,
        opportunityLevel: item.opportunityLevel,
      });
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
    const statusBandIndex: Record<AvailabilityStatus, number> = {
      Pahali: 0,
      Normal: 1,
      Uygun: 2,
    };
    rangeFilteredHistory.forEach((point) => {
      const status = resolveAvailabilityStatus(point.availabilityScore ?? null);
      const target = baseBands[statusBandIndex[status]];
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

  let historyContent: ReactNode;
  if (historyBusy) {
    historyContent = <p className="mt-3 text-sm text-[#6b655c]">Yukleniyor...</p>;
  } else if (selectedProductHistory.length === 0) {
    historyContent = <p className="mt-3 text-sm text-[#6b655c]">Fiyat gecmisi yok.</p>;
  } else if (filteredHistory.length === 0) {
    historyContent = <p className="mt-3 text-sm text-[#6b655c]">Secili market icin kayit yok.</p>;
  } else if (rangeFilteredHistory.length === 0) {
    historyContent = <p className="mt-3 text-sm text-[#6b655c]">Secili zaman araliginda kayit yok.</p>;
  } else {
    historyContent = (
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
          <span className="h-0.5 w-4 border-t-2 border-dashed border-slate-400" />
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
        <div className="h-80 flex-1 lg:h-104">
          <HistoryChart
            rangeFilteredHistory={rangeFilteredHistory}
            hoveredHistoryPoint={hoveredHistoryPoint}
            onHoverChange={setHoveredHistoryPoint}
            resolveAvailabilityStatus={resolveAvailabilityStatus}
          />
        </div>
      </div>
    </div>
    );
  }

  const selectedProductModal = (() => {
    if (selectedPreviewItem === null) {
      return null;
    }
    return {
      name: selectedPreviewItem.name,
      imageUrl: selectedPreviewItem.imageUrl,
      brandName: selectedPreviewItem.brandName ?? "Marka yok",
      marketplaceCode: selectedPreviewItem.marketplaceCode ?? "",
    };
  })();
  const selectedEffectiveCampaignText = (() => {
    if (selectedPreviewItem === null) {
      return null;
    }
    if (
      selectedPreviewItem.campaignBuyQuantity === null ||
      selectedPreviewItem.campaignPayQuantity === null ||
      selectedPreviewItem.effectivePrice === null
    ) {
      return null;
    }
    return `${selectedPreviewItem.campaignBuyQuantity} al ${selectedPreviewItem.campaignPayQuantity} ode - Efektif ${selectedPreviewItem.effectivePrice.toFixed(2)} TL`;
  })();

  const marketplaceSuggestionCards = [
    {
      code: "MG" as const,
      title: "Migros",
      items: suggestedByMarketplace.MG,
      crossNotices: optimizedNeedSuggestions.crossNotices.MG,
      total: basketShortfall.mgTotal,
      min: basketShortfall.mgMin,
      shortfall: basketShortfall.mgShortfall,
      showMinimumSettings: true,
    },
    {
      code: "YS" as const,
      title: "Yemeksepeti",
      items: suggestedByMarketplace.YS,
      crossNotices: optimizedNeedSuggestions.crossNotices.YS,
      total: basketShortfall.ysTotal,
      min: basketShortfall.ysMin,
      shortfall: basketShortfall.ysShortfall,
      showMinimumSettings: false,
    },
  ];
  const mgMinimumLabel = mgMinimumBasketAmount === null ? "Bulunamadi" : `${mgMinimumBasketAmount.toFixed(2)} TL`;
  const mgShortfallLabel = mgMinimumBasketAmount === null ? "Bulunamadi" : `${basketShortfall.mgShortfall.toFixed(2)} TL`;
  const renderNeedSuggestionRow = (item: NeedItem, rowKey: string) => (
    <button
      type="button"
      key={rowKey}
      className="flex w-full items-center gap-2 rounded-lg border border-black/10 bg-white px-2 py-1.5 text-left transition hover:bg-amber-50"
      onClick={() => openPreview(previewFromNeed(item))}
    >
      <div className="relative h-9 w-9 overflow-hidden rounded-md border border-black/10 bg-[#f3f4f6]">
        {item.imageUrl ? (
          <Image src={item.imageUrl} alt={item.name} fill sizes="36px" className="object-cover" unoptimized />
        ) : null}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-[#14532d]">{item.name}</p>
      </div>
      <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
        {urgencyLabel(item.urgency)}
      </span>
      <p className="text-sm font-semibold text-[#111]">
        {(resolveThresholdPrice(item, considerEffectivePricing, migrosMoneyMember) ?? 0).toFixed(2)} TL
      </p>
    </button>
  );
  const renderCrossNoticeRow = (notice: CrossPlatformDiffNotice, rowKey: string) => (
    <button
      type="button"
      key={rowKey}
      className="flex w-full items-center gap-2 rounded-lg border border-black/10 bg-white px-2 py-1.5 text-left opacity-60 transition hover:bg-amber-50"
      onClick={() => openPreview(previewFromNeed(notice.item))}
    >
      <div className="relative h-9 w-9 overflow-hidden rounded-md border border-black/10 bg-[#f3f4f6]">
        {notice.item.imageUrl ? (
          <Image
            src={notice.item.imageUrl}
            alt={notice.item.name}
            fill
            sizes="36px"
            className="object-cover"
            unoptimized
          />
        ) : null}
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-[#14532d]">{notice.item.name}</p>
      </div>
      <span className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-[10px] font-semibold text-[#6b655c]">
        {notice.diff.toFixed(2)} TL daha pahali
      </span>
    </button>
  );
  const getNeedItemKeyPart = (item: NeedItem) => item.key;
  const getCrossNoticeKeyPart = (notice: CrossPlatformDiffNotice) =>
    `${notice.item.key}-${notice.cheaperMarketplace}`;
  const isClient = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false
  );
  const headerActionsEl = isClient ? document.getElementById("header-actions") : null;

  return (
    <div className="mx-auto w-full max-w-5xl overflow-x-hidden px-3 py-8 sm:px-4">
      {headerActionsEl &&
        createPortal(
          <div className="relative">
            <div className="flex items-start gap-2">
              <button
                type="button"
                className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
                title="Ayarlar"
                aria-label="Ayarlar"
                onClick={() => setShowSettingsMenu((prev) => !prev)}
              >
                {"\u2699\uFE0F"}
              </button>
              <button
                type="button"
                className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
                onClick={() => {
                  globalThis.location.href = "/needs";
                }}
                title="Ihtiyac Listesi"
              >
                {"\uD83D\uDCDD"}
              </button>
              <button
                type="button"
                className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-amber-50 text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)]"
                title="Sepet Onerileri"
              >
                {"\uD83D\uDED2"}
              </button>
            </div>
            <div
              className={`absolute right-0 top-12 w-[min(20rem,calc(100vw-1.5rem))] rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] ${
                showSettingsMenu ? "block" : "hidden"
              }`}
            >
              <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
                Ayarlar
              </p>
              <div className="mt-2 flex flex-col gap-2">
                <div className="flex items-center justify-between gap-3 rounded-2xl border border-black/10 bg-[#f9f4ee] px-2 py-1.5">
                  <span className="px-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-[#6b655c]">
                    Money Uyelik
                  </span>
                  <div className="inline-flex items-center rounded-xl border border-black/10 bg-white p-0.5">
                    <button
                      type="button"
                      className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                        !migrosMoneyMember
                          ? "bg-[#f3f4f6] text-[#111] shadow-sm"
                          : "text-[#6b655c]"
                      }`}
                      onClick={() => setMigrosMoneyMember(false)}
                    >
                      Pasif
                    </button>
                    <button
                      type="button"
                      className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                        migrosMoneyMember
                          ? "bg-[#d97706] text-white shadow-sm"
                          : "text-[#6b655c]"
                      }`}
                      onClick={() => setMigrosMoneyMember(true)}
                    >
                      Aktif
                    </button>
                  </div>
                </div>
                <div className="flex items-center justify-between gap-3 rounded-2xl border border-black/10 bg-[#f9f4ee] px-2 py-1.5">
                  <span className="px-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-[#6b655c]">
                    Efektif Fiyat
                  </span>
                  <div className="inline-flex items-center rounded-xl border border-black/10 bg-white p-0.5">
                    <button
                      type="button"
                      className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                        !considerEffectivePricing
                          ? "bg-[#f3f4f6] text-[#111] shadow-sm"
                          : "text-[#6b655c]"
                      }`}
                      onClick={() => setConsiderEffectivePricing(false)}
                    >
                      Pasif
                    </button>
                    <button
                      type="button"
                      className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                        considerEffectivePricing
                          ? "bg-emerald-600 text-white shadow-sm"
                          : "text-[#6b655c]"
                      }`}
                      onClick={() => setConsiderEffectivePricing(true)}
                    >
                      Aktif
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>,
          headerActionsEl
        )}

      <div className="rounded-3xl border border-black/10 bg-white p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <h1 className="display text-3xl">Sepet Onerileri</h1>
        <p className="mt-1 text-sm text-[#6b655c]">
          Ihtiyac ve genel firsat sinyallerinden uretilen alisveris onceligi.
        </p>

        <NeedSuggestionsSection
          cards={marketplaceSuggestionCards}
          settingsLoaded={settingsLoaded}
          mgMinimumLabel={mgMinimumLabel}
          mgShortfallLabel={mgShortfallLabel}
          renderNeedSuggestionRow={renderNeedSuggestionRow}
          renderCrossNoticeRow={renderCrossNoticeRow}
          getNeedItemKeyPart={getNeedItemKeyPart}
          getCrossNoticeKeyPart={getCrossNoticeKeyPart}
        />

        <AutoSuggestionsSection
          items={autoSuggestions}
          onOpen={(item) => openPreview(previewFromOpportunity(item))}
          marketplaceLabel={marketplaceLabel}
          formatAvailabilityScore={formatAvailabilityScore}
          formatPrice={formatPrice}
        />
        <ProductInfoModal
          isOpen={selectedPreviewItem !== null}
          product={selectedProductModal}
          onCloseAction={closePreview}
          priceLabel={`Guncel fiyat: ${formatPrice(selectedPreviewItem?.price ?? null)}`}
          showMoneyBadge={
            selectedPreviewItem?.marketplaceCode === "MG" &&
            selectedPreviewItem.moneyPrice !== null &&
            migrosMoneyMember &&
            !considerEffectivePricing
          }
          moneyBadgeText={formatPrice(selectedPreviewItem?.moneyPrice ?? null)}
          showBasketDiscount={hasBasketDiscount(
            selectedPreviewItem?.marketplaceCode ?? null,
            selectedPreviewItem?.basketDiscountThreshold ?? null,
            selectedPreviewItem?.basketDiscountPrice ?? null
          )}
          basketDiscountThresholdText={formatTl(selectedPreviewItem?.basketDiscountThreshold ?? null, 0)}
          basketDiscountPriceText={formatTl(selectedPreviewItem?.basketDiscountPrice ?? null)}
          showEffectiveCampaign={
            selectedPreviewItem?.marketplaceCode === "MG" &&
            selectedPreviewItem.campaignBuyQuantity !== null &&
            selectedPreviewItem.campaignPayQuantity !== null &&
            selectedPreviewItem.campaignBuyQuantity > selectedPreviewItem.campaignPayQuantity &&
            selectedPreviewItem.effectivePrice !== null
          }
          effectiveCampaignText={selectedEffectiveCampaignText}
          selectedOpportunityLevel={selectedOpportunityLevel}
          todayRecommendationLabel={selectedTodayRecommendation.label}
          todayRecommendationReason={selectedTodayRecommendation.reason}
          selectedNeedUrgency={selectedNeedUrgency}
          urgencyLabelAction={urgencyLabel}
          onSelectNeedUrgencyAction={setSelectedNeedUrgency}
          onAddSelectedToNeedListAction={handleAddSelectedToNeedList}
          selectedInNeedList={selectedInNeedList}
          showAddCategoryButton={false}
          onAddActiveCategoryToNeedListAction={() => undefined}
          historyCountLabel={`${rangeFilteredHistory.length}/${compactHistory.length} kayit`}
          historyContent={historyContent}
        />
      </div>
    </div>
  );
}

