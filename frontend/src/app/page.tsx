﻿﻿﻿﻿"use client";

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
type UserSettings = {
  migrosMoneyMember: boolean;
  considerEffectivePricing: boolean;
  minimumBasketAmount: number | null;
  deliveryFee: number | null;
};
type HistoryPoint = {
  recordedAt: string;
  price: number;
  marketplaceCode: string;
  availabilityScore: number | null;
  opportunityLevel: string | null;
};
type NeedUrgency = "VERY_URGENT" | "URGENT" | "NOT_URGENT";
type AvailabilityStatus = "Uygun" | "Normal" | "Pahali";
type NeedItemType = "PRODUCT" | "CATEGORY";
type NeedListItem = {
  key: string;
  type: NeedItemType;
  categoryId: number;
  categoryName: string;
  externalId: string | null;
  marketplaceCode: MarketplaceCode | null;
  name: string;
  imageUrl: string;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  urgency: NeedUrgency;
  availabilityScore: number | null;
  availabilityStatus: AvailabilityStatus;
  opportunityLevel: string | null;
};
type OpportunityItem = {
  key: string;
  categoryId: number;
  categoryName: string;
  externalId: string;
  marketplaceCode: MarketplaceCode;
  name: string;
  price: number | null;
  availabilityStatus: AvailabilityStatus;
  opportunityLevel: string;
};
type HistoryMarketplaceFilter = "ALL" | MarketplaceCode;
type HistoryRangeFilter = "1M" | "3M" | "1Y";
type HoveredHistoryPoint = {
  x: number;
  y: number;
  point: HistoryPoint;
};

const MARKETPLACES: { code: MarketplaceCode; name: string; iconSrc: string }[] = [
  { code: "YS", name: "Yemeksepeti", iconSrc: "/yemeksepeti-logo.png" },
  { code: "MG", name: "Migros", iconSrc: "/migros-logo.png" },
];

const CATEGORY_VISIBLE_COUNT = 3;
const LIST_VISIBLE_COUNT = 6;
const CANDIDATE_DRAG_TYPE = "application/x-smart-pantry-candidate";
const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";
const OPPORTUNITY_FEED_STORAGE_KEY = "smart-pantry:opportunity-feed";
const DEFAULT_MIGROS_BASKET_THRESHOLD = 50;
const DEFAULT_USER_SETTINGS: UserSettings = {
  migrosMoneyMember: false,
  considerEffectivePricing: false,
  minimumBasketAmount: null,
  deliveryFee: null,
};
const ADDED_PRODUCTS_DROPZONE_CLASS =
  "mt-2 space-y-2 rounded-2xl border-2 border-dashed border-[#374151] bg-[#fff4e0] p-2 shadow-[0_12px_30px_-20px_rgba(217,119,6,0.7)]";

const formatPriceSuffix = (price: number | null) => {
  if (price === null) {
    return "";
  }
  return ` - ${price.toFixed(2)} TL`;
};

const formatPrice = (price: number | null) => {
  if (price === null) {
    return "";
  }
  return `${price.toFixed(2)} TL`;
};

const formatTl = (value: number | null, fractionDigits = 2) => {
  if (value === null) {
    return "-";
  }
  return `${value.toLocaleString("tr-TR", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })} TL`;
};

const hasMoneyDiscount = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null
) => {
  if (marketplaceCode !== "MG" || moneyPrice === null) {
    return false;
  }
  if (price === null) {
    return true;
  }
  return moneyPrice < price;
};

const hasBasketDiscount = (
  marketplaceCode: string,
  basketDiscountThreshold: number | null,
  basketDiscountPrice: number | null
) =>
  marketplaceCode === "MG" &&
  basketDiscountThreshold !== null &&
  basketDiscountPrice !== null;

const resolveEffectivePriceValue = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  preferMoneyBasePrice: boolean
) => {
  if (effectivePrice !== null) {
    return effectivePrice;
  }
  const basePrice =
    preferMoneyBasePrice && moneyPrice !== null ? moneyPrice : (price ?? moneyPrice);
  if (
    basePrice === null ||
    campaignBuyQuantity === null ||
    campaignPayQuantity === null ||
    campaignBuyQuantity <= campaignPayQuantity ||
    campaignBuyQuantity <= 0
  ) {
    return null;
  }
  return Number(((basePrice * campaignPayQuantity) / campaignBuyQuantity).toFixed(2));
};

const hasEffectiveCampaign = (
  marketplaceCode: string | null,
  price: number | null,
  moneyPrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  effectivePrice: number | null
) =>
  marketplaceCode === "MG" &&
  campaignBuyQuantity !== null &&
  campaignPayQuantity !== null &&
  campaignBuyQuantity > campaignPayQuantity &&
  resolveEffectivePriceValue(
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    true
  ) !== null;

const formatEffectiveCampaignBadge = (
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null
) => {
  if (
    campaignBuyQuantity === null ||
    campaignPayQuantity === null ||
    campaignBuyQuantity <= campaignPayQuantity
  ) {
    return "Money Hediye";
  }
  const refundQuantity = campaignBuyQuantity - campaignPayQuantity;
  return `${campaignBuyQuantity} Ode ${refundQuantity}'i Money Hediye`;
};

type DisplayPriceSource = "regular" | "money" | "effective";

const resolveDisplayPrice = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  if (marketplaceCode !== "MG") {
    return { price, source: "regular" as DisplayPriceSource };
  }
  const resolvedEffectivePrice = resolveEffectivePriceValue(
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember
  );
  if (considerEffectivePricing && resolvedEffectivePrice !== null) {
    return { price: resolvedEffectivePrice, source: "effective" as DisplayPriceSource };
  }
  const options: { value: number; source: DisplayPriceSource }[] = [];
  if (price !== null) {
    options.push({ value: price, source: "regular" });
  }
  if (migrosMoneyMember && moneyPrice !== null) {
    options.push({ value: moneyPrice, source: "money" });
  }
  if (options.length === 0) {
    return { price: null, source: "regular" as DisplayPriceSource };
  }
  const selected = options.reduce((best, current) =>
    current.value < best.value ? current : best
  );
  return { price: selected.value, source: selected.source };
};

const formatMarketplacePriceSuffix = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  marketplaceCode: string,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  const resolved = resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  );
  const suffix = formatPriceSuffix(resolved.price);
  if (!suffix) {
    return "";
  }
  if (marketplaceCode === "MG" && resolved.source === "money") {
    return `${suffix} (Money)`;
  }
  if (marketplaceCode === "MG" && resolved.source === "effective") {
    return `${suffix} (Efektif)`;
  }
  return suffix;
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

const formatMarketplacePriceLabel = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  marketplaceCode: string,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  const resolved = resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  );
  const label = formatPriceOrLabel(resolved.price);
  if (label === "Fiyat yok") {
    return label;
  }
  if (marketplaceCode === "MG" && resolved.source === "money") {
    return `${label} (Money)`;
  }
  if (marketplaceCode === "MG" && resolved.source === "effective") {
    return `${label} (Efektif)`;
  }
  return label;
};

const isMoneyDisplayPrice = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) =>
  resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  ).source === "money";

const resolveThresholdPrice = (item: NeedListItem, considerEffectivePricing: boolean) => {
  if (item.marketplaceCode !== "MG") {
    return item.price;
  }
  const resolvedEffectivePrice = resolveEffectivePriceValue(
    item.price,
    item.moneyPrice,
    item.effectivePrice,
    item.campaignBuyQuantity,
    item.campaignPayQuantity,
    true
  );
  if (considerEffectivePricing && resolvedEffectivePrice !== null) {
    return resolvedEffectivePrice;
  }
  const options = [item.price, item.moneyPrice, item.basketDiscountPrice].filter(
    (value): value is number => value !== null
  );
  if (options.length === 0) {
    return null;
  }
  return Math.min(...options);
};

const formatCategoryTitle = (name: string) => name.toLocaleUpperCase("tr-TR");
const marketplaceLabel = (code: string) => {
  if (code === "YS") {
    return "Yemeksepeti";
  }
  if (code === "MG") {
    return "Migros";
  }
  return code;
};
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

const resolveAvailabilityStatus = (score: number | null): AvailabilityStatus => {
  if (score === null || Number.isNaN(score)) {
    return "Normal";
  }
  if (score >= 65) {
    return "Uygun";
  }
  if (score >= 40) {
    return "Normal";
  }
  return "Pahali";
};

const urgencyLabel = (urgency: NeedUrgency) => {
  if (urgency === "VERY_URGENT") {
    return "Cok Acil";
  }
  if (urgency === "URGENT") {
    return "Acil";
  }
  return "Acil Degil";
};

const normalizeProductName = (value: string) =>
  value
    .toLocaleLowerCase("tr-TR")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\d+([.,]\d+)?\s*(g|gr|kg|ml|l|lt)\b/g, " ")
    .replace(/\b(li|lu|paket|adet|pet|sise|şişe)\b/g, " ")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

const tokenSet = (value: string) =>
  new Set(
    normalizeProductName(value)
      .split(" ")
      .filter((token) => token.length > 1)
  );

const NAME_STOP_WORDS = new Set([
  "ve",
  "ile",
  "icin",
  "için",
  "pet",
  "paket",
  "adet",
  "sise",
  "şişe",
  "kutu",
  "boy",
  "mini",
  "maxi",
]);

const coreTokenSet = (value: string) =>
  new Set(
    [...tokenSet(value)].filter(
      (token) => !NAME_STOP_WORDS.has(token) && token.length > 2
    )
  );

const jaccardSimilarity = (left: Set<string>, right: Set<string>) => {
  if (left.size === 0 || right.size === 0) {
    return 0;
  }
  let intersection = 0;
  left.forEach((token) => {
    if (right.has(token)) {
      intersection += 1;
    }
  });
  const union = left.size + right.size - intersection;
  return union === 0 ? 0 : intersection / union;
};

type QuantityInfo = {
  amount: number | null;
  unit: "g" | "ml" | null;
  packCount: number | null;
};

const parseQuantityInfo = (name: string): QuantityInfo => {
  const lower = name.toLocaleLowerCase("tr-TR");
  const packMatch = lower.match(/(\d+)\s*['']?(li|lu|lü|pack|paket)\b/);
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

const compareQuantity = (leftName: string, rightName: string) => {
  const left = parseQuantityInfo(leftName);
  const right = parseQuantityInfo(rightName);
  if (left.packCount !== null && right.packCount !== null && left.packCount !== right.packCount) {
    return { compatible: false, score: 0 };
  }
  if (left.unit && right.unit) {
    if (left.unit !== right.unit) {
      return { compatible: false, score: 0 };
    }
    if (left.amount !== null && right.amount !== null) {
      const ratio = Math.min(left.amount, right.amount) / Math.max(left.amount, right.amount);
      if (ratio < 0.8) {
        return { compatible: false, score: 0 };
      }
      if (ratio >= 0.95) {
        return { compatible: true, score: 1 };
      }
      if (ratio >= 0.9) {
        return { compatible: true, score: 0.8 };
      }
      return { compatible: true, score: 0.6 };
    }
  }
  if (left.packCount !== null && right.packCount !== null) {
    return { compatible: true, score: 0.8 };
  }
  return { compatible: true, score: 0.4 };
};

const imageFingerprint = (url: string) => {
  if (!url) {
    return "";
  }
  const withoutQuery = url.split("?")[0];
  const fileName = withoutQuery.split("/").pop() ?? "";
  return fileName.replace(/\.[a-z0-9]+$/i, "").toLocaleLowerCase("tr-TR");
};

const imageSimilarity = (leftUrl: string, rightUrl: string) => {
  const left = imageFingerprint(leftUrl);
  const right = imageFingerprint(rightUrl);
  if (!left || !right) {
    return 0;
  }
  if (left === right) {
    return 1;
  }
  return left.slice(0, 8) === right.slice(0, 8) ? 0.6 : 0;
};

const brandSimilarity = (leftBrand: string, rightBrand: string) => {
  const left = leftBrand.trim().toLocaleLowerCase("tr-TR");
  const right = rightBrand.trim().toLocaleLowerCase("tr-TR");
  if (!left || !right) {
    return 0.5;
  }
  return left === right ? 1 : 0;
};

const candidateKey = (item: MarketplaceProductCandidateResponse) =>
  `${item.marketplaceCode}:${item.externalId}`;

type CandidateMatchScore = {
  score: number;
  nameScore: number;
  coreNameScore: number;
  quantityScore: number;
  brandScore: number;
  imageScore: number;
};

type CandidatePair = {
  ys: MarketplaceProductCandidateResponse;
  mg: MarketplaceProductCandidateResponse;
  score: CandidateMatchScore;
};

const scoreCandidatePair = (
  source: MarketplaceProductCandidateResponse,
  target: MarketplaceProductCandidateResponse
): CandidateMatchScore | null => {
  const nameScore = jaccardSimilarity(tokenSet(source.name || ""), tokenSet(target.name || ""));
  const coreNameScore = jaccardSimilarity(
    coreTokenSet(source.name || ""),
    coreTokenSet(target.name || "")
  );
  if (nameScore < 0.55 && coreNameScore < 0.45) {
    return null;
  }
  const brandScore = brandSimilarity(source.brandName || "", target.brandName || "");
  if (brandScore === 0) {
    return null;
  }
  const quantity = compareQuantity(source.name || "", target.name || "");
  if (!quantity.compatible) {
    return null;
  }
  const imageScore = imageSimilarity(source.imageUrl || "", target.imageUrl || "");
  const score =
    nameScore * 0.34 +
    coreNameScore * 0.28 +
    quantity.score * 0.23 +
    brandScore * 0.1 +
    imageScore * 0.05;
  return {
    score,
    nameScore,
    coreNameScore,
    quantityScore: quantity.score,
    brandScore,
    imageScore,
  };
};

const entryToCandidate = (
  item: MarketplaceProductEntryResponse
): MarketplaceProductCandidateResponse => ({
  marketplaceCode: item.marketplaceCode,
  externalId: item.externalId,
  sku: item.sku,
  name: item.name,
  brandName: item.brandName,
  imageUrl: item.imageUrl,
  price: item.price,
  moneyPrice: item.moneyPrice,
  basketDiscountThreshold: item.basketDiscountThreshold,
  basketDiscountPrice: item.basketDiscountPrice,
  campaignBuyQuantity: item.campaignBuyQuantity,
  campaignPayQuantity: item.campaignPayQuantity,
  effectivePrice: item.effectivePrice,
});

const buildMarketplacePairs = (
  ys: MarketplaceProductCandidateResponse[],
  mg: MarketplaceProductCandidateResponse[],
  minScore: number
) => {
  const usedMgKeys = new Set<string>();
  const pairs: CandidatePair[] = [];
  for (const ysItem of ys) {
    let bestMatch: { item: MarketplaceProductCandidateResponse; score: CandidateMatchScore } | null =
      null;
    for (const mgItem of mg) {
      const mgKey = candidateKey(mgItem);
      if (usedMgKeys.has(mgKey)) {
        continue;
      }
      const scored = scoreCandidatePair(ysItem, mgItem);
      if (!scored) {
        continue;
      }
      if (!bestMatch || scored.score > bestMatch.score.score) {
        bestMatch = { item: mgItem, score: scored };
      }
    }
    if (bestMatch && bestMatch.score.score >= minScore) {
      usedMgKeys.add(candidateKey(bestMatch.item));
      pairs.push({ ys: ysItem, mg: bestMatch.item, score: bestMatch.score });
    }
  }
  return pairs;
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
  marketplaceCode: MarketplaceCode;
  bridgeSide: "left" | "right";
  sectionCandidates: MarketplaceProductCandidateResponse[];
  visibleSectionCandidates: MarketplaceProductCandidateResponse[];
  matchedCandidateKeys: Set<string>;
  suggestedListKey: string;
  isMigrosMoneyMember: boolean;
  considerEffectivePricing: boolean;
  candidateBusy: boolean;
  addedCandidateIds: Set<string>;
  addedCandidateKeys: Record<string, boolean>;
  expandedLists: Record<string, boolean>;
  onAddCandidate: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  onToggleList: (key: string) => void;
  onDragStartCandidate: (
    event: React.DragEvent<HTMLDivElement>,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
}>;

function SuggestedProducts({
  category,
  marketplaceCode,
  bridgeSide,
  sectionCandidates,
  visibleSectionCandidates,
  matchedCandidateKeys,
  suggestedListKey,
  isMigrosMoneyMember,
  considerEffectivePricing,
  candidateBusy,
  addedCandidateIds,
  addedCandidateKeys,
  expandedLists,
  onAddCandidate,
  onToggleList,
  onDragStartCandidate,
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
          const isMatched = matchedCandidateKeys.has(candidateKey(item));
          const matchedTone =
            marketplaceCode === "YS"
              ? "border-rose-300 bg-rose-50/70"
              : "border-amber-300 bg-amber-50/70";
          return (
            <div
              key={`${item.externalId}-${index}`}
              className={`relative flex h-[92px] items-center gap-3 rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 ${
                isMatched ? matchedTone : ""
              } ${
                isAdded ? "opacity-40" : "cursor-grab active:cursor-grabbing"
              }`}
              draggable={!isAdded && !candidateBusy}
              onDragStart={(event) => onDragStartCandidate(event, item)}
            >
              {isMatched && (
                <span className="absolute right-2 top-1 inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
                  <svg viewBox="0 0 16 16" className="h-3 w-3" aria-hidden>
                    <path
                      d="M6.2 5.2 4.7 3.7a2.4 2.4 0 1 0-3.4 3.4l1.5 1.5a2.4 2.4 0 0 0 3.4 0m3.6-2.4 1.5-1.5a2.4 2.4 0 1 1 3.4 3.4l-1.5 1.5a2.4 2.4 0 0 1-3.4 0M5.1 10.9l5.8-5.8"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                  <span>Eslesti</span>
                </span>
              )}
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
                <p className="truncate text-sm font-semibold text-[#111]">
                  {item.name}
                </p>
                <p className="truncate text-xs text-[#554f47]">
                  {item.brandName || "Marka yok"}
                  {formatMarketplacePriceSuffix(
                    item.price,
                    item.moneyPrice,
                    item.effectivePrice,
                    item.campaignBuyQuantity,
                    item.campaignPayQuantity,
                    item.marketplaceCode,
                    isMigrosMoneyMember,
                    considerEffectivePricing
                  )}
                </p>
                {isMoneyDisplayPrice(
                  item.marketplaceCode,
                  item.price,
                  item.moneyPrice,
                  item.effectivePrice,
                  item.campaignBuyQuantity,
                  item.campaignPayQuantity,
                  isMigrosMoneyMember,
                  considerEffectivePricing
                ) && (
                  <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                    <img
                      src="/migros-money.png"
                      alt="Money"
                      className="h-3 w-3 object-contain"
                    />
                    <span>Money ile</span>
                    <span>{formatPrice(item.moneyPrice)}</span>
                  </div>
                )}
                {hasBasketDiscount(
                  item.marketplaceCode,
                  item.basketDiscountThreshold,
                  item.basketDiscountPrice
                ) && (
                  <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                    <span>Sepette {formatTl(item.basketDiscountThreshold, 0)}</span>
                    <span>{formatTl(item.basketDiscountPrice)}</span>
                  </div>
                )}
                {hasEffectiveCampaign(
                  item.marketplaceCode,
                  item.price,
                  item.moneyPrice,
                  item.campaignBuyQuantity,
                  item.campaignPayQuantity,
                  item.effectivePrice
                ) && (
                  <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                    <span>
                      {formatEffectiveCampaignBadge(
                        item.campaignBuyQuantity,
                        item.campaignPayQuantity
                      )}
                    </span>
                  </div>
                )}
              </div>
              {isAdded ? null : (
                <button
                  type="button"
                  className="flex h-9 w-9 items-center justify-center rounded-full bg-[#d97706] text-lg font-semibold text-white transition hover:bg-[#b45309]"
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
  const [candidateBusyByCategory, setCandidateBusyByCategory] = useState<
    Record<number, Partial<Record<MarketplaceCode, boolean>>>
  >({});
  const [activeDropZoneKey, setActiveDropZoneKey] = useState<string | null>(null);
  const [candidateItemsByCategory, setCandidateItemsByCategory] = useState<
    Record<number, MarketplaceProductCandidateResponse[]>
  >({});
  const [addedProductsByCategory, setAddedProductsByCategory] = useState<
    Record<number, Partial<Record<MarketplaceCode, MarketplaceProductEntryResponse[]>>>
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
  const [showProductInfoModal, setShowProductInfoModal] = useState(false);
  const [selectedProductCategory, setSelectedProductCategory] = useState<Category | null>(null);
  const [selectedProductHistory, setSelectedProductHistory] = useState<HistoryPoint[]>([]);
  const [needList, setNeedList] = useState<NeedListItem[]>([]);
  const [selectedNeedUrgency, setSelectedNeedUrgency] = useState<NeedUrgency>("URGENT");
  const [opportunitiesByCategory, setOpportunitiesByCategory] = useState<
    Record<number, OpportunityItem[]>
  >({});
  const [historyMarketplaceFilter, setHistoryMarketplaceFilter] =
    useState<HistoryMarketplaceFilter>("ALL");
  const [historyRangeFilter, setHistoryRangeFilter] = useState<HistoryRangeFilter>("1M");
  const [hoveredHistoryPoint, setHoveredHistoryPoint] = useState<HoveredHistoryPoint | null>(
    null
  );
  const [historyBusy, setHistoryBusy] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [userSettings, setUserSettings] = useState<UserSettings>(
    DEFAULT_USER_SETTINGS
  );

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
    try {
      const raw = window.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as Partial<UserSettings>;
      setUserSettings({
        migrosMoneyMember: Boolean(parsed.migrosMoneyMember),
        considerEffectivePricing: Boolean(parsed.considerEffectivePricing),
        minimumBasketAmount:
          typeof parsed.minimumBasketAmount === "number"
            ? parsed.minimumBasketAmount
            : null,
        deliveryFee:
          typeof parsed.deliveryFee === "number" ? parsed.deliveryFee : null,
      });
    } catch {
      setUserSettings(DEFAULT_USER_SETTINGS);
    }
  }, []);

  useEffect(() => {
    window.localStorage.setItem(
      USER_SETTINGS_STORAGE_KEY,
      JSON.stringify(userSettings)
    );
  }, [userSettings]);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(NEED_LIST_STORAGE_KEY);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as NeedListItem[];
      setNeedList(Array.isArray(parsed) ? parsed : []);
    } catch {
      setNeedList([]);
    }
  }, []);

  useEffect(() => {
    window.localStorage.setItem(NEED_LIST_STORAGE_KEY, JSON.stringify(needList));
  }, [needList]);

  useEffect(() => {
    const flat = Object.values(opportunitiesByCategory).flat();
    window.localStorage.setItem(OPPORTUNITY_FEED_STORAGE_KEY, JSON.stringify(flat));
  }, [opportunitiesByCategory]);

  useEffect(() => {
    if (categories.length === 0) {
      return;
    }
    const validCategoryIds = new Set(categories.map((category) => category.id));
    setNeedList((prev) => prev.filter((item) => validCategoryIds.has(item.categoryId)));
  }, [categories]);

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
      await loadAllAddedProducts(created);
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

  const setMarketplaceBusy = (
    categoryId: number,
    marketplaceCode: MarketplaceCode,
    isBusy: boolean
  ) => {
    setCandidateBusyByCategory((prev) => ({
      ...prev,
      [categoryId]: {
        ...(prev[categoryId] ?? {}),
        [marketplaceCode]: isBusy,
      },
    }));
  };

  const isMarketplaceBusy = (categoryId: number, marketplaceCode: MarketplaceCode) =>
    Boolean(candidateBusyByCategory[categoryId]?.[marketplaceCode]);

  const applyLocalAddedProductsMutation = (
    categoryId: number,
    candidate: MarketplaceProductCandidateResponse,
    isAdded: boolean
  ) => {
    setAddedProductsByCategory((prev) => {
      const currentByMarketplace = prev[categoryId] ?? {};
      const marketplaceCode = candidate.marketplaceCode as MarketplaceCode;
      const currentList = currentByMarketplace[marketplaceCode] ?? [];
      const nextList = isAdded
        ? currentList.some((item) => item.externalId === candidate.externalId)
          ? currentList
          : [
              {
                marketplaceCode: candidate.marketplaceCode,
                externalId: candidate.externalId,
                sku: candidate.sku,
                name: candidate.name,
                productId: null,
                brandName: candidate.brandName,
                imageUrl: candidate.imageUrl,
                price: candidate.price,
                moneyPrice: candidate.moneyPrice,
                basketDiscountThreshold: candidate.basketDiscountThreshold,
                basketDiscountPrice: candidate.basketDiscountPrice,
                campaignBuyQuantity: candidate.campaignBuyQuantity,
                campaignPayQuantity: candidate.campaignPayQuantity,
                effectivePrice: candidate.effectivePrice,
              },
              ...currentList,
            ]
        : currentList.filter((item) => item.externalId !== candidate.externalId);
      return {
        ...prev,
        [categoryId]: {
          ...currentByMarketplace,
          [marketplaceCode]: nextList,
        },
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

  const buildPriceHistoryQuery = (marketplaceCode?: string) => {
    const params = new URLSearchParams();
    if (marketplaceCode) {
      params.set("marketplaceCode", marketplaceCode);
    }
    params.set("useMoneyPrice", String(userSettings.migrosMoneyMember));
    params.set("useEffectivePrice", String(userSettings.considerEffectivePricing));
    return params.toString();
  };

  const loadCandidates = async (category: Category) => {
    setMarketplaceBusy(category.id, "YS", true);
    setMarketplaceBusy(category.id, "MG", true);
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
      setMarketplaceBusy(category.id, "YS", false);
      setMarketplaceBusy(category.id, "MG", false);
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
        [category.id]: {
          ...(prev[category.id] ?? {}),
          [marketplaceCode]: products,
        },
      }));
    } catch (err) {
      addNotice(`Eklenen urunler alinamadi: ${(err as Error).message}`, "error");
    }
  };

  const loadAllAddedProducts = async (category: Category) => {
    await Promise.all([loadAddedProducts(category, "YS"), loadAddedProducts(category, "MG")]);
  };

  const loadCategoryOpportunities = async (category: Category) => {
    const [ys, mg] = await Promise.all([
      request<MarketplaceProductEntryResponse[]>(
        `/categories/${category.id}/marketplace-products/added?marketplaceCode=YS`
      ),
      request<MarketplaceProductEntryResponse[]>(
        `/categories/${category.id}/marketplace-products/added?marketplaceCode=MG`
      ),
    ]);
    const all = [...ys, ...mg];
    const targets = all.filter(
      (item): item is MarketplaceProductEntryResponse & { productId: number } => item.productId !== null
    );
    if (targets.length === 0) {
      setOpportunitiesByCategory((prev) => ({ ...prev, [category.id]: [] }));
      return;
    }
    const feed: Array<OpportunityItem | null> = await Promise.all(
      targets.map(async (target) => {
        const history = await request<PriceHistoryResponse[]>(
          `/products/${target.productId}/prices?${buildPriceHistoryQuery(target.marketplaceCode)}`
        );
        const latest = [...history]
          .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
        if (!latest?.opportunityLevel) {
          return null;
        }
        const availabilityStatus = resolveAvailabilityStatus(latest.availabilityScore ?? null);
        if (
          latest.opportunityLevel !== "Yuksek" ||
          (availabilityStatus !== "Uygun" && availabilityStatus !== "Normal")
        ) {
          return null;
        }
        return {
          key: `${category.id}:${target.marketplaceCode}:${target.externalId}`,
          categoryId: category.id,
          categoryName: category.name,
          externalId: target.externalId,
          marketplaceCode: target.marketplaceCode as MarketplaceCode,
          name: target.name || `Urun ${target.externalId}`,
          price: target.price,
          availabilityStatus,
          opportunityLevel: latest.opportunityLevel,
        };
      })
    );
    const filtered = feed.filter((item): item is OpportunityItem => item !== null);
    setOpportunitiesByCategory((prev) => ({ ...prev, [category.id]: filtered }));
  };

  const handleToggleCategory = async (category: Category) => {
    if (expandedCategoryId === category.id) {
      setExpandedCategoryId(null);
      setSelectedAddedProduct(null);
      setSelectedProductCategory(null);
      setSelectedProductHistory([]);
      setHistoryMarketplaceFilter("ALL");
      setHistoryRangeFilter("1M");
      setHoveredHistoryPoint(null);
      return;
    }
    setExpandedCategoryId(category.id);
    setSelectedAddedProduct(null);
    setSelectedProductCategory(null);
    setSelectedProductHistory([]);
    setHistoryMarketplaceFilter("ALL");
    setHistoryRangeFilter("1M");
    setHoveredHistoryPoint(null);
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
    const loadedAdded = addedProductsByCategory[category.id];
    if (!loadedAdded?.MG || !loadedAdded?.YS) {
      await loadAllAddedProducts(category);
    }
    await loadCategoryOpportunities(category);
  };

  const refreshCategoryMarketplaceData = async (category: Category) => {
    await loadAllAddedProducts(category);
  };

  const mutateCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse,
    method: "POST" | "DELETE",
    successMessage: string,
    isAdded: boolean
  ) => {
    setMarketplaceBusy(category.id, candidate.marketplaceCode as MarketplaceCode, true);
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
      applyLocalAddedProductsMutation(category.id, candidate, isAdded);
      void refreshCategoryMarketplaceData(category);
      void loadCategoryOpportunities(category);
      return true;
    } catch (err) {
      const actionText = method === "POST" ? "eklenemedi" : "silinemedi";
      addNotice(`Urun ${actionText}: ${(err as Error).message}`, "error");
      return false;
    } finally {
      setMarketplaceBusy(category.id, candidate.marketplaceCode as MarketplaceCode, false);
    }
  };

  const removeNeedListEntryForCandidate = (
    candidate: MarketplaceProductCandidateResponse
  ) => {
    setNeedList((prev) =>
      prev.filter(
        (item) =>
          !(
            item.type === "PRODUCT" &&
            item.externalId === candidate.externalId &&
            item.marketplaceCode === (candidate.marketplaceCode as MarketplaceCode)
          )
      )
    );
  };

  const removeCategoryLocalState = (categoryId: number) => {
    setProductsByCategory((prev) => {
      const next = { ...prev };
      delete next[categoryId];
      return next;
    });
    setCandidateItemsByCategory((prev) => {
      const next = { ...prev };
      delete next[categoryId];
      return next;
    });
    setAddedProductsByCategory((prev) => {
      const next = { ...prev };
      delete next[categoryId];
      return next;
    });
    setAddedCandidateKeysByCategory((prev) => {
      const next = { ...prev };
      delete next[categoryId];
      return next;
    });
    setExpandedLists((prev) =>
      Object.fromEntries(
        Object.entries(prev).filter(([key]) => !key.startsWith(`${categoryId}:`))
      )
    );
    setOpportunitiesByCategory((prev) => {
      const next = { ...prev };
      delete next[categoryId];
      return next;
    });
  };

  const handleDeleteCategory = async (category: Category) => {
    const confirmed = window.confirm(
      `"${category.name}" kategorisini silmek istiyor musun?`
    );
    if (!confirmed) {
      return;
    }
    setBusy(true);
    try {
      await request<string>(`/categories/${category.id}`, { method: "DELETE" });
      setCategories((prev) => prev.filter((item) => item.id !== category.id));
      removeCategoryLocalState(category.id);
      setNeedList((prev) => prev.filter((item) => item.categoryId !== category.id));
      if (expandedCategoryId === category.id) {
        setExpandedCategoryId(null);
        setSelectedAddedProduct(null);
        setSelectedProductCategory(null);
        setSelectedProductHistory([]);
        setHistoryMarketplaceFilter("ALL");
        setHistoryRangeFilter("1M");
        setHoveredHistoryPoint(null);
      }
      addNotice("Kategori silindi.", "success");
    } catch (err) {
      addNotice(`Kategori silinemedi: ${(err as Error).message}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const onDeleteCategoryClick = (category: Category) => {
    return () => {
      void handleDeleteCategory(category);
    };
  };

  type CrossMarketplaceMatch = {
    item: MarketplaceProductCandidateResponse;
    score: number;
    nameScore: number;
    coreNameScore: number;
    quantityScore: number;
    brandScore: number;
    imageScore: number;
  };

  const findCrossMarketplaceMatches = (
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const otherMarketplaceCode = candidate.marketplaceCode === "YS" ? "MG" : "YS";
    return activeCandidates
      .filter((item) => item.marketplaceCode === otherMarketplaceCode)
      .map((item): CrossMarketplaceMatch | null => {
        const scored = scoreCandidatePair(candidate, item);
        if (!scored) {
          return null;
        }
        return {
          item,
          ...scored,
        };
      })
      .filter((match): match is CrossMarketplaceMatch => match !== null)
      .sort((left, right) => right.score - left.score);
  };

  const findBestCrossMarketplaceMatch = (
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const matches = findCrossMarketplaceMatches(candidate);
    if (matches.length === 0) {
      return null;
    }
    return matches[0];
  };

  const findBestCrossMarketplaceAddedMatch = (
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const otherMarketplaceCode = candidate.marketplaceCode === "YS" ? "MG" : "YS";
    const sourceCandidate: MarketplaceProductCandidateResponse = { ...candidate };
    let bestMatch:
      | {
          item: MarketplaceProductEntryResponse;
          score: CandidateMatchScore;
        }
      | null = null;
    for (const addedItem of allAddedProducts) {
      if (addedItem.marketplaceCode !== otherMarketplaceCode) {
        continue;
      }
      const targetCandidate = entryToCandidate(addedItem);
      const score = scoreCandidatePair(sourceCandidate, targetCandidate);
      if (!score) {
        continue;
      }
      if (!bestMatch || score.score > bestMatch.score.score) {
        bestMatch = { item: addedItem, score };
      }
    }
    return bestMatch;
  };

  const isAlreadyAddedInMarketplace = (
    candidate: MarketplaceProductCandidateResponse
  ) => {
    return allAddedProducts.some(
      (item) =>
        item.marketplaceCode === candidate.marketplaceCode &&
        item.externalId === candidate.externalId
    );
  };

  const handleAddCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const isSuccess = await mutateCandidateProduct(
      category,
      candidate,
      "POST",
      "Urun eklendi.",
      true
    );
    if (!isSuccess) {
      return;
    }
    const bestMatch = findBestCrossMarketplaceMatch(candidate);
    if (!bestMatch) {
      return;
    }
    const target = bestMatch.item;
    const otherMarketplaceName = candidate.marketplaceCode === "YS" ? "Migros" : "Yemeksepeti";
    if (isAlreadyAddedInMarketplace(target)) {
      addNotice(
        `${otherMarketplaceName} tarafinda benzer urun zaten ekli: ${target.name}`,
        "info"
      );
      return;
    }
    const autoAddSafe =
      bestMatch.score >= 0.82 &&
      bestMatch.nameScore >= 0.62 &&
      bestMatch.coreNameScore >= 0.55 &&
      bestMatch.quantityScore >= 0.8;
    if (!autoAddSafe) {
      addNotice(
        `${otherMarketplaceName} tarafinda benzer urun bulundu: ${target.name}`,
        "info"
      );
      return;
    }
    const autoAddSuccess = await mutateCandidateProduct(
      category,
      target,
      "POST",
      `${otherMarketplaceName} tarafinda benzer urun otomatik eklendi.`,
      true
    );
    if (!autoAddSuccess) {
      addNotice(`${otherMarketplaceName} tarafinda otomatik ekleme basarisiz oldu.`, "error");
    }
  };

  const handleRemoveCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const removed = await mutateCandidateProduct(
      category,
      candidate,
      "DELETE",
      "Urun silindi.",
      false
    );
    if (!removed) {
      return;
    }
    removeNeedListEntryForCandidate(candidate);
    if (
      selectedAddedProduct &&
      selectedAddedProduct.externalId === candidate.externalId &&
      selectedAddedProduct.marketplaceCode === candidate.marketplaceCode
    ) {
      setSelectedAddedProduct(null);
      setSelectedProductCategory(null);
      setSelectedProductHistory([]);
      setHoveredHistoryPoint(null);
    }
    const bestMatch = findBestCrossMarketplaceAddedMatch(candidate);
    if (!bestMatch) {
      return;
    }
    const autoRemoveSafe =
      bestMatch.score.score >= 0.82 &&
      bestMatch.score.nameScore >= 0.62 &&
      bestMatch.score.coreNameScore >= 0.55 &&
      bestMatch.score.quantityScore >= 0.8;
    if (!autoRemoveSafe) {
      return;
    }
    const otherMarketplaceName =
      candidate.marketplaceCode === "YS" ? "Migros" : "Yemeksepeti";
    await mutateCandidateProduct(
      category,
      entryToCandidate(bestMatch.item),
      "DELETE",
      `${otherMarketplaceName} tarafindaki es urun de silindi.`,
      false
    );
    removeNeedListEntryForCandidate(entryToCandidate(bestMatch.item));
    if (
      selectedAddedProduct &&
      selectedAddedProduct.externalId === bestMatch.item.externalId &&
      selectedAddedProduct.marketplaceCode === bestMatch.item.marketplaceCode
    ) {
      setSelectedAddedProduct(null);
      setSelectedProductCategory(null);
      setSelectedProductHistory([]);
      setHoveredHistoryPoint(null);
    }
  };

  const handleSelectAddedProduct = async (
    product: MarketplaceProductEntryResponse,
    category: Category
  ) => {
    setShowProductInfoModal(true);
    setSelectedAddedProduct(product);
    setSelectedProductCategory(category);
    setSelectedProductHistory([]);
    setHistoryMarketplaceFilter("ALL");
    setHistoryRangeFilter("1M");
    setHoveredHistoryPoint(null);
    const selectedTokens = tokenSet(product.name || "");
    const otherMarketplaceCode = product.marketplaceCode === "YS" ? "MG" : "YS";
    const fuzzyMatches = allAddedProducts.filter((item) => {
      if (item.marketplaceCode !== otherMarketplaceCode) {
        return false;
      }
      const score = jaccardSimilarity(selectedTokens, tokenSet(item.name || ""));
      return score >= 0.6;
    });
    const relatedProducts = fuzzyMatches.length > 0 ? [product, ...fuzzyMatches] : [product];
    const targets = relatedProducts.filter(
      (item): item is MarketplaceProductEntryResponse & { productId: number } =>
        item.productId !== null
    );
    const uniqueTargets = Array.from(
      new Map(targets.map((item) => [item.productId, item])).values()
    );
    if (uniqueTargets.length === 0) {
      return;
    }
    setHistoryBusy(true);
    try {
      const historyGroups = await Promise.all(
        uniqueTargets.map(async (target) => {
          const data = await request<PriceHistoryResponse[]>(
            `/products/${target.productId}/prices?${buildPriceHistoryQuery()}`
          );
          return data.map((item) => ({
            recordedAt: item.recordedAt,
            price: Number(item.price),
            marketplaceCode: item.marketplaceCode || target.marketplaceCode,
            availabilityScore:
              typeof item.availabilityScore === "number" ? item.availabilityScore : null,
            opportunityLevel:
              typeof item.opportunityLevel === "string" ? item.opportunityLevel : null,
          }));
        })
      );
      const deduped = Array.from(
        new Map(
          historyGroups
            .flat()
            .map((item) => [
              `${item.recordedAt}:${item.marketplaceCode}:${item.price.toFixed(2)}`,
              item,
            ])
        ).values()
      );
      setSelectedProductHistory(deduped);
    } catch (err) {
      addNotice(`Fiyat gecmisi alinamadi: ${(err as Error).message}`, "error");
    } finally {
      setHistoryBusy(false);
    }
  };

  const handleAddSelectedToNeedList = () => {
    if (!selectedAddedProduct || !selectedProductCategory) {
      return;
    }
    const itemKey = `category:${selectedProductCategory.id}`;
    const latestPoint = [...selectedProductHistory]
      .filter((item) => item.marketplaceCode === selectedAddedProduct.marketplaceCode)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    const availabilityScore = latestPoint?.availabilityScore ?? null;
    const availabilityStatus = resolveAvailabilityStatus(availabilityScore);
    const opportunityLevel = latestPoint?.opportunityLevel ?? null;
    const needItem: NeedListItem = {
      key: itemKey,
      type: "PRODUCT",
      categoryId: selectedProductCategory.id,
      categoryName: selectedProductCategory.name,
      externalId: selectedAddedProduct.externalId,
      marketplaceCode: selectedAddedProduct.marketplaceCode as MarketplaceCode,
      name: selectedAddedProduct.name || `Urun ${selectedAddedProduct.externalId}`,
      imageUrl: selectedAddedProduct.imageUrl,
      price: selectedAddedProduct.price,
      moneyPrice: selectedAddedProduct.moneyPrice,
      basketDiscountThreshold: selectedAddedProduct.basketDiscountThreshold,
      basketDiscountPrice: selectedAddedProduct.basketDiscountPrice,
      campaignBuyQuantity: selectedAddedProduct.campaignBuyQuantity,
      campaignPayQuantity: selectedAddedProduct.campaignPayQuantity,
      effectivePrice: selectedAddedProduct.effectivePrice,
      urgency: selectedNeedUrgency,
      availabilityScore,
      availabilityStatus,
      opportunityLevel,
    };
    setNeedList((prev) => {
      const withoutCategory = prev.filter(
        (item) => item.categoryId !== selectedProductCategory.id
      );
      return [needItem, ...withoutCategory];
    });
    addNotice(
      `${selectedProductCategory.name} kategorisi icin secili urun kaydedildi.`,
      "success"
    );
  };

  const handleAddActiveCategoryToNeedList = () => {
    if (!activeCategory) {
      return;
    }
    const item: NeedListItem = {
      key: `category:${activeCategory.id}`,
      type: "CATEGORY",
      categoryId: activeCategory.id,
      categoryName: activeCategory.name,
      externalId: null,
      marketplaceCode: null,
      name: `${formatCategoryTitle(activeCategory.name)} (Kategori)`,
      imageUrl: "",
      price: null,
      moneyPrice: null,
      basketDiscountThreshold: null,
      basketDiscountPrice: null,
      campaignBuyQuantity: null,
      campaignPayQuantity: null,
      effectivePrice: null,
      urgency: selectedNeedUrgency,
      availabilityScore: null,
      availabilityStatus: "Normal",
      opportunityLevel: null,
    };
    setNeedList((prev) => {
      const withoutCategory = prev.filter((entry) => entry.categoryId !== activeCategory.id);
      return [item, ...withoutCategory];
    });
    addNotice(`${activeCategory.name} kategorisi ihtiyac listesine eklendi.`, "success");
  };

  const removeFromNeedList = (key: string) => {
    setNeedList((prev) => prev.filter((item) => item.key !== key));
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

  const onSelectAddedProductClick = (
    item: MarketplaceProductEntryResponse,
    category: Category
  ) => {
    return () => {
      void handleSelectAddedProduct(item, category);
    };
  };

  const onSelectAddedProductKeyDown = (
    item: MarketplaceProductEntryResponse,
    category: Category
  ) => {
    return (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (event.key !== "Enter" && event.key !== " ") {
        return;
      }
      event.preventDefault();
      void handleSelectAddedProduct(item, category);
    };
  };

  const onRemoveAddedProductClick = (
    category: Category,
    item: MarketplaceProductEntryResponse,
    marketplaceCode: MarketplaceCode
  ) => {
    return (event: React.MouseEvent<HTMLButtonElement>) => {
      event.stopPropagation();
      void handleRemoveCandidateProduct(category, {
        marketplaceCode,
        externalId: item.externalId,
        sku: item.sku,
        name: item.name,
        brandName: item.brandName,
        imageUrl: item.imageUrl,
        price: item.price,
        moneyPrice: item.moneyPrice,
        basketDiscountThreshold: item.basketDiscountThreshold,
        basketDiscountPrice: item.basketDiscountPrice,
        campaignBuyQuantity: item.campaignBuyQuantity,
        campaignPayQuantity: item.campaignPayQuantity,
        effectivePrice: item.effectivePrice,
      });
    };
  };

  const onToggleProductClick = (productId: number) => {
    return () => {
      void handleToggleProduct(productId);
    };
  };

  const setMigrosMoneyMembership = (isMember: boolean) => {
    setUserSettings((prev) => ({ ...prev, migrosMoneyMember: isMember }));
  };

  const onCandidateDragStart = (
    event: React.DragEvent<HTMLDivElement>,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    event.dataTransfer.effectAllowed = "copy";
    event.dataTransfer.setData(CANDIDATE_DRAG_TYPE, JSON.stringify(candidate));
  };

  const onCandidateDragOver = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  };

  const onCandidateDragEnter = (dropZoneKey: string) => {
    return () => setActiveDropZoneKey(dropZoneKey);
  };

  const onCandidateDragLeave = (dropZoneKey: string) => {
    return () => {
      if (activeDropZoneKey === dropZoneKey) {
        setActiveDropZoneKey(null);
      }
    };
  };

  const onCandidateDrop = (category: Category, marketplaceCode: MarketplaceCode) => {
    return (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setActiveDropZoneKey(null);
      const raw = event.dataTransfer.getData(CANDIDATE_DRAG_TYPE);
      if (!raw) {
        return;
      }
      try {
        const candidate = JSON.parse(raw) as MarketplaceProductCandidateResponse;
        if (candidate.marketplaceCode !== marketplaceCode) {
          addNotice("Ayni marketplace icinde birakabilirsiniz.", "error");
          return;
        }
        void handleAddCandidateProduct(category, candidate);
      } catch {
        addNotice("Suruklenen urun verisi okunamadi.", "error");
      }
    };
  };

  const activeCategory = categories.find((cat) => cat.id === expandedCategoryId);
  const activeProducts = activeCategory ? productsByCategory[activeCategory.id] : null;
  const activeCandidates = activeCategory
    ? candidateItemsByCategory[activeCategory.id] ?? []
    : [];
  const addedProductsByMarketplace = useMemo(() => {
    if (!activeCategory) {
      return {};
    }
    return addedProductsByCategory[activeCategory.id] ?? {};
  }, [activeCategory, addedProductsByCategory]);
  const allAddedProducts = useMemo(
    () =>
      Object.values(addedProductsByMarketplace).flatMap(
        (items) => items ?? []
      ),
    [addedProductsByMarketplace]
  );
  const addedCandidateIds = useMemo(
    () => new Set(allAddedProducts.map((item) => item.externalId)),
    [allAddedProducts]
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

  const suggestedMatchPairs = useMemo(() => {
    const ys = activeCandidates.filter((item) => item.marketplaceCode === "YS");
    const mg = activeCandidates.filter((item) => item.marketplaceCode === "MG");
    return buildMarketplacePairs(ys, mg, 0.72);
  }, [activeCandidates]);

  const suggestedMatchKeySet = useMemo(() => {
    const keys = new Set<string>();
    suggestedMatchPairs.forEach((pair) => {
      keys.add(candidateKey(pair.ys));
      keys.add(candidateKey(pair.mg));
    });
    return keys;
  }, [suggestedMatchPairs]);

  const suggestedMatchRank = useMemo(() => {
    const rankMap: Record<string, number> = {};
    suggestedMatchPairs.forEach((pair, index) => {
      rankMap[candidateKey(pair.ys)] = index;
      rankMap[candidateKey(pair.mg)] = index;
    });
    return rankMap;
  }, [suggestedMatchPairs]);

  const addedMatchPairs = useMemo(() => {
    const ys = allAddedProducts
      .filter((item) => item.marketplaceCode === "YS")
      .map(entryToCandidate);
    const mg = allAddedProducts
      .filter((item) => item.marketplaceCode === "MG")
      .map(entryToCandidate);
    return buildMarketplacePairs(ys, mg, 0.72);
  }, [allAddedProducts]);

  const addedMatchKeySet = useMemo(() => {
    const keys = new Set<string>();
    addedMatchPairs.forEach((pair) => {
      keys.add(candidateKey(pair.ys));
      keys.add(candidateKey(pair.mg));
    });
    return keys;
  }, [addedMatchPairs]);

  const addedMatchRank = useMemo(() => {
    const rankMap: Record<string, number> = {};
    addedMatchPairs.forEach((pair, index) => {
      rankMap[candidateKey(pair.ys)] = index;
      rankMap[candidateKey(pair.mg)] = index;
    });
    return rankMap;
  }, [addedMatchPairs]);

  const compactHistory = useMemo(() => {
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

  const selectedOpportunityLevel = useMemo(() => {
    if (!selectedAddedProduct) {
      return "-";
    }
    const preferred = selectedProductHistory.find(
      (item) =>
        item.marketplaceCode === selectedAddedProduct.marketplaceCode &&
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
  }, [selectedAddedProduct, selectedProductHistory]);

  const selectedTodayRecommendation = useMemo(() => {
    if (!selectedAddedProduct) {
      return {
        label: "-",
        reason: "Secili urun yok.",
        availabilityStatus: "Normal" as AvailabilityStatus,
      };
    }
    const latestPoint = [...selectedProductHistory]
      .filter((item) => item.marketplaceCode === selectedAddedProduct.marketplaceCode)
      .sort((left, right) => Date.parse(right.recordedAt) - Date.parse(left.recordedAt))[0];
    const availabilityStatus = resolveAvailabilityStatus(latestPoint?.availabilityScore ?? null);
    const opportunityLevel = latestPoint?.opportunityLevel ?? selectedOpportunityLevel;
    const effectiveCampaignEligible =
      userSettings.considerEffectivePricing &&
      hasEffectiveCampaign(
        selectedAddedProduct.marketplaceCode,
        selectedAddedProduct.price,
        selectedAddedProduct.moneyPrice,
        selectedAddedProduct.campaignBuyQuantity,
        selectedAddedProduct.campaignPayQuantity,
        selectedAddedProduct.effectivePrice
      );
    const shouldBuyToday =
      availabilityStatus === "Uygun" ||
      (availabilityStatus === "Normal" && opportunityLevel === "Yuksek") ||
      effectiveCampaignEligible;
    return {
      label: shouldBuyToday ? "Bugun Alinabilir" : "Bekletilebilir",
      reason: shouldBuyToday
        ? effectiveCampaignEligible
          ? `Efektif kampanya fiyati nedeniyle alinabilir.`
          : `${availabilityStatus} fiyat seviyesi ve firsat durumu uygun.`
        : `${availabilityStatus} fiyat seviyesi nedeniyle beklemek daha iyi olabilir.`,
      availabilityStatus,
    };
  }, [
    selectedAddedProduct,
    selectedProductHistory,
    selectedOpportunityLevel,
    userSettings.considerEffectivePricing,
  ]);

  const selectedNeedItemKey = selectedProductCategory
    ? `category:${selectedProductCategory.id}`
    : null;
  const selectedInNeedList = selectedNeedItemKey
    ? needList.some((item) => item.key === selectedNeedItemKey)
    : false;

  const basketDecisionByNeed = useMemo(() => {
    const defaultThreshold =
      userSettings.minimumBasketAmount ?? DEFAULT_MIGROS_BASKET_THRESHOLD;
    const migrosBasketTotal = needList
      .filter((item) => item.marketplaceCode === "MG")
      .reduce((sum, item) => {
        const price = resolveThresholdPrice(
          item,
          userSettings.considerEffectivePricing
        );
        return sum + (price ?? 0);
      }, 0);

    return needList.map((item) => {
      const isHighOpportunity = item.opportunityLevel === "Yuksek";
      const campaignThreshold = item.basketDiscountThreshold ?? defaultThreshold;
      const campaignEligible =
        item.marketplaceCode === "MG" &&
        item.basketDiscountPrice !== null &&
        migrosBasketTotal >= campaignThreshold;
      const effectiveCampaignEligible =
        userSettings.considerEffectivePricing &&
        hasEffectiveCampaign(
          item.marketplaceCode,
          item.price,
          item.moneyPrice,
          item.campaignBuyQuantity,
          item.campaignPayQuantity,
          item.effectivePrice
        );
      const canBuy =
        item.urgency === "VERY_URGENT"
          ? item.availabilityStatus !== "Pahali"
          : item.urgency === "URGENT"
            ? item.availabilityStatus === "Uygun" ||
              (item.availabilityStatus === "Normal" && isHighOpportunity)
            : item.availabilityStatus === "Uygun";
      return {
        ...item,
        shouldBuy: canBuy || campaignEligible || effectiveCampaignEligible,
      };
    });
  }, [needList, userSettings.minimumBasketAmount, userSettings.considerEffectivePricing]);

  const suggestedBasketItems = useMemo(
    () => basketDecisionByNeed.filter((item) => item.shouldBuy),
    [basketDecisionByNeed]
  );

  const autoOpportunitySuggestions = useMemo(() => {
    const needCategories = new Set(needList.map((item) => item.categoryId));
    const all = Object.values(opportunitiesByCategory).flat();
    return all.filter((item) => !needCategories.has(item.categoryId));
  }, [needList, opportunitiesByCategory]);

  const basketSuggestionCount = suggestedBasketItems.length + autoOpportunitySuggestions.length;

  const filteredHistory = useMemo(() => {
    if (historyMarketplaceFilter === "ALL") {
      return compactHistory;
    }
    return compactHistory.filter(
      (item) => item.marketplaceCode === historyMarketplaceFilter
    );
  }, [compactHistory, historyMarketplaceFilter]);

  const rangeFilteredHistory = useMemo(() => {
    if (filteredHistory.length === 0) {
      return [];
    }
    const parsedPoints = filteredHistory
      .map((item) => ({
        item,
        ts: Date.parse(item.recordedAt),
      }))
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
    const scorePoints = rangeFilteredHistory.filter(
      (point) =>
        point.availabilityScore !== null &&
        point.availabilityScore !== undefined &&
        Number.isFinite(point.availabilityScore)
    );
    scorePoints.forEach((point) => {
      const status = resolveAvailabilityStatus(point.availabilityScore);
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
    return {
      bands: baseBands,
      total,
      normalizedTotal,
    };
  }, [rangeFilteredHistory]);

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
  } else if (filteredHistory.length === 0) {
    historyContent = (
      <p className="mt-3 text-sm text-[#6b655c]">
        Secili market icin kayit yok.
      </p>
    );
  } else if (rangeFilteredHistory.length === 0) {
    historyContent = (
      <p className="mt-3 text-sm text-[#6b655c]">
        Secili zaman araliginda kayit yok.
      </p>
    );
  } else {
    historyContent = (
      <div className="mt-3 space-y-3">
        <div className="flex flex-wrap items-center gap-2 text-[11px] text-[#6b655c]">
          <span className="text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">
            Marketler
          </span>
          <button
            type="button"
            className={`rounded-full border px-2 py-1 transition ${
              historyMarketplaceFilter === "ALL"
                ? "border-black/20 bg-white text-[#111]"
                : "border-black/10 bg-[#f9f4ee] text-[#6b655c]"
            }`}
            onClick={() => {
              setHistoryMarketplaceFilter("ALL");
              setHoveredHistoryPoint(null);
            }}
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
            onClick={() => {
              setHistoryMarketplaceFilter("YS");
              setHoveredHistoryPoint(null);
            }}
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
            onClick={() => {
              setHistoryMarketplaceFilter("MG");
              setHoveredHistoryPoint(null);
            }}
          >
            <span className="h-2 w-2 rounded-full bg-amber-500" />
            Migros
          </button>
          <span className="inline-flex items-center gap-1">
            <span className="h-[2px] w-4 border-t-2 border-dashed border-slate-400" />
            Aylik Ortalama
          </span>
          <span className="ml-2 text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">
            Zaman
          </span>
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
            <p className="text-[10px] uppercase tracking-[0.2em] text-[#9a5c00]">
              Durum Dagilimi
            </p>
            <div className="mt-2 space-y-2">
              {historyBandSummary.bands.map((band) => {
                const ratio = Math.round((band.count / historyBandSummary.normalizedTotal) * 100);
                return (
                  <div
                    key={`band-card-${band.label}`}
                    className="rounded-xl border border-black/5 bg-white px-2 py-1.5 text-[11px]"
                  >
                    <div className="flex items-center justify-between">
                      <span className="inline-flex items-center gap-1 text-[#334155]">
                        <span
                          className="h-2.5 w-2.5 rounded-full"
                          style={{ backgroundColor: band.color }}
                        />
                        {band.label}
                      </span>
                      <span className="font-semibold text-[#111]">{ratio}%</span>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="mt-3 rounded-xl border border-black/5 bg-white px-2 py-2">
              <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
                Firsat Durumu
              </p>
              <p className="mt-1 text-sm font-semibold text-[#111]">{selectedOpportunityLevel}</p>
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
                  return {
                    ...point,
                    ts: Number.isFinite(parsed) ? parsed : index,
                  };
                })
                .sort((left, right) => left.ts - right.ts);
              const prices = points.map((point) => point.price);
              const max = Math.max(...prices, 0);
              const axis = buildNicePriceAxis(max);
              const range = Math.max(axis.maxTick, 1);
              const minTs = Math.min(...points.map((point) => point.ts));
              const maxTs = Math.max(...points.map((point) => point.ts));
              const timeRange = maxTs - minTs || 1;
              const toX = (ts: number) =>
                ((ts - minTs) / timeRange) * plotWidth + plotLeft;
              const toCoord = (point: {
                recordedAt: string;
                price: number;
                marketplaceCode: string;
                availabilityScore: number | null;
                opportunityLevel: string | null;
                ts: number;
              }) => {
                const x = toX(point.ts);
                const y = axisBottom - (point.price / range) * ySpan;
                return { x, y, point };
              };
              const ysCoords = points
                .filter((point) => point.marketplaceCode === "YS")
                .map(toCoord);
              const mgCoords = points
                .filter((point) => point.marketplaceCode === "MG")
                .map(toCoord);
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
              const monthlyAverage =
                monthAverages.reduce((sum, value) => sum + value, 0) /
                Math.max(monthAverages.length, 1);
              const averageY = axisBottom - (monthlyAverage / range) * ySpan;
              const ysLine = ysCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
              const mgLine = mgCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
              const ysArea =
                ysCoords.length > 1
                  ? `${ysLine} ${ysCoords[ysCoords.length - 1].x},${axisBottom} ${ysCoords[0].x},${axisBottom}`
                  : "";
              const mgArea =
                mgCoords.length > 1
                  ? `${mgLine} ${mgCoords[mgCoords.length - 1].x},${axisBottom} ${mgCoords[0].x},${axisBottom}`
                  : "";
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
                  ? `${hoveredHistoryPoint.point.availabilityScore.toFixed(1)}`
                  : "-";
              const bandTemplate = historyBandSummary.bands;
              const hoverPanelWidth = axisRight - axisLeft;
              return (
                <>
                  <line
                    x1={axisLeft}
                    y1={axisTop}
                    x2={axisLeft}
                    y2={axisBottom}
                    stroke="#94a3b8"
                    strokeWidth="1"
                  />
                  <line
                    x1={axisLeft}
                    y1={axisBottom}
                    x2={axisRight}
                    y2={axisBottom}
                    stroke="#94a3b8"
                    strokeWidth="1"
                  />
                  {(() => {
                    const bandHeight = ySpan / bandTemplate.length;
                    return bandTemplate.map((band, index) => {
                      const y = axisTop + bandHeight * index;
                      return (
                        <g key={`price-band-${band.label}`}>
                          <rect
                            x={plotLeft}
                            y={y}
                            width={plotWidth}
                            height={Math.max(bandHeight, 0)}
                            fill={band.color}
                            fillOpacity="0.26"
                          />
                        </g>
                      );
                    });
                  })()}
                  {tickValues.map((value, index) => {
                    const y = axisBottom - (value / range) * ySpan;
                    return (
                      <g key={`tick-${index}`}>
                        <line
                          x1={axisLeft}
                          y1={y}
                          x2={axisRight}
                          y2={y}
                          stroke="#cbd5e1"
                          strokeWidth="1"
                          opacity="0.45"
                        />
                        <text
                          x={axisLeft - 6}
                          y={y + 3}
                          textAnchor="end"
                          fontSize="9"
                          fill="#6b7280"
                        >
                          {formatAxisTickLabel(value, axis.step)}
                        </text>
                      </g>
                    );
                  })}
                  {monthTicks.map((tick, index) => (
                    <g key={`month-tick-${index}`}>
                      <line
                        x1={tick.x}
                        y1={axisBottom}
                        x2={tick.x}
                        y2={axisBottom + 4}
                        stroke="#94a3b8"
                        strokeWidth="1"
                      />
                      <text
                        x={tick.x}
                        y={axisBottom + 16}
                        textAnchor="middle"
                        fontSize="9"
                        fill="#6b7280"
                      >
                        {tick.label}
                      </text>
                    </g>
                  ))}
                  <line
                    x1={axisLeft}
                    y1={averageY}
                    x2={axisRight}
                    y2={averageY}
                    stroke="#94a3b8"
                    strokeDasharray="6 5"
                    strokeWidth="2"
                  />
                  {ysArea && (
                    <polygon
                      points={ysArea}
                      fill="#fda4af"
                      fillOpacity="0.04"
                    />
                  )}
                  {mgArea && (
                    <polygon
                      points={mgArea}
                      fill="#fcd34d"
                      fillOpacity="0.04"
                    />
                  )}
                  {ysCoords.length > 0 && (
                    <polyline
                      fill="none"
                      stroke="#e11d48"
                      strokeWidth="2"
                      strokeLinejoin="round"
                      strokeLinecap="round"
                      points={ysLine}
                    />
                  )}
                  {mgCoords.length > 0 && (
                    <polyline
                      fill="none"
                      stroke="#d97706"
                      strokeWidth="2"
                      strokeLinejoin="round"
                      strokeLinecap="round"
                      points={mgLine}
                    />
                  )}
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
                      fill={
                        hoveredHistoryPoint.point.marketplaceCode === "YS"
                          ? "#e11d48"
                          : "#d97706"
                      }
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

      <section className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <h3 className="display text-xl">Ayarlar</h3>
            <p className="text-xs text-[#6b655c]">
              Migros Money ve efektif fiyat tercihlerinizi secin.
            </p>
          </div>
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-3 rounded-2xl border border-black/10 bg-[#f9f4ee] px-2 py-1.5">
              <span className="px-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-[#6b655c]">
                Money Uyelik
              </span>
              <div className="inline-flex items-center rounded-xl border border-black/10 bg-white p-0.5">
                <button
                  type="button"
                  className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                    !userSettings.migrosMoneyMember
                      ? "bg-[#f3f4f6] text-[#111] shadow-sm"
                      : "text-[#6b655c]"
                  }`}
                  onClick={() => setMigrosMoneyMembership(false)}
                >
                  Pasif
                </button>
                <button
                  type="button"
                  className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                    userSettings.migrosMoneyMember
                      ? "bg-[#d97706] text-white shadow-sm"
                      : "text-[#6b655c]"
                  }`}
                  onClick={() => setMigrosMoneyMembership(true)}
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
                    !userSettings.considerEffectivePricing
                      ? "bg-[#f3f4f6] text-[#111] shadow-sm"
                      : "text-[#6b655c]"
                  }`}
                  onClick={() =>
                    setUserSettings((prev) => ({ ...prev, considerEffectivePricing: false }))
                  }
                >
                  Pasif
                </button>
                <button
                  type="button"
                  className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                    userSettings.considerEffectivePricing
                      ? "bg-emerald-600 text-white shadow-sm"
                      : "text-[#6b655c]"
                  }`}
                  onClick={() =>
                    setUserSettings((prev) => ({ ...prev, considerEffectivePricing: true }))
                  }
                >
                  Aktif
                </button>
              </div>
            </div>
          </div>
        </div>
        <p className="mt-3 text-xs text-[#6b655c]">
          Efektif fiyat acik oldugunda &quot;2 Ode 1&apos;i Money Hediye&quot; kampanyalari karar
          hesabina dahil edilir.
        </p>
      </section>

      <section className="grid gap-6">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] overflow-hidden">
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
                className={`rounded-2xl border border-black/5 bg-[#f9f4ee] p-4 transition overflow-hidden ${
                  expandedCategoryId === category.id
                    ? "shadow-[0_20px_40px_-35px_rgba(0,0,0,0.5)]"
                    : ""
                }`}
              >
                <div
                  role="button"
                  tabIndex={0}
                  className="relative flex min-h-8 cursor-pointer items-center rounded-xl px-1"
                  onClick={() => {
                    void handleToggleCategory(category);
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter" && event.key !== " ") {
                      return;
                    }
                    event.preventDefault();
                    void handleToggleCategory(category);
                  }}
                >
                  <p
                    className={`category-title text-lg transition-all ${
                      expandedCategoryId === category.id
                        ? "mx-auto text-center"
                        : "text-left"
                    }`}
                  >
                    {formatCategoryTitle(category.name)}
                  </p>
                  <div
                    className={`flex items-center gap-2 ${
                      expandedCategoryId === category.id ? "absolute right-0" : "ml-3"
                    }`}
                  >
                    <span className="rounded-full border border-black/10 bg-white px-3 py-1 text-xs text-[#6b655c]">
                      #{category.id}
                    </span>
                    <button
                      type="button"
                      className="rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                      onClick={(event) => {
                        event.stopPropagation();
                        onDeleteCategoryClick(category)();
                      }}
                      disabled={busy}
                      title="Kategoriyi sil"
                    >
                      Sil
                    </button>
                  </div>
                </div>

                {expandedCategoryId === category.id && (
                  <div className="mt-4 space-y-4">
                    {activeProducts ? (
                      <div className="grid gap-3 xl:grid-cols-2">
                        {marketplaceSections.map((section) => {
                        const sectionCandidatesRaw = activeCandidates.filter(
                          (item) => item.marketplaceCode === section.code
                        );
                        const sectionCandidateIndex = Object.fromEntries(
                          sectionCandidatesRaw.map((item, index) => [candidateKey(item), index])
                        );
                        const sectionCandidates = [...sectionCandidatesRaw].sort((left, right) => {
                            const leftMatched = suggestedMatchKeySet.has(candidateKey(left)) ? 0 : 1;
                            const rightMatched = suggestedMatchKeySet.has(candidateKey(right)) ? 0 : 1;
                            if (leftMatched !== rightMatched) {
                              return leftMatched - rightMatched;
                            }
                            const leftRank = suggestedMatchRank[candidateKey(left)] ?? Number.MAX_SAFE_INTEGER;
                            const rightRank = suggestedMatchRank[candidateKey(right)] ?? Number.MAX_SAFE_INTEGER;
                            if (leftRank !== rightRank) {
                              return leftRank - rightRank;
                            }
                            return (
                              (sectionCandidateIndex[candidateKey(left)] ?? Number.MAX_SAFE_INTEGER) -
                              (sectionCandidateIndex[candidateKey(right)] ?? Number.MAX_SAFE_INTEGER)
                            );
                          });
                        const suggestedListKey = `${category.id}:suggested:${section.code}`;
                        const addedListKey = `${category.id}:added:${section.code}`;
                        const sectionBusy = isMarketplaceBusy(category.id, section.code);
                        const sectionAddedRaw = addedProductsByMarketplace[section.code] ?? [];
                        const sectionAddedIndex = Object.fromEntries(
                          sectionAddedRaw.map((item, index) => [
                            `${item.marketplaceCode}:${item.externalId}`,
                            index,
                          ])
                        );
                        const sectionAddedProducts = [...sectionAddedRaw].sort((left, right) => {
                          const leftKey = `${left.marketplaceCode}:${left.externalId}`;
                          const rightKey = `${right.marketplaceCode}:${right.externalId}`;
                          const leftMatched = addedMatchKeySet.has(leftKey) ? 0 : 1;
                          const rightMatched = addedMatchKeySet.has(rightKey) ? 0 : 1;
                          if (leftMatched !== rightMatched) {
                            return leftMatched - rightMatched;
                          }
                          const leftRank = addedMatchRank[leftKey] ?? Number.MAX_SAFE_INTEGER;
                          const rightRank = addedMatchRank[rightKey] ?? Number.MAX_SAFE_INTEGER;
                          if (leftRank !== rightRank) {
                            return leftRank - rightRank;
                          }
                          return (
                            (sectionAddedIndex[leftKey] ?? Number.MAX_SAFE_INTEGER) -
                            (sectionAddedIndex[rightKey] ?? Number.MAX_SAFE_INTEGER)
                          );
                        });
                        const showAllAdded =
                          expandedLists[addedListKey] ||
                          sectionAddedProducts.length <= LIST_VISIBLE_COUNT;
                        const visibleAddedProducts = showAllAdded
                          ? sectionAddedProducts
                          : sectionAddedProducts.slice(0, LIST_VISIBLE_COUNT);
                        const showAllSuggested =
                          expandedLists[suggestedListKey] ||
                          sectionCandidates.length <= LIST_VISIBLE_COUNT;
                        const visibleSectionCandidates = showAllSuggested
                          ? sectionCandidates
                          : sectionCandidates.slice(0, LIST_VISIBLE_COUNT);
                        const panelToneClass =
                          section.code === "MG"
                            ? "border-amber-200 bg-amber-50/70"
                            : "border-rose-200 bg-rose-50/70";
                        return (
                        <div
                          key={section.code}
                          className={`rounded-2xl border p-3 ${panelToneClass}`}
                        >
                          <div className="text-center">
                            <div className="flex items-center justify-center gap-3">
                              <img
                                src={section.iconSrc}
                                alt={section.name}
                                className="h-5 w-5 rounded-sm object-contain"
                              />
                              <p className="display text-lg">{section.name}</p>
                            </div>
                            <p className="text-xs text-[#6b655c]">
                              {sectionAddedProducts.length} urun
                            </p>
                          </div>
                          <div className="mt-3 space-y-3">
                            <div>
                              <div className="flex items-center justify-between">
                                <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                                  Eklenen Urunler
                                </p>
                                <span className="text-xs text-[#6b655c]">
                                  {sectionAddedProducts.length} urun
                                </span>
                              </div>
                              <div
                                className={`${ADDED_PRODUCTS_DROPZONE_CLASS} ${
                                  activeDropZoneKey === addedListKey
                                    ? "ring-2 ring-[#d97706] ring-offset-2 ring-offset-white bg-[#ffeac5]"
                                    : ""
                                }`}
                                onDragOver={onCandidateDragOver}
                                onDragEnter={onCandidateDragEnter(addedListKey)}
                                onDragLeave={onCandidateDragLeave(addedListKey)}
                                onDrop={onCandidateDrop(category, section.code)}
                              >
                                {sectionAddedProducts.length === 0 ? (
                                  <p className="text-xs text-[#6b655c]">
                                    Bu marketplace icin urun yok. Onerilerden surukleyip buraya birakin.
                                  </p>
                                ) : (
                                  visibleAddedProducts.map((item, index) => (
                                    <div
                                      key={`${item.externalId}-${index}`}
                                      role="button"
                                      tabIndex={0}
                                      className={`relative flex h-[92px] w-full items-center gap-3 overflow-hidden rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 text-left transition hover:bg-white ${
                                        addedMatchKeySet.has(`${item.marketplaceCode}:${item.externalId}`)
                                          ? section.code === "YS"
                                            ? "border-rose-300 bg-rose-50/70"
                                            : "border-amber-300 bg-amber-50/70"
                                          : ""
                                      }`}
                                      onClick={onSelectAddedProductClick(item, category)}
                                      onKeyDown={onSelectAddedProductKeyDown(item, category)}
                                    >
                                      {addedMatchKeySet.has(
                                        `${item.marketplaceCode}:${item.externalId}`
                                      ) && (
                                        <span className="absolute right-2 top-1 inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-800">
                                          <svg viewBox="0 0 16 16" className="h-3 w-3" aria-hidden>
                                            <path
                                              d="M6.2 5.2 4.7 3.7a2.4 2.4 0 1 0-3.4 3.4l1.5 1.5a2.4 2.4 0 0 0 3.4 0m3.6-2.4 1.5-1.5a2.4 2.4 0 1 1 3.4 3.4l-1.5 1.5a2.4 2.4 0 0 1-3.4 0M5.1 10.9l5.8-5.8"
                                              fill="none"
                                              stroke="currentColor"
                                              strokeWidth="1.5"
                                              strokeLinecap="round"
                                              strokeLinejoin="round"
                                            />
                                          </svg>
                                          <span>
                                            Eslesme #
                                            {(addedMatchRank[
                                              `${item.marketplaceCode}:${item.externalId}`
                                            ] ?? 0) + 1}
                                          </span>
                                        </span>
                                      )}
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
                                        <p className="truncate text-sm font-semibold text-[#111]">
                                          {item.name || `Urun ${item.externalId}`}
                                        </p>
                                        <p className="truncate text-xs text-[#554f47]">
                                          {item.brandName || "Marka yok"}
                                          {formatMarketplacePriceSuffix(
                                            item.price,
                                            item.moneyPrice,
                                            item.effectivePrice,
                                            item.campaignBuyQuantity,
                                            item.campaignPayQuantity,
                                            item.marketplaceCode,
                                            userSettings.migrosMoneyMember,
                                            userSettings.considerEffectivePricing
                                          )}
                                        </p>
                                        {isMoneyDisplayPrice(
                                          item.marketplaceCode,
                                          item.price,
                                          item.moneyPrice,
                                          item.effectivePrice,
                                          item.campaignBuyQuantity,
                                          item.campaignPayQuantity,
                                          userSettings.migrosMoneyMember,
                                          userSettings.considerEffectivePricing
                                        ) && (
                                          <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                                            <img
                                              src="/migros-money.png"
                                              alt="Money"
                                              className="h-3 w-3 object-contain"
                                            />
                                            <span>Money ile</span>
                                            <span>{formatPrice(item.moneyPrice)}</span>
                                          </div>
                                        )}
                                        {hasBasketDiscount(
                                          item.marketplaceCode,
                                          item.basketDiscountThreshold,
                                          item.basketDiscountPrice
                                        ) && (
                                          <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                                            <span>
                                              Sepette {formatTl(item.basketDiscountThreshold, 0)}
                                            </span>
                                            <span>{formatTl(item.basketDiscountPrice)}</span>
                                          </div>
                                        )}
                                        {hasEffectiveCampaign(
                                          item.marketplaceCode,
                                          item.price,
                                          item.moneyPrice,
                                          item.campaignBuyQuantity,
                                          item.campaignPayQuantity,
                                          item.effectivePrice
                                        ) && (
                                          <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                                            <span>
                                              {formatEffectiveCampaignBadge(
                                                item.campaignBuyQuantity,
                                                item.campaignPayQuantity
                                              )}
                                            </span>
                                          </div>
                                        )}
                                      </div>
                                      <button
                                        type="button"
                                        className="flex h-9 w-9 items-center justify-center rounded-full border border-black/10 bg-white text-lg font-semibold text-[#9a5c00] transition hover:bg-amber-50"
                                        onClick={onRemoveAddedProductClick(category, item, section.code)}
                                        disabled={sectionBusy}
                                        title="Urun sil"
                                      >
                                        -
                                      </button>
                                    </div>
                                  ))
                                )}
                                {sectionAddedProducts.length > LIST_VISIBLE_COUNT && (
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
                              marketplaceCode={section.code}
                              bridgeSide={section.code === "YS" ? "right" : "left"}
                              sectionCandidates={sectionCandidates}
                              visibleSectionCandidates={visibleSectionCandidates}
                              matchedCandidateKeys={suggestedMatchKeySet}
                              suggestedListKey={suggestedListKey}
                              isMigrosMoneyMember={userSettings.migrosMoneyMember}
                              considerEffectivePricing={userSettings.considerEffectivePricing}
                              candidateBusy={sectionBusy}
                              addedCandidateIds={addedCandidateIds}
                              addedCandidateKeys={addedCandidateKeys}
                              expandedLists={expandedLists}
                              onAddCandidate={handleAddCandidateProduct}
                              onToggleList={toggleList}
                              onDragStartCandidate={onCandidateDragStart}
                            />
                          </div>
                        </div>
                      );
                        })}
                      </div>
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
                {showAllCategories ? "Daha Az Kategori Goster" : "Daha Fazla Kategori Gor"}
              </button>
            )}
          </div>
        </div>

      </section>

      {showProductInfoModal && selectedAddedProduct && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4"
          onClick={() => setShowProductInfoModal(false)}
        >
          <div
            className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-black/10 bg-white p-5 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.55)]"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className="display text-xl">Urun Bilgisi</h3>
              <button
                className="text-sm text-[#6b655c] transition hover:text-[#111]"
                type="button"
                onClick={() => setShowProductInfoModal(false)}
              >
                Kapat
              </button>
            </div>
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
                    {selectedAddedProduct.brandName || "Marka yok"}
                  </p>
                  <p className="text-xs text-[#6b655c]">
                    {formatMarketplacePriceLabel(
                      selectedAddedProduct.price,
                      selectedAddedProduct.moneyPrice,
                      selectedAddedProduct.effectivePrice,
                      selectedAddedProduct.campaignBuyQuantity,
                      selectedAddedProduct.campaignPayQuantity,
                      selectedAddedProduct.marketplaceCode,
                      userSettings.migrosMoneyMember,
                      userSettings.considerEffectivePricing
                    )}
                  </p>
                  {isMoneyDisplayPrice(
                    selectedAddedProduct.marketplaceCode,
                    selectedAddedProduct.price,
                    selectedAddedProduct.moneyPrice,
                    selectedAddedProduct.effectivePrice,
                    selectedAddedProduct.campaignBuyQuantity,
                    selectedAddedProduct.campaignPayQuantity,
                    userSettings.migrosMoneyMember,
                    userSettings.considerEffectivePricing
                  ) && (
                    <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                      <img
                        src="/migros-money.png"
                        alt="Money"
                        className="h-3 w-3 object-contain"
                      />
                      <span>Money ile</span>
                      <span>{formatPrice(selectedAddedProduct.moneyPrice)}</span>
                    </div>
                  )}
                  {hasBasketDiscount(
                    selectedAddedProduct.marketplaceCode,
                    selectedAddedProduct.basketDiscountThreshold,
                    selectedAddedProduct.basketDiscountPrice
                  ) && (
                    <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                      <span>
                        Sepette {formatTl(selectedAddedProduct.basketDiscountThreshold, 0)}
                      </span>
                      <span>{formatTl(selectedAddedProduct.basketDiscountPrice)}</span>
                    </div>
                  )}
                  {hasEffectiveCampaign(
                    selectedAddedProduct.marketplaceCode,
                    selectedAddedProduct.price,
                    selectedAddedProduct.moneyPrice,
                    selectedAddedProduct.campaignBuyQuantity,
                    selectedAddedProduct.campaignPayQuantity,
                    selectedAddedProduct.effectivePrice
                  ) && (
                    <p className="mt-1 text-[11px] font-semibold text-emerald-700">
                      Kampanya:{" "}
                      {formatEffectiveCampaignBadge(
                        selectedAddedProduct.campaignBuyQuantity,
                        selectedAddedProduct.campaignPayQuantity
                      )}{" "}
                      - Efektif{" "}
                      {formatTl(
                        resolveEffectivePriceValue(
                          selectedAddedProduct.price,
                          selectedAddedProduct.moneyPrice,
                          selectedAddedProduct.effectivePrice,
                          selectedAddedProduct.campaignBuyQuantity,
                          selectedAddedProduct.campaignPayQuantity,
                          userSettings.migrosMoneyMember
                        )
                      )}
                    </p>
                  )}
                </div>
              </div>
              <div className="mt-3 grid gap-2 text-xs text-[#6b655c]">
                <div className="flex items-center gap-2 rounded-xl border border-black/5 bg-white px-3 py-2">
                  <span>Marketler:</span>
                  <span
                    className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 ${
                      selectedAddedProduct.marketplaceCode === "YS"
                        ? "border-rose-200 bg-rose-50 text-rose-700"
                        : "border-black/10 bg-white text-[#6b655c]"
                    }`}
                  >
                    <img
                      src="/yemeksepeti-logo.png"
                      alt="Yemeksepeti"
                      className="h-4 w-4 rounded-sm object-contain"
                    />
                    <span>Yemeksepeti</span>
                  </span>
                  <span
                    className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 ${
                      selectedAddedProduct.marketplaceCode === "MG"
                        ? "border-amber-200 bg-amber-50 text-amber-700"
                        : "border-black/10 bg-white text-[#6b655c]"
                    }`}
                  >
                    <img
                      src="/migros-logo.png"
                      alt="Migros"
                      className="h-4 w-4 rounded-sm object-contain"
                    />
                    <span>Migros</span>
                  </span>
                </div>
                <div className="flex items-center justify-between rounded-xl border border-black/5 bg-white px-3 py-2">
                  <span>Firsat Durumu:</span>
                  <span className="font-semibold text-[#111]">{selectedOpportunityLevel}</span>
                </div>
                <div className="rounded-xl border border-black/5 bg-white px-3 py-2">
                  <p className="text-[11px] uppercase tracking-[0.18em] text-[#9a5c00]">
                    Bugun Alim Durumu
                  </p>
                  <p
                    className={`mt-1 text-sm font-semibold ${
                      selectedTodayRecommendation.label === "Bugun Alinabilir"
                        ? "text-emerald-700"
                        : "text-rose-700"
                    }`}
                  >
                    {selectedTodayRecommendation.label}
                  </p>
                  <p className="mt-1 text-[11px] text-[#6b655c]">
                    {selectedTodayRecommendation.reason}
                  </p>
                </div>
                <div className="rounded-xl border border-black/5 bg-white px-3 py-2">
                  <p className="text-[11px] uppercase tracking-[0.18em] text-[#9a5c00]">
                    Ihtiyac Aciliyeti
                  </p>
                  <div className="mt-2 inline-flex items-center rounded-xl border border-black/10 bg-[#f9f4ee] p-1">
                    {(["VERY_URGENT", "URGENT", "NOT_URGENT"] as NeedUrgency[]).map(
                      (urgency) => (
                        <button
                          key={urgency}
                          type="button"
                          className={`rounded-lg px-2 py-1 text-[11px] font-semibold transition ${
                            selectedNeedUrgency === urgency
                              ? "bg-[#d97706] text-white shadow-sm"
                              : "text-[#6b655c]"
                          }`}
                          onClick={() => setSelectedNeedUrgency(urgency)}
                        >
                          {urgencyLabel(urgency)}
                        </button>
                      )
                    )}
                  </div>
                  <button
                    type="button"
                    className="mt-2 rounded-xl border border-black/10 bg-white px-3 py-1.5 text-xs font-semibold text-[#9a5c00] transition hover:bg-amber-50"
                    onClick={handleAddSelectedToNeedList}
                  >
                    {selectedInNeedList
                      ? "Ihtiyac Listesinde Guncelle"
                      : "Ihtiyac Listesine Ekle"}
                  </button>
                  {activeCategory && (
                    <button
                      type="button"
                      className="ml-2 mt-2 rounded-xl border border-black/10 bg-[#f9f4ee] px-3 py-1.5 text-xs font-semibold text-[#6b655c] transition hover:bg-amber-50"
                      onClick={handleAddActiveCategoryToNeedList}
                    >
                      Kategoriyi Ihtiyac Listesine Ekle
                    </button>
                  )}
                </div>
              </div>
              <div className="mt-4 rounded-2xl border border-black/5 bg-white p-3">
                <div className="flex items-center justify-between">
                  <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                    Fiyat Gecmisi
                  </p>
                  <span className="text-xs text-[#6b655c]">
                    {rangeFilteredHistory.length}/{compactHistory.length} kayit
                  </span>
                </div>
                {historyContent}
              </div>
            </div>
          </div>
        </div>
      )}

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

      <div className="fixed right-6 top-6 z-50 flex items-start gap-2">
        <div className="group relative">
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
            onClick={() => {
              window.location.href = "/needs";
            }}
            title="Ihtiyac Listesi"
          >
            📝
          </button>
          <div className="absolute right-0 top-12 hidden w-72 rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] group-hover:block">
            <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
              Ihtiyac Listesi
            </p>
            <div className="mt-2 space-y-1">
              {needList.length === 0 ? (
                <p className="text-[#6b655c]">Liste bos.</p>
              ) : (
                needList.slice(0, 5).map((item) => (
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
        </div>
        <div className="group relative">
          <button
            type="button"
            className="relative flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
            onClick={() => {
              window.location.href = "/basket";
            }}
            title="Sepet Onerileri"
          >
            🛒
            {basketSuggestionCount > 0 && (
              <span className="absolute -bottom-1 -right-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[#d97706] px-1 text-[10px] font-semibold text-white">
                {basketSuggestionCount}
              </span>
            )}
          </button>
          <div className="absolute right-0 top-12 hidden w-80 rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] group-hover:block">
            <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
              Sepet Onerileri
            </p>
            <div className="mt-2 space-y-1">
              {basketSuggestionCount === 0 ? (
                <p className="text-[#6b655c]">Su an onerilen urun yok.</p>
              ) : (
                <>
                  {suggestedBasketItems.slice(0, 3).map((item) => (
                    <p key={`basket-preview-${item.key}`} className="text-[#14532d]">
                      {item.name}
                    </p>
                  ))}
                  {autoOpportunitySuggestions.slice(0, 3).map((item) => (
                    <p key={`basket-auto-preview-${item.key}`} className="text-[#14532d]">
                      {item.name}
                    </p>
                  ))}
                </>
              )}
            </div>
            <button
              type="button"
              className="mt-2 rounded-lg border border-black/10 bg-[#f9f4ee] px-2 py-1 text-[11px] text-[#6b655c]"
              onClick={() => {
                window.location.href = "/basket";
              }}
            >
              Tum Sepeti Gor
            </button>
          </div>
        </div>
      </div>

      <div className="pointer-events-none fixed right-6 top-24 z-50 flex w-[320px] flex-col gap-2">
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
