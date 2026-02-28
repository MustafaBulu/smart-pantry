"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { request, requestForMarketplace } from "@/lib/api";
import {
  buildNicePriceAxis,
  formatAxisTickLabel,
  formatCategoryTitle,
  formatEffectiveCampaignBadge,
  formatMarketplacePriceLabel,
  formatMarketplacePriceSuffix,
  formatMonthTick,
  formatPrice,
  formatTl,
  hasBasketDiscount,
  hasEffectiveCampaign,
  isMoneyDisplayPrice,
  resolveAvailabilityStatus,
  resolveEffectivePriceValue,
  resolveThresholdPrice,
  urgencyLabel,
  type AvailabilityStatus,
  type NeedUrgency,
} from "@/lib/ui/pricing";
import Image from "next/image";
import type {
  BasketMinimumSettingsResponse,
  Category,
  MarketplaceProductAddedResponse,
  MarketplaceProductCandidateResponse,
  MarketplaceProductEntryResponse,
  NeedListItemDto,
  PriceHistoryResponse,
  ProductResponse,
} from "@/lib/types";
import ProductInfoModal from "@/components/ProductInfoModal";

type CategoryProducts = {
  YS: ProductResponse[];
  MG: ProductResponse[];
};

type MarketplaceCode = "YS" | "MG";

type Notice = { id: string; message: string; tone: "success" | "error" | "info" };
type CustomerLocation = {
  city: string;
  district: string;
  latitude: number | null;
  longitude: number | null;
};
type UserSettings = {
  migrosMoneyMember: boolean;
  considerEffectivePricing: boolean;
  ysMinimumBasketAmount: number | null;
  mgMinimumBasketAmount: number | null;
  customerLocation: CustomerLocation;
};
type HistoryPoint = {
  recordedAt: string;
  price: number;
  marketplaceCode: string;
  availabilityScore: number | null;
  opportunityLevel: string | null;
};
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
  historyDayCount: number | null;
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

const asMarketplaceCode = (value: string): MarketplaceCode | null => {
  const normalized = value.trim().toUpperCase();
  if (normalized === "YS" || normalized === "MG") {
    return normalized;
  }
  return null;
};

const CATEGORY_VISIBLE_COUNT = 3;
const CATEGORY_PAGE_SIZE = 5;
const LIST_VISIBLE_COUNT = 6;
const MAX_OPPORTUNITY_TARGETS = 20;
const MATCH_MIN_SCORE = 0.76;
const CANDIDATE_DRAG_TYPE = "application/x-smart-pantry-candidate";
const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";
const OPPORTUNITY_FEED_STORAGE_KEY = "smart-pantry:opportunity-feed";
const DEFAULT_MIGROS_BASKET_THRESHOLD = 50;
const DEFAULT_USER_SETTINGS: UserSettings = {
  migrosMoneyMember: false,
  considerEffectivePricing: false,
  ysMinimumBasketAmount: null,
  mgMinimumBasketAmount: null,
  customerLocation: {
    city: "",
    district: "",
    latitude: null,
    longitude: null,
  },
};

const parseNullableNumber = (value: unknown): number | null => {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }
  return value;
};
const ADDED_PRODUCTS_DROPZONE_CLASS =
  "mt-2 space-y-2 rounded-2xl border-2 border-dashed border-[#374151] bg-[#fff4e0] p-2 shadow-[0_12px_30px_-20px_rgba(217,119,6,0.7)]";

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
  const comboMatch = lower.match(/(\d+)\s*[xX]\s*(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|lt|l)\b/);
  const packMatch = lower.match(/(\d+)\s*['']?(li|lu|lü|pack|paket)\b/);
  const amountMatch = lower.match(/(\d+(?:[.,]\d+)?)\s*(kg|gr|g|ml|lt|l)\b/);
  let amount: number | null = null;
  let unit: "g" | "ml" | null = null;
  if (comboMatch) {
    const rawAmount = Number.parseFloat(comboMatch[2].replace(",", "."));
    const rawUnit = comboMatch[3];
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
    return {
      amount,
      unit,
      packCount: Number.parseInt(comboMatch[1], 10),
    };
  }
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
  if ((left.packCount === null) !== (right.packCount === null)) {
    return { compatible: false, score: 0 };
  }
  if (left.packCount !== null && right.packCount !== null && left.packCount !== right.packCount) {
    return { compatible: false, score: 0 };
  }
  if ((left.unit === null) !== (right.unit === null)) {
    return { compatible: false, score: 0 };
  }
  if ((left.amount === null) !== (right.amount === null)) {
    return { compatible: false, score: 0 };
  }
  if (left.unit && right.unit) {
    if (left.unit !== right.unit) {
      return { compatible: false, score: 0 };
    }
    if (left.amount !== null && right.amount !== null) {
      const ratio = Math.min(left.amount, right.amount) / Math.max(left.amount, right.amount);
      if (ratio < 0.98) {
        return { compatible: false, score: 0 };
      }
      return { compatible: true, score: 1 };
    }
  }
  return { compatible: true, score: 0.85 };
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
  const leftRaw = (leftUrl ?? "").trim();
  const rightRaw = (rightUrl ?? "").trim();
  if (!leftRaw || !rightRaw) {
    return 0;
  }
  if (leftRaw === rightRaw) {
    return 1;
  }
  const left = imageFingerprint(leftRaw);
  const right = imageFingerprint(rightRaw);
  if (!left || !right) {
    return 0;
  }
  if (left === right) {
    return 1;
  }
  if (left.includes(right) || right.includes(left)) {
    return 0.9;
  }
  const leftTokens = new Set(left.split(/[^a-z0-9]+/).filter((token) => token.length > 2));
  const rightTokens = new Set(right.split(/[^a-z0-9]+/).filter((token) => token.length > 2));
  const tokenScore = jaccardSimilarity(leftTokens, rightTokens);
  if (tokenScore > 0) {
    return Math.min(0.85, 0.45 + tokenScore * 0.4);
  }
  return left.slice(0, 10) === right.slice(0, 10) ? 0.6 : 0;
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
  phraseScore: number;
  quantityScore: number;
  brandScore: number;
  imageScore: number;
  priceScore: number;
  profileScore: number;
};

type CandidatePair = {
  ys: MarketplaceProductCandidateResponse;
  mg: MarketplaceProductCandidateResponse;
  score: CandidateMatchScore;
};

const isStrongNameMatch = (
  source: MarketplaceProductCandidateResponse,
  target: MarketplaceProductCandidateResponse
) => normalizeProductName(source.name || "") === normalizeProductName(target.name || "");

const shouldAutoLinkCandidates = (
  match: {
    score: number;
    nameScore: number;
    coreNameScore: number;
    phraseScore: number;
    quantityScore: number;
    brandScore: number;
    imageScore: number;
    priceScore: number;
    profileScore: number;
  },
  source: MarketplaceProductCandidateResponse,
  target: MarketplaceProductCandidateResponse
) => {
  if (
    isStrongNameMatch(source, target) &&
    match.quantityScore >= 0.95 &&
    match.brandScore >= 0.7 &&
    match.priceScore >= 0.45 &&
    match.profileScore >= 0.65 &&
    match.score >= 0.8
  ) {
    return true;
  }
  return (
    match.score >= 0.82 &&
    match.nameScore >= 0.6 &&
    match.coreNameScore >= 0.52 &&
    match.phraseScore >= 0.55 &&
    match.quantityScore >= 0.95 &&
    match.brandScore >= 0.6 &&
    match.priceScore >= 0.4 &&
    match.profileScore >= 0.7
  );
};

const normalizeMatchText = (value: string) =>
  value
    .toLocaleLowerCase("tr-TR")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\s+/g, " ")
    .trim();

const GENERIC_BRAND_TOKENS = new Set([
  "sut",
  "icme",
  "uht",
  "pastorize",
  "gunluk",
  "yagli",
  "organik",
  "proteinli",
  "laktozsuz",
]);

const normalizeBrand = (brand: string) => {
  const normalized = normalizeMatchText(brand);
  if (!normalized || normalized === "marka yok") {
    return "";
  }
  return normalized;
};

const inferBrand = (candidate: MarketplaceProductCandidateResponse) => {
  const explicit = normalizeBrand(candidate.brandName || "");
  if (explicit) {
    return explicit;
  }
  const firstToken = normalizeMatchText(candidate.name || "").split(" ")[0] ?? "";
  if (!firstToken || GENERIC_BRAND_TOKENS.has(firstToken)) {
    return "";
  }
  return firstToken;
};

const leadingPhraseScore = (leftName: string, rightName: string) => {
  const left = normalizeMatchText(leftName).split(" ").filter(Boolean);
  const right = normalizeMatchText(rightName).split(" ").filter(Boolean);
  if (left.length === 0 || right.length === 0) {
    return 0;
  }
  const left1 = left[0];
  const right1 = right[0];
  const left2 = left.slice(0, 2).join(" ");
  const right2 = right.slice(0, 2).join(" ");
  if (left2 && right2 && left2 === right2) {
    return 1;
  }
  if (left1 === right1) {
    return 0.9;
  }
  if (left1.startsWith(right1) || right1.startsWith(left1)) {
    return 0.7;
  }
  const leftHead = new Set(left.slice(0, 3));
  const rightHead = new Set(right.slice(0, 3));
  return jaccardSimilarity(leftHead, rightHead) * 0.7;
};

const phraseSimilarity = (leftName: string, rightName: string) => {
  const left = normalizeProductName(leftName).split(" ").filter((token) => token.length > 1);
  const right = normalizeProductName(rightName).split(" ").filter((token) => token.length > 1);
  if (left.length < 2 || right.length < 2) {
    return 0;
  }
  const leftBigrams = new Set<string>();
  const rightBigrams = new Set<string>();
  for (let i = 0; i < left.length - 1; i += 1) {
    leftBigrams.add(`${left[i]} ${left[i + 1]}`);
  }
  for (let i = 0; i < right.length - 1; i += 1) {
    rightBigrams.add(`${right[i]} ${right[i + 1]}`);
  }
  return jaccardSimilarity(leftBigrams, rightBigrams);
};

const resolveComparablePrice = (candidate: MarketplaceProductCandidateResponse) => {
  const candidates = [
    candidate.effectivePrice,
    candidate.basketDiscountPrice,
    candidate.moneyPrice,
    candidate.price,
  ].filter((value): value is number => typeof value === "number" && Number.isFinite(value) && value > 0);
  if (candidates.length === 0) {
    return null;
  }
  return Math.min(...candidates);
};

const comparePriceConsistency = (
  source: MarketplaceProductCandidateResponse,
  target: MarketplaceProductCandidateResponse
) => {
  const left = resolveComparablePrice(source);
  const right = resolveComparablePrice(target);
  if (left === null || right === null) {
    return { compatible: true, score: 0.55 };
  }
  const ratio = Math.max(left, right) / Math.min(left, right);
  if (ratio > 2.2) {
    return { compatible: false, score: 0 };
  }
  if (ratio <= 1.15) {
    return { compatible: true, score: 1 };
  }
  if (ratio <= 1.35) {
    return { compatible: true, score: 0.85 };
  }
  if (ratio <= 1.6) {
    return { compatible: true, score: 0.65 };
  }
  if (ratio <= 2.0) {
    return { compatible: true, score: 0.45 };
  }
  return { compatible: true, score: 0.25 };
};

type MatchProfile = {
  lactoseFree: boolean;
  organic: boolean;
  protein: boolean;
  goatMilk: boolean;
  flavor: string | null;
  fatPercent: number | null;
  fatClass: "LOW" | "HALF" | "FULL" | null;
  uht: boolean;
  pasteurized: boolean;
  daily: boolean;
  bottle: boolean;
};

const parseFatPercent = (normalizedText: string) => {
  const match = normalizedText.match(/%?\s*(\d+(?:[.,]\d+)?)\s*yag/);
  if (!match) {
    return null;
  }
  const parsed = Number.parseFloat(match[1].replace(",", "."));
  return Number.isFinite(parsed) ? parsed : null;
};

const extractMatchProfile = (candidate: MarketplaceProductCandidateResponse): MatchProfile => {
  const text = normalizeMatchText(candidate.name || "");
  const fatPercent = parseFatPercent(text);
  return {
    lactoseFree: text.includes("laktozsuz") || text.includes("rahat"),
    organic: text.includes("organik"),
    protein: text.includes("protein"),
    goatMilk: text.includes("keci"),
    flavor: text.includes("kakaolu")
      ? "kakao"
      : text.includes("cilekli")
        ? "cilek"
        : text.includes("muzlu")
          ? "muz"
          : text.includes("latte")
            ? "latte"
            : text.includes("kahveli")
              ? "kahve"
              : null,
    fatPercent,
    fatClass: text.includes("tam yagli")
      ? "FULL"
      : text.includes("yarim yagli")
        ? "HALF"
        : text.includes("az yagli") || text.includes("0,5") || text.includes("0.5")
          ? "LOW"
          : null,
    uht: text.includes("uht"),
    pasteurized: text.includes("pastorize"),
    daily: text.includes("gunluk"),
    bottle: text.includes("sise"),
  };
};

const resolveFatScore = (left: MatchProfile, right: MatchProfile) => {
  if (left.fatPercent !== null && right.fatPercent !== null) {
    const diff = Math.abs(left.fatPercent - right.fatPercent);
    if (diff > 0.8) {
      return { compatible: false, score: 0 };
    }
    if (diff <= 0.2) {
      return { compatible: true, score: 1 };
    }
    if (diff <= 0.5) {
      return { compatible: true, score: 0.75 };
    }
    return { compatible: true, score: 0.5 };
  }
  if (left.fatClass && right.fatClass) {
    return left.fatClass === right.fatClass
      ? { compatible: true, score: 1 }
      : { compatible: false, score: 0 };
  }
  return { compatible: true, score: 0.6 };
};

const compareProfiles = (left: MatchProfile, right: MatchProfile) => {
  if (left.goatMilk !== right.goatMilk) {
    return { compatible: false, score: 0 };
  }
  if (left.lactoseFree !== right.lactoseFree) {
    return { compatible: false, score: 0 };
  }
  if (left.organic !== right.organic) {
    return { compatible: false, score: 0 };
  }
  if (left.protein !== right.protein) {
    return { compatible: false, score: 0 };
  }
  if ((left.flavor === null) !== (right.flavor === null)) {
    return { compatible: false, score: 0 };
  }
  if (left.flavor !== null && right.flavor !== null && left.flavor !== right.flavor) {
    return { compatible: false, score: 0 };
  }

  const fat = resolveFatScore(left, right);
  if (!fat.compatible) {
    return { compatible: false, score: 0 };
  }

  let processScore = 0.6;
  if (left.uht && right.uht) {
    processScore += 0.15;
  }
  if (left.pasteurized && right.pasteurized) {
    processScore += 0.15;
  }
  if (left.daily && right.daily) {
    processScore += 0.1;
  }
  if (left.bottle && right.bottle) {
    processScore += 0.1;
  }
  processScore = Math.min(processScore, 1);
  return { compatible: true, score: (fat.score * 0.65) + (processScore * 0.35) };
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
  const phraseScore = Math.max(
    leadingPhraseScore(source.name || "", target.name || ""),
    phraseSimilarity(source.name || "", target.name || "")
  );
  if ((nameScore < 0.55 && coreNameScore < 0.45) || phraseScore < 0.35) {
    return null;
  }
  const explicitSourceBrand = normalizeBrand(source.brandName || "");
  const explicitTargetBrand = normalizeBrand(target.brandName || "");
  if (explicitSourceBrand && explicitTargetBrand && explicitSourceBrand !== explicitTargetBrand) {
    return null;
  }
  const inferredSourceBrand = inferBrand(source);
  const inferredTargetBrand = inferBrand(target);
  const brandScore = inferredSourceBrand && inferredTargetBrand
    ? inferredSourceBrand === inferredTargetBrand
      ? 1
      : 0.1
    : brandSimilarity(source.brandName || "", target.brandName || "");
  if (brandScore < 0.1) {
    return null;
  }
  const quantity = compareQuantity(source.name || "", target.name || "");
  if (!quantity.compatible) {
    return null;
  }
  const price = comparePriceConsistency(source, target);
  if (!price.compatible) {
    return null;
  }
  const profile = compareProfiles(extractMatchProfile(source), extractMatchProfile(target));
  if (!profile.compatible) {
    return null;
  }
  const imageScore = imageSimilarity(source.imageUrl || "", target.imageUrl || "");
  const score =
    nameScore * 0.18 +
    coreNameScore * 0.14 +
    phraseScore * 0.18 +
    quantity.score * 0.2 +
    brandScore * 0.14 +
    imageScore * 0.08 +
    price.score * 0.04 +
    profile.score * 0.04;
  return {
    score,
    nameScore,
    coreNameScore,
    phraseScore,
    quantityScore: quantity.score,
    brandScore,
    imageScore,
    priceScore: price.score,
    profileScore: profile.score,
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

const addedToCandidate = (
  item: MarketplaceProductAddedResponse
): MarketplaceProductCandidateResponse => ({
  marketplaceCode: item.marketplaceCode,
  externalId: item.externalId,
  sku: "",
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

const resolveCandidateDisplayPrice = (item: MarketplaceProductCandidateResponse): number =>
  Number(
    (
      item.effectivePrice ??
      item.basketDiscountPrice ??
      item.moneyPrice ??
      item.price ??
      Number.MAX_SAFE_INTEGER
    )
  );

const resolveEntryDisplayPrice = (item: MarketplaceProductEntryResponse): number =>
  Number(
    (
      item.effectivePrice ??
      item.basketDiscountPrice ??
      item.moneyPrice ??
      item.price ??
      Number.MAX_SAFE_INTEGER
    )
  );

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
  sectionCandidates: MarketplaceProductCandidateResponse[];
  visibleSectionCandidates: MarketplaceProductCandidateResponse[];
  matchedCandidateKeys: Set<string>;
  suggestedListKey: string;
  isMigrosMoneyMember: boolean;
  considerEffectivePricing: boolean;
  candidateBusy: boolean;
  addedCandidateIds: Set<string>;
  expandedLists: Record<string, boolean>;
  onAddCandidate: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse,
    isMatched: boolean
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
  sectionCandidates,
  visibleSectionCandidates,
  matchedCandidateKeys,
  suggestedListKey,
  isMigrosMoneyMember,
  considerEffectivePricing,
  candidateBusy,
  addedCandidateIds,
  expandedLists,
  onAddCandidate,
  onToggleList,
  onDragStartCandidate,
}: SuggestedProductsProps) {
  let suggestedContent: ReactNode;
  if (candidateBusy && sectionCandidates.length === 0) {
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
          const itemKey = candidateKey(item);
          const isAdded = addedCandidateIds.has(itemKey);
          const isMatched = matchedCandidateKeys.has(itemKey);
          const matchedTone =
            marketplaceCode === "YS"
              ? "border-rose-300 bg-rose-50/70"
              : "border-amber-300 bg-amber-50/70";
          return (
            <div
              key={`${item.externalId}-${index}`}
              className={`relative grid h-[92px] grid-cols-[40px_minmax(0,1fr)_36px] items-center gap-3 rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 ${
                isMatched ? matchedTone : ""
              } ${
                isAdded ? "opacity-40" : "cursor-grab active:cursor-grabbing"
              }`}
              draggable={!isAdded && !candidateBusy}
              onDragStart={(event) => onDragStartCandidate(event, item)}
            >
              {isMatched && (
                <span className="absolute right-2 top-1 inline-flex items-center rounded-full border border-emerald-300 bg-emerald-100 p-1 text-[10px] font-semibold text-emerald-800">
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
                </span>
              )}
              <div className="relative h-10 w-10 overflow-hidden rounded-xl border border-black/10 bg-white">
                {item.imageUrl ? (
                  <Image src={item.imageUrl} alt={item.name} fill sizes="40px" className="object-cover" />
                ) : (
                  <div className="flex h-full w-full items-center justify-center text-[10px] text-[#6b655c]">
                    Gorsel yok
                  </div>
                )}
              </div>
              <div className="min-w-0">
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
                    <Image src="/migros-money.png" alt="Money" width={12} height={12} className="h-3 w-3 object-contain" />
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
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#d97706] text-lg font-semibold text-white transition hover:bg-[#b45309]"
                  onClick={() => onAddCandidate(category, item, isMatched)}
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
  const [categoryFilterQuery, setCategoryFilterQuery] = useState("");
  const [showAddCategory, setShowAddCategory] = useState(false);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [categoryPage, setCategoryPage] = useState(0);
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
  const [expandedLists, setExpandedLists] = useState<Record<string, boolean>>({});
  const [pendingScrollId, setPendingScrollId] = useState<number | null>(null);
  const categoryRefs = useRef<Map<number, HTMLDivElement>>(new Map());
  const [selectedAddedProduct, setSelectedAddedProduct] =
    useState<MarketplaceProductEntryResponse | null>(null);
  const [showProductInfoModal, setShowProductInfoModal] = useState(false);
  const [selectedProductCategory, setSelectedProductCategory] = useState<Category | null>(null);
  const [selectedProductHistory, setSelectedProductHistory] = useState<HistoryPoint[]>([]);
  const [needList, setNeedList] = useState<NeedListItem[]>([]);
  const [needListLoaded, setNeedListLoaded] = useState(false);
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
  const [isCategoryListCollapsed, setIsCategoryListCollapsed] = useState(false);
  const [isInfoSectionCollapsed, setIsInfoSectionCollapsed] = useState(false);
  const [totalMarketplaceProductCount, setTotalMarketplaceProductCount] = useState(0);
  const [matchedMarketplaceProductCount, setMatchedMarketplaceProductCount] = useState(0);
  const [userSettings, setUserSettings] = useState<UserSettings>(
    DEFAULT_USER_SETTINGS
  );
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const settingsMenuTimeoutRef = useRef<number | null>(null);
  const [headerActionsEl, setHeaderActionsEl] = useState<HTMLElement | null>(null);
  const addFlowLocksRef = useRef<Set<string>>(new Set());
  const marketplaceCategoryIdCacheRef = useRef<Record<string, number | null>>({});
  const marketplaceCategoryIdInFlightRef = useRef<Record<string, Promise<number | null>>>({});

  const clearSettingsMenuTimeout = () => {
    if (settingsMenuTimeoutRef.current !== null) {
      window.clearTimeout(settingsMenuTimeoutRef.current);
      settingsMenuTimeoutRef.current = null;
    }
  };

  const scheduleSettingsMenuClose = () => {
    clearSettingsMenuTimeout();
    settingsMenuTimeoutRef.current = window.setTimeout(() => {
      setShowSettingsMenu(false);
    }, 2500);
  };

  const removeNoticeById = useCallback((id: string) => {
    setNotices((prev) => prev.filter((notice) => notice.id !== id));
  }, []);

  const addNotice = useCallback((message: string, tone: Notice["tone"] = "info") => {
    const id = `${Date.now()}-${Math.random()}`;
    setNotices((prev) => [{ id, message, tone }, ...prev].slice(0, 5));
    setTimeout(() => {
      removeNoticeById(id);
    }, 2800);
  }, [removeNoticeById]);

  useEffect(() => {
    setHeaderActionsEl(document.getElementById("header-actions"));
  }, []);

  useEffect(() => {
    return () => {
      clearSettingsMenuTimeout();
    };
  }, []);

  const loadCategories = async () => {
    const data = await request<Category[]>("/categories");
    setCategories(data);
    marketplaceCategoryIdCacheRef.current = {};
    marketplaceCategoryIdInFlightRef.current = {};
  };

  const refreshTotalMarketplaceProductCount = useCallback(async () => {
    const [ysResult, mgResult] = await Promise.allSettled([
      requestForMarketplace<MarketplaceProductAddedResponse[]>(
        "YS",
        "/categories/marketplace-products/added"
      ),
      requestForMarketplace<MarketplaceProductAddedResponse[]>(
        "MG",
        "/categories/marketplace-products/added"
      ),
    ]);
    const ysRows =
      ysResult.status === "fulfilled" && Array.isArray(ysResult.value)
        ? ysResult.value
        : [];
    const mgRows =
      mgResult.status === "fulfilled" && Array.isArray(mgResult.value)
        ? mgResult.value
        : [];
    const byCategory = new Map<number, { ys: MarketplaceProductAddedResponse[]; mg: MarketplaceProductAddedResponse[] }>();
    [...ysRows, ...mgRows].forEach((item) => {
      const bucket = byCategory.get(item.categoryId) ?? { ys: [], mg: [] };
      if (item.marketplaceCode === "YS") {
        bucket.ys.push(item);
      } else if (item.marketplaceCode === "MG") {
        bucket.mg.push(item);
      }
      byCategory.set(item.categoryId, bucket);
    });
    let matchedPairs = 0;
    byCategory.forEach((group) => {
      const pairs = buildMarketplacePairs(
        group.ys.map(addedToCandidate),
        group.mg.map(addedToCandidate),
        0.72
      );
      matchedPairs += pairs.length;
    });
    setMatchedMarketplaceProductCount(matchedPairs);
    setTotalMarketplaceProductCount(ysRows.length + mgRows.length - matchedPairs);
  }, []);

  useEffect(() => {
    loadCategories().catch((err) =>
      addNotice(`Kategoriler yuklenemedi: ${err.message}`, "error")
    );
  }, [addNotice]);

  useEffect(() => {
    void refreshTotalMarketplaceProductCount();
  }, [refreshTotalMarketplaceProductCount]);

  useEffect(() => {
    let cancelled = false;
    const loadSettings = async () => {
      let local: Partial<UserSettings> = {};
      try {
        const raw = window.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
        if (raw) {
          local = JSON.parse(raw) as Partial<UserSettings>;
        }
      } catch {
        local = {};
      }
      let backendDefaults: BasketMinimumSettingsResponse | null = null;
      try {
        backendDefaults = await request<BasketMinimumSettingsResponse>("/settings/basket-minimums");
      } catch {
        backendDefaults = null;
      }
      if (cancelled) {
        return;
      }
      const localLocation: Partial<CustomerLocation> =
        local.customerLocation && typeof local.customerLocation === "object"
          ? local.customerLocation
          : {};
      setUserSettings({
        migrosMoneyMember: Boolean(local.migrosMoneyMember),
        considerEffectivePricing: Boolean(local.considerEffectivePricing),
        ysMinimumBasketAmount:
          backendDefaults?.ysMinimumBasketAmount ??
          (typeof local.ysMinimumBasketAmount === "number"
            ? local.ysMinimumBasketAmount
            : null),
        mgMinimumBasketAmount:
          backendDefaults?.mgMinimumBasketAmount ??
          (typeof local.mgMinimumBasketAmount === "number"
            ? local.mgMinimumBasketAmount
            : null),
        customerLocation: {
          city:
            typeof localLocation.city === "string" ? localLocation.city : "",
          district:
            typeof localLocation.district === "string" ? localLocation.district : "",
          latitude: parseNullableNumber(localLocation.latitude),
          longitude: parseNullableNumber(localLocation.longitude),
        },
      });
    };
    void loadSettings();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    window.localStorage.setItem(
      USER_SETTINGS_STORAGE_KEY,
      JSON.stringify(userSettings)
    );
  }, [userSettings]);

  useEffect(() => {
    void request<NeedListItemDto[]>("/needs")
      .then((rows) => {
        setNeedList((Array.isArray(rows) ? rows : []) as NeedListItem[]);
      })
      .catch(() => {
        try {
          const raw = window.localStorage.getItem(NEED_LIST_STORAGE_KEY);
          if (!raw) {
            setNeedList([]);
            return;
          }
          const parsed = JSON.parse(raw) as NeedListItem[];
          setNeedList(Array.isArray(parsed) ? parsed : []);
        } catch {
          setNeedList([]);
        }
      })
      .finally(() => setNeedListLoaded(true));
  }, []);

  useEffect(() => {
    window.localStorage.setItem(NEED_LIST_STORAGE_KEY, JSON.stringify(needList));
    if (!needListLoaded) {
      return;
    }
    void request<NeedListItemDto[]>("/needs", {
      method: "PUT",
      body: JSON.stringify(needList),
    }).catch(() => undefined);
  }, [needList, needListLoaded]);

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
      const created = await requestForMarketplace<Category>("MG", "/categories", {
        method: "POST",
        body: JSON.stringify({ name: categoryName }),
      });
      try {
        await requestForMarketplace<Category>("YS", "/categories", {
          method: "POST",
          body: JSON.stringify({ name: categoryName }),
        });
      } catch {
        // Keep MG as source of truth if YS mirror write fails.
      }
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

  const fetchCategoryProducts = async (category: Category) => {
    const categoryNameParam = encodeURIComponent(category.name);
    const [ys, mg] = await Promise.all([
      requestForMarketplace<ProductResponse[]>(
        "YS",
        `/marketplaces/products?categoryName=${categoryNameParam}`
      ),
      requestForMarketplace<ProductResponse[]>(
        "MG",
        `/marketplaces/products?categoryName=${categoryNameParam}`
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

  const resolveCategoryIdForMarketplace = async (
    category: Category,
    marketplaceCode: MarketplaceCode
  ) => {
    const cacheKey = `${category.id}:${marketplaceCode}`;
    const cachedValue = marketplaceCategoryIdCacheRef.current[cacheKey];
    if (cachedValue !== undefined) {
      return cachedValue;
    }
    const pending = marketplaceCategoryIdInFlightRef.current[cacheKey];
    if (pending) {
      return pending;
    }

    const resolverPromise = (async () => {
    if (marketplaceCode === "MG") {
        return category.id;
    }
    const marketplaceCategories = await requestForMarketplace<Category[]>(
      marketplaceCode,
      "/categories"
    );
    const normalizedCategoryName = category.name.trim().toLocaleLowerCase("tr-TR");
    const matched = marketplaceCategories.find(
      (item) => item.name.trim().toLocaleLowerCase("tr-TR") === normalizedCategoryName
    );
    return matched?.id ?? null;
    })();

    marketplaceCategoryIdInFlightRef.current[cacheKey] = resolverPromise;
    try {
      const resolved = await resolverPromise;
      marketplaceCategoryIdCacheRef.current[cacheKey] = resolved;
      return resolved;
    } finally {
      delete marketplaceCategoryIdInFlightRef.current[cacheKey];
    }
  };

  const loadCandidates = async (category: Category) => {
    setMarketplaceBusy(category.id, "YS", true);
    setMarketplaceBusy(category.id, "MG", true);
    try {
      const [ysCategoryId, mgCategoryId] = await Promise.all([
        resolveCategoryIdForMarketplace(category, "YS"),
        resolveCategoryIdForMarketplace(category, "MG"),
      ]);
      const [ysCandidates, mgCandidates] = await Promise.all([
        ysCategoryId === null
          ? Promise.resolve([] as MarketplaceProductCandidateResponse[])
          : requestForMarketplace<MarketplaceProductCandidateResponse[]>(
              "YS",
              `/categories/${ysCategoryId}/marketplace-products`
            ),
        mgCategoryId === null
          ? Promise.resolve([] as MarketplaceProductCandidateResponse[])
          : requestForMarketplace<MarketplaceProductCandidateResponse[]>(
              "MG",
              `/categories/${mgCategoryId}/marketplace-products`
            ),
      ]);
      const candidates = [...ysCandidates, ...mgCandidates];
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
      const marketplaceCategoryId = await resolveCategoryIdForMarketplace(
        category,
        marketplaceCode
      );
      if (marketplaceCategoryId === null) {
        setAddedProductsByCategory((prev) => ({
          ...prev,
          [category.id]: {
            ...(prev[category.id] ?? {}),
            [marketplaceCode]: [],
          },
        }));
        return;
      }
      const products = await requestForMarketplace<MarketplaceProductEntryResponse[]>(
        marketplaceCode,
        `/categories/${marketplaceCategoryId}/marketplace-products/added`
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
    const cached = addedProductsByCategory[category.id];
    let ys: MarketplaceProductEntryResponse[] | undefined = cached?.YS;
    let mg: MarketplaceProductEntryResponse[] | undefined = cached?.MG;

    if (!ys || !mg) {
      const [ysCategoryId, mgCategoryId] = await Promise.all([
        resolveCategoryIdForMarketplace(category, "YS"),
        resolveCategoryIdForMarketplace(category, "MG"),
      ]);
      const [fetchedYs, fetchedMg] = await Promise.all([
        ysCategoryId === null
          ? Promise.resolve([] as MarketplaceProductEntryResponse[])
          : requestForMarketplace<MarketplaceProductEntryResponse[]>(
              "YS",
              `/categories/${ysCategoryId}/marketplace-products/added`
            ),
        mgCategoryId === null
          ? Promise.resolve([] as MarketplaceProductEntryResponse[])
          : requestForMarketplace<MarketplaceProductEntryResponse[]>(
              "MG",
              `/categories/${mgCategoryId}/marketplace-products/added`
            ),
      ]);
      ys = fetchedYs;
      mg = fetchedMg;
      setAddedProductsByCategory((prev) => ({
        ...prev,
        [category.id]: {
          ...(prev[category.id] ?? {}),
          YS: fetchedYs,
          MG: fetchedMg,
        },
      }));
    }

    const all = [...ys, ...mg];
    const targets = all.filter(
      (item): item is MarketplaceProductEntryResponse & { productId: number } => item.productId !== null
    );
    if (targets.length === 0) {
      setOpportunitiesByCategory((prev) => ({ ...prev, [category.id]: [] }));
      return;
    }
    const feed: Array<OpportunityItem | null> = await Promise.all(
      targets.slice(0, MAX_OPPORTUNITY_TARGETS).map(async (target) => {
        let history: PriceHistoryResponse[] = [];
        try {
          history = await request<PriceHistoryResponse[]>(
            `/products/${target.productId}/prices?${buildPriceHistoryQuery(target.marketplaceCode)}`
          );
        } catch {
          return null;
        }
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
    const startupTasks: Promise<unknown>[] = [];
    if (!productsByCategory[category.id]) {
      startupTasks.push(
        (async () => {
          setBusy(true);
          try {
            await fetchCategoryProducts(category);
          } catch (err) {
            addNotice(`Urunler yuklenemedi: ${(err as Error).message}`, "error");
          } finally {
            setBusy(false);
          }
        })()
      );
    }
    if (!candidateItemsByCategory[category.id]) {
      startupTasks.push(loadCandidates(category));
    }
    const loadedAdded = addedProductsByCategory[category.id];
    if (!loadedAdded?.MG || !loadedAdded?.YS) {
      startupTasks.push(loadAllAddedProducts(category));
    }
    if (startupTasks.length > 0) {
      await Promise.all(startupTasks);
    }
    void loadCategoryOpportunities(category);
  };

  const refreshCategoryMarketplaceData = async (
    category: Category,
    marketplaceCode?: MarketplaceCode
  ) => {
    if (marketplaceCode) {
      await loadAddedProducts(category, marketplaceCode);
      return;
    }
    await loadAllAddedProducts(category);
  };

  const toAddedProductEntry = (
    candidate: MarketplaceProductCandidateResponse
  ): MarketplaceProductEntryResponse => ({
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
  });

  const addOptimisticAddedProduct = (
    categoryId: number,
    marketplaceCode: MarketplaceCode,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const optimisticEntry = toAddedProductEntry(candidate);
    setAddedProductsByCategory((prev) => {
      const categoryState = prev[categoryId] ?? {};
      const currentRows = categoryState[marketplaceCode] ?? [];
      const exists = currentRows.some((row) => row.externalId === candidate.externalId);
      if (exists) {
        return prev;
      }
      return {
        ...prev,
        [categoryId]: {
          ...categoryState,
          [marketplaceCode]: [optimisticEntry, ...currentRows],
        },
      };
    });
  };

  const removeOptimisticAddedProduct = (
    categoryId: number,
    marketplaceCode: MarketplaceCode,
    externalId: string
  ) => {
    setAddedProductsByCategory((prev) => {
      const categoryState = prev[categoryId] ?? {};
      const currentRows = categoryState[marketplaceCode] ?? [];
      const nextRows = currentRows.filter((row) => row.externalId !== externalId);
      return {
        ...prev,
        [categoryId]: {
          ...categoryState,
          [marketplaceCode]: nextRows,
        },
      };
    });
  };

  const mutateCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse,
    method: "POST" | "DELETE",
    successMessage: string,
    options?: { logicalTotalDelta?: number }
  ) => {
    const marketplaceCode = asMarketplaceCode(candidate.marketplaceCode);
    if (!marketplaceCode) {
      addNotice(`Gecersiz marketplace kodu: ${candidate.marketplaceCode}`, "error");
      return false;
    }
    const wasAlreadyAdded = allAddedProducts.some(
      (item) =>
        item.marketplaceCode === marketplaceCode && item.externalId === candidate.externalId
    );
    const isPartOfMatchedAddedPair =
      method === "DELETE" && addedMatchKeySet.has(candidateKey(candidate));
    const defaultLogicalDelta =
      method === "POST" && !wasAlreadyAdded
        ? 1
        : method === "DELETE" && wasAlreadyAdded
          ? isPartOfMatchedAddedPair
            ? 0
            : -1
          : 0;
    const logicalTotalDelta = options?.logicalTotalDelta ?? defaultLogicalDelta;
    if (method === "POST" && !wasAlreadyAdded) {
      addOptimisticAddedProduct(category.id, marketplaceCode, candidate);
    }
    if (logicalTotalDelta !== 0) {
      setTotalMarketplaceProductCount((prev) => Math.max(0, prev + logicalTotalDelta));
    }
    try {
      const endpoint =
        method === "POST"
          ? `/marketplaces/${marketplaceCode}/categories/${encodeURIComponent(category.name)}/addproduct`
          : `/marketplaces/${marketplaceCode}/products/${encodeURIComponent(
              candidate.externalId
            )}?categoryName=${encodeURIComponent(category.name)}`;
      const message = await requestForMarketplace<string>(marketplaceCode, endpoint, {
        method,
        body:
          method === "POST"
            ? JSON.stringify({ productId: candidate.externalId })
            : undefined,
      });
      addNotice(message || successMessage, "success");
      void refreshCategoryMarketplaceData(category, marketplaceCode);
      void loadCategoryOpportunities(category);
      void refreshTotalMarketplaceProductCount();
      return true;
    } catch (err) {
      if (method === "POST" && !wasAlreadyAdded) {
        removeOptimisticAddedProduct(category.id, marketplaceCode, candidate.externalId);
      }
      if (logicalTotalDelta !== 0) {
        setTotalMarketplaceProductCount((prev) => Math.max(0, prev - logicalTotalDelta));
      }
      void refreshTotalMarketplaceProductCount();
      const actionText = method === "POST" ? "eklenemedi" : "silinemedi";
      addNotice(`Urun ${actionText}: ${(err as Error).message}`, "error");
      return false;
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
      await requestForMarketplace<string>("MG", `/categories/${category.id}`, {
        method: "DELETE",
      });
      try {
        const ysCategories = await requestForMarketplace<Category[]>("YS", "/categories");
        const mirrorCategory = ysCategories.find(
          (item) => item.name.trim().toLocaleLowerCase("tr-TR") === category.name.trim().toLocaleLowerCase("tr-TR")
        );
        if (mirrorCategory) {
          await requestForMarketplace<string>("YS", `/categories/${mirrorCategory.id}`, {
            method: "DELETE",
          });
        }
      } catch {
        // Keep MG as source of truth if YS mirror delete fails.
      }
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
    candidate: MarketplaceProductCandidateResponse,
    isMatchedCandidate: boolean
  ) => {
    const sourceMarketplaceCode = asMarketplaceCode(candidate.marketplaceCode);
    const sourceKey = candidateKey(candidate);
    const debugPrefix = `[auto-link][${category.id}] ${sourceKey}`;
    if (!sourceMarketplaceCode) {
      console.info(`${debugPrefix} skipped: invalid marketplace code`);
      addNotice(`Gecersiz marketplace kodu: ${candidate.marketplaceCode}`, "error");
      return;
    }
    const flowKey = `${category.id}:${sourceMarketplaceCode}:${sourceKey}`;
    if (addFlowLocksRef.current.has(flowKey)) {
      console.info(`${debugPrefix} skipped: flow locked (${flowKey})`);
      addNotice("Ayni markette ekleme islemi devam ediyor, lutfen bekleyin.", "info");
      return;
    }
    addFlowLocksRef.current.add(flowKey);
    try {
      console.info(
        `${debugPrefix} start: isMatchedCandidate=${isMatchedCandidate}, suggestedSize=${suggestedMatchPairs.length}`
      );
      const sourceAddPromise = mutateCandidateProduct(
        category,
        candidate,
        "POST",
        "Urun eklendi.",
        { logicalTotalDelta: 1 }
      );
      if (!isMatchedCandidate) {
        const isSuccess = await sourceAddPromise;
        if (!isSuccess) {
          console.info(`${debugPrefix} stop: source add failed`);
          return;
        }
        console.info(`${debugPrefix} stop: isMatchedCandidate=false`);
        return;
      }
      const matchedPair = suggestedMatchPairs.find(
        (pair) =>
          (pair.ys.marketplaceCode === candidate.marketplaceCode &&
            pair.ys.externalId === candidate.externalId) ||
          (pair.mg.marketplaceCode === candidate.marketplaceCode &&
            pair.mg.externalId === candidate.externalId)
      );
      if (!matchedPair) {
        const isSuccess = await sourceAddPromise;
        if (!isSuccess) {
          console.info(`${debugPrefix} stop: source add failed`);
          return;
        }
        console.info(`${debugPrefix} stop: no matchedPair`);
        return;
      }
      const target =
        matchedPair.ys.marketplaceCode === candidate.marketplaceCode &&
        matchedPair.ys.externalId === candidate.externalId
          ? matchedPair.mg
          : matchedPair.ys;
      const targetKey = candidateKey(target);
      if (!suggestedMatchKeySet.has(targetKey)) {
        const isSuccess = await sourceAddPromise;
        if (!isSuccess) {
          console.info(`${debugPrefix} stop: source add failed`);
          return;
        }
        console.info(`${debugPrefix} stop: target not in suggestedMatchKeySet (${targetKey})`);
        return;
      }
      const otherMarketplaceName = candidate.marketplaceCode === "YS" ? "Migros" : "Yemeksepeti";
      if (isAlreadyAddedInMarketplace(target)) {
        const isSuccess = await sourceAddPromise;
        if (!isSuccess) {
          console.info(`${debugPrefix} stop: source add failed`);
          return;
        }
        console.info(`${debugPrefix} stop: target already added (${targetKey})`);
        addNotice(
          `${otherMarketplaceName} tarafinda benzer urun zaten ekli: ${target.name}`,
          "info"
        );
        return;
      }
      console.info(`${debugPrefix} auto-adding target: ${targetKey}`);
      void mutateCandidateProduct(
        category,
        target,
        "POST",
        `${otherMarketplaceName} tarafindaki eslesen urun otomatik eklendi.`,
        { logicalTotalDelta: 0 }
      );
      console.info(`${debugPrefix} done: target add dispatched (${targetKey})`);

      const isSuccess = await sourceAddPromise;
      if (!isSuccess) {
        console.info(`${debugPrefix} stop: source add failed`);
        return;
      }
    } finally {
      console.info(`${debugPrefix} finish`);
      addFlowLocksRef.current.delete(flowKey);
    }
  };

  const handleRemoveCandidateProduct = async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const bestMatch = findBestCrossMarketplaceAddedMatch(candidate);
    const autoRemoveTarget =
      bestMatch &&
      shouldAutoLinkCandidates(
        {
          score: bestMatch.score.score,
          nameScore: bestMatch.score.nameScore,
          coreNameScore: bestMatch.score.coreNameScore,
          phraseScore: bestMatch.score.phraseScore,
          quantityScore: bestMatch.score.quantityScore,
          brandScore: bestMatch.score.brandScore,
          imageScore: bestMatch.score.imageScore,
          priceScore: bestMatch.score.priceScore,
          profileScore: bestMatch.score.profileScore,
        },
        candidate,
        entryToCandidate(bestMatch.item)
      )
        ? entryToCandidate(bestMatch.item)
        : null;
    const removed = await mutateCandidateProduct(
      category,
      candidate,
      "DELETE",
      "Urun silindi.",
      { logicalTotalDelta: autoRemoveTarget ? -1 : undefined }
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
    if (!autoRemoveTarget) {
      return;
    }
    const otherMarketplaceName =
      autoRemoveTarget.marketplaceCode === "YS" ? "Yemeksepeti" : "Migros";
    const autoRemoved = await mutateCandidateProduct(
      category,
      autoRemoveTarget,
      "DELETE",
      `${otherMarketplaceName} tarafindaki eslesen urun otomatik silindi.`,
      { logicalTotalDelta: 0 }
    );
    if (!autoRemoved) {
      return;
    }
    removeNeedListEntryForCandidate(autoRemoveTarget);
    if (
      selectedAddedProduct &&
      selectedAddedProduct.externalId === autoRemoveTarget.externalId &&
      selectedAddedProduct.marketplaceCode === autoRemoveTarget.marketplaceCode
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
      new Map(targets.map((item) => [`${item.marketplaceCode}:${item.productId}`, item])).values()
    );
    if (uniqueTargets.length === 0) {
      return;
    }
    setHistoryBusy(true);
    try {
      const historyGroups = await Promise.all(
        uniqueTargets.map(async (target) => {
          try {
            const data = await request<PriceHistoryResponse[]>(
              `/products/${target.productId}/prices?${buildPriceHistoryQuery(target.marketplaceCode)}`
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
          } catch {
            return [];
          }
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
    const historyForMarketplace = selectedProductHistory.filter(
      (item) => item.marketplaceCode === selectedAddedProduct.marketplaceCode
    );
    const historyDayCount = new Set(historyForMarketplace.map((item) => item.recordedAt)).size;
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
      historyDayCount,
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

  const handleAddCategoryToNeedList = async (category: Category) => {
    let categoryProductsByMarketplace = addedProductsByCategory[category.id] ?? {};
    const hasLoadedBothMarketplaces =
      Array.isArray(categoryProductsByMarketplace.YS) &&
      Array.isArray(categoryProductsByMarketplace.MG);
    if (!hasLoadedBothMarketplaces) {
      try {
        const [ysCategoryId, mgCategoryId] = await Promise.all([
          resolveCategoryIdForMarketplace(category, "YS"),
          resolveCategoryIdForMarketplace(category, "MG"),
        ]);
        const [ysRows, mgRows] = await Promise.all([
          ysCategoryId === null
            ? Promise.resolve([] as MarketplaceProductEntryResponse[])
            : requestForMarketplace<MarketplaceProductEntryResponse[]>(
                "YS",
                `/categories/${ysCategoryId}/marketplace-products/added`
              ),
          mgCategoryId === null
            ? Promise.resolve([] as MarketplaceProductEntryResponse[])
            : requestForMarketplace<MarketplaceProductEntryResponse[]>(
                "MG",
                `/categories/${mgCategoryId}/marketplace-products/added`
              ),
        ]);
        categoryProductsByMarketplace = {
          YS: Array.isArray(ysRows) ? ysRows : [],
          MG: Array.isArray(mgRows) ? mgRows : [],
        };
        setAddedProductsByCategory((prev) => ({
          ...prev,
          [category.id]: categoryProductsByMarketplace,
        }));
      } catch (err) {
        addNotice(`Kategori urunleri alinamadi: ${(err as Error).message}`, "error");
      }
    }
    const allCategoryProducts = Object.values(categoryProductsByMarketplace).flatMap(
      (items) => items ?? []
    );
    if (allCategoryProducts.length === 0) {
      const item: NeedListItem = {
        key: `category:${category.id}`,
        type: "CATEGORY",
        categoryId: category.id,
        categoryName: category.name,
        externalId: null,
        marketplaceCode: null,
        name: `${formatCategoryTitle(category.name)} (Kategori)`,
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
        historyDayCount: null,
        availabilityStatus: "Normal",
        opportunityLevel: null,
      };
      setNeedList((prev) => {
        const withoutCategory = prev.filter((entry) => entry.categoryId !== category.id);
        return [item, ...withoutCategory];
      });
      addNotice(`${category.name} kategorisi ihtiyac listesine eklendi.`, "success");
      return;
    }

    const opportunityMap = new Map(
      (opportunitiesByCategory[category.id] ?? []).map((item) => [
        `${item.marketplaceCode}:${item.externalId}`,
        item,
      ])
    );
    const uniqueProducts = new Map<string, MarketplaceProductEntryResponse>();
    allCategoryProducts.forEach((product) => {
      const marketplaceCode =
        product.marketplaceCode === "YS" || product.marketplaceCode === "MG"
          ? product.marketplaceCode
          : null;
      if (!marketplaceCode) {
        return;
      }
      const dedupeKey = `${marketplaceCode}:${product.externalId}`;
      uniqueProducts.set(dedupeKey, product);
    });
    const needItems: NeedListItem[] = Array.from(uniqueProducts.values()).map((product) => {
      const marketplaceCode = product.marketplaceCode as MarketplaceCode;
      const opp = opportunityMap.get(`${marketplaceCode}:${product.externalId}`);
      const availabilityStatus = opp?.availabilityStatus ?? "Normal";
      return {
        key: `product:${category.id}:${marketplaceCode}:${product.externalId}`,
        type: "PRODUCT",
        categoryId: category.id,
        categoryName: category.name,
        externalId: product.externalId,
        marketplaceCode,
        name: product.name || `Urun ${product.externalId}`,
        imageUrl: product.imageUrl,
        price: product.price,
        moneyPrice: product.moneyPrice,
        basketDiscountThreshold: product.basketDiscountThreshold,
        basketDiscountPrice: product.basketDiscountPrice,
        campaignBuyQuantity: product.campaignBuyQuantity,
        campaignPayQuantity: product.campaignPayQuantity,
        effectivePrice: product.effectivePrice,
        urgency: selectedNeedUrgency,
        availabilityScore: null,
        historyDayCount: null,
        availabilityStatus,
        opportunityLevel: opp?.opportunityLevel ?? null,
      };
    });

    setNeedList((prev) => {
      const withoutCategory = prev.filter((entry) => entry.categoryId !== category.id);
      return [...needItems, ...withoutCategory];
    });
    addNotice(
      `${category.name} kategorisinden ${needItems.length} urun ihtiyac listesine eklendi.`,
      "success"
    );
  };

  const buildNeedProductKey = (
    categoryId: number,
    marketplaceCode: MarketplaceCode,
    externalId: string
  ) => `product:${categoryId}:${marketplaceCode}:${externalId}`;

  const isNeedProductAdded = (
    categoryId: number,
    marketplaceCode: MarketplaceCode,
    externalId: string
  ) =>
    needList.some(
      (item) =>
        item.type === "PRODUCT" &&
        item.categoryId === categoryId &&
        item.marketplaceCode === marketplaceCode &&
        item.externalId === externalId
    );

  const isNeedCategoryAdded = (categoryId: number) =>
    needList.some((item) => item.type === "CATEGORY" && item.categoryId === categoryId);

  const handleAddSingleProductToNeedList = (
    category: Category,
    product: MarketplaceProductEntryResponse
  ) => {
    const marketplaceCode =
      product.marketplaceCode === "YS" || product.marketplaceCode === "MG"
        ? product.marketplaceCode
        : null;
    if (!marketplaceCode) {
      addNotice("Gecersiz marketplace kodu.", "error");
      return;
    }
    if (isNeedProductAdded(category.id, marketplaceCode, product.externalId)) {
      addNotice("Urun zaten ihtiyac listesinde.", "info");
      return;
    }
    const opportunityMap = new Map(
      (opportunitiesByCategory[category.id] ?? []).map((item) => [
        `${item.marketplaceCode}:${item.externalId}`,
        item,
      ])
    );
    const opp = opportunityMap.get(`${marketplaceCode}:${product.externalId}`);
    const needItem: NeedListItem = {
      key: buildNeedProductKey(category.id, marketplaceCode, product.externalId),
      type: "PRODUCT",
      categoryId: category.id,
      categoryName: category.name,
      externalId: product.externalId,
      marketplaceCode,
      name: product.name || `Urun ${product.externalId}`,
      imageUrl: product.imageUrl,
      price: product.price,
      moneyPrice: product.moneyPrice,
      basketDiscountThreshold: product.basketDiscountThreshold,
      basketDiscountPrice: product.basketDiscountPrice,
      campaignBuyQuantity: product.campaignBuyQuantity,
      campaignPayQuantity: product.campaignPayQuantity,
      effectivePrice: product.effectivePrice,
      urgency: selectedNeedUrgency,
      availabilityScore: null,
      historyDayCount: null,
      availabilityStatus: opp?.availabilityStatus ?? "Normal",
      opportunityLevel: opp?.opportunityLevel ?? null,
    };
    setNeedList((prev) => [needItem, ...prev]);
    addNotice(`${needItem.name} ihtiyac listesine eklendi.`, "success");
  };

  const handleRemoveSingleProductFromNeedList = (
    category: Category,
    product: MarketplaceProductEntryResponse
  ) => {
    const marketplaceCode =
      product.marketplaceCode === "YS" || product.marketplaceCode === "MG"
        ? product.marketplaceCode
        : null;
    if (!marketplaceCode) {
      return;
    }
    const targetKey = buildNeedProductKey(category.id, marketplaceCode, product.externalId);
    setNeedList((prev) => prev.filter((item) => item.key !== targetKey));
    addNotice(`${product.name || `Urun ${product.externalId}`} ihtiyac listesinden cikarildi.`, "success");
  };

  const handleRemoveCategoryFromNeedList = (category: Category) => {
    const isInNeedList = needList.some((item) => item.categoryId === category.id);
    if (!isInNeedList) {
      addNotice(`${category.name} kategorisi ihtiyac listesinde degil.`, "info");
      return;
    }
    setNeedList((prev) => prev.filter((item) => item.categoryId !== category.id));
    addNotice(`${category.name} kategorisi ihtiyac listesinden cikarildi.`, "success");
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
        void handleAddCandidateProduct(
          category,
          candidate,
          suggestedMatchKeySet.has(candidateKey(candidate))
        );
      } catch {
        addNotice("Suruklenen urun verisi okunamadi.", "error");
      }
    };
  };

  const activeCategory = categories.find((cat) => cat.id === expandedCategoryId);
  const activeProducts = activeCategory ? productsByCategory[activeCategory.id] : null;
  const activeCandidates = useMemo(
    () => (activeCategory ? candidateItemsByCategory[activeCategory.id] ?? [] : []),
    [activeCategory, candidateItemsByCategory]
  );
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
    () => new Set(allAddedProducts.map((item) => `${item.marketplaceCode}:${item.externalId}`)),
    [allAddedProducts]
  );
  const sortedCategories = useMemo(
    () =>
      [...categories].sort((left, right) =>
        left.name.localeCompare(right.name, "tr-TR", { sensitivity: "base" })
      ),
    [categories]
  );
  const searchableNamesByCategory = useMemo(() => {
    const grouped = new Map<number, Set<string>>();
    const addName = (categoryId: number, value: string | null | undefined) => {
      if (!Number.isFinite(categoryId)) {
        return;
      }
      const normalized = (value ?? "").trim();
      if (!normalized) {
        return;
      }
      const current = grouped.get(categoryId) ?? new Set<string>();
      current.add(normalized);
      grouped.set(categoryId, current);
    };
    categories.forEach((category) => addName(category.id, category.name));
    Object.entries(candidateItemsByCategory).forEach(([categoryIdRaw, products]) => {
      const categoryId = Number(categoryIdRaw);
      products.forEach((product) => addName(categoryId, product.name));
    });
    Object.entries(addedProductsByCategory).forEach(([categoryIdRaw, byMarketplace]) => {
      const categoryId = Number(categoryIdRaw);
      Object.values(byMarketplace ?? {}).forEach((rows) => {
        (rows ?? []).forEach((row) => addName(categoryId, row.name));
      });
    });
    Object.entries(productsByCategory).forEach(([categoryIdRaw, byMarketplace]) => {
      const categoryId = Number(categoryIdRaw);
      [...(byMarketplace?.YS ?? []), ...(byMarketplace?.MG ?? [])].forEach((product) =>
        addName(categoryId, product.name)
      );
    });
    return grouped;
  }, [addedProductsByCategory, candidateItemsByCategory, categories, productsByCategory]);
  const normalizedCategoryFilterQuery = normalizeProductName(categoryFilterQuery);
  const isCategoryFilterActive = normalizedCategoryFilterQuery.length > 0;
  const filteredSortedCategories = useMemo(() => {
    if (!isCategoryFilterActive) {
      return sortedCategories;
    }
    const queryTokens = normalizedCategoryFilterQuery.split(" ").filter(Boolean);
    if (queryTokens.length === 0) {
      return sortedCategories;
    }
    return sortedCategories.filter((category) => {
      const names = searchableNamesByCategory.get(category.id);
      if (!names || names.size === 0) {
        return false;
      }
      return [...names].some((name) => {
        const normalizedName = normalizeProductName(name);
        return queryTokens.every((token) => normalizedName.includes(token));
      });
    });
  }, [
    isCategoryFilterActive,
    normalizedCategoryFilterQuery,
    searchableNamesByCategory,
    sortedCategories,
  ]);
  const categoryFilterSuggestions = useMemo(() => {
    const candidates = new Set<string>();
    searchableNamesByCategory.forEach((names) => {
      names.forEach((name) => candidates.add(name));
    });
    let result = [...candidates].sort((left, right) =>
      left.localeCompare(right, "tr-TR", { sensitivity: "base" })
    );
    if (isCategoryFilterActive) {
      result = result.filter((name) =>
        normalizeProductName(name).includes(normalizedCategoryFilterQuery)
      );
    }
    return result.slice(0, 24);
  }, [isCategoryFilterActive, normalizedCategoryFilterQuery, searchableNamesByCategory]);
  const categoryTotalPages = isCategoryFilterActive
    ? 1
    : Math.max(1, Math.ceil(filteredSortedCategories.length / CATEGORY_PAGE_SIZE));
  const currentCategoryPage = isCategoryFilterActive
    ? 0
    : Math.min(categoryPage, categoryTotalPages - 1);
  const pagedCategories = useMemo(() => {
    if (isCategoryFilterActive) {
      return filteredSortedCategories;
    }
    const start = currentCategoryPage * CATEGORY_PAGE_SIZE;
    return filteredSortedCategories.slice(start, start + CATEGORY_PAGE_SIZE);
  }, [currentCategoryPage, filteredSortedCategories, isCategoryFilterActive]);
  const visibleCategories = isCategoryFilterActive
    ? filteredSortedCategories
    : currentCategoryPage === 0 && !showAllCategories
      ? pagedCategories.slice(0, CATEGORY_VISIBLE_COUNT)
      : pagedCategories;
  const needProductPreviewByCategory = useMemo(() => {
    const grouped: Record<number, string[]> = {};
    needList.forEach((item) => {
      if (item.type !== "PRODUCT" || item.categoryId <= 0) {
        return;
      }
      const normalizedName = (item.name || "").trim();
      if (!normalizedName) {
        return;
      }
      const current = grouped[item.categoryId] ?? [];
      if (!current.includes(normalizedName)) {
        current.push(normalizedName);
      }
      grouped[item.categoryId] = current;
    });
    return grouped;
  }, [needList]);
  const needProductCount = useMemo(() => {
    const unique = new Set<string>();
    needList.forEach((item) => {
      if (item.type !== "PRODUCT") {
        return;
      }
      unique.add(`${item.categoryId}:${item.marketplaceCode ?? "NA"}:${item.externalId ?? item.name}`);
    });
    return unique.size;
  }, [needList]);

  const marketplaceSections = useMemo(() => {
    if (!activeProducts) {
      return [];
    }
    return MARKETPLACES.map((marketplace) => ({
      ...marketplace,
      products: activeProducts[marketplace.code],
    }));
  }, [activeProducts]);

  useEffect(() => {
    setCategoryPage((prev) => Math.min(prev, categoryTotalPages - 1));
  }, [categoryTotalPages]);

  useEffect(() => {
    setCategoryPage(0);
    if (isCategoryFilterActive) {
      setShowAllCategories(true);
    }
  }, [isCategoryFilterActive]);

  useEffect(() => {
    if (expandedCategoryId === null) {
      return;
    }
    const index = filteredSortedCategories.findIndex((item) => item.id === expandedCategoryId);
    if (index < 0) {
      return;
    }
    const targetPage = Math.floor(index / CATEGORY_PAGE_SIZE);
    if (targetPage !== currentCategoryPage) {
      setCategoryPage(targetPage);
      setShowAllCategories(true);
    }
  }, [currentCategoryPage, expandedCategoryId, filteredSortedCategories]);

  const suggestedMatchPairs = useMemo(() => {
    const ys = activeCandidates.filter((item) => item.marketplaceCode === "YS");
    const mg = activeCandidates.filter((item) => item.marketplaceCode === "MG");
    return buildMarketplacePairs(ys, mg, MATCH_MIN_SCORE);
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
    return buildMarketplacePairs(ys, mg, MATCH_MIN_SCORE);
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
      const insufficientHistory =
        item.marketplaceCode !== null &&
        (item.historyDayCount === null || item.historyDayCount < 7);
      if (insufficientHistory) {
        return {
          ...item,
          shouldBuy: false,
        };
      }
      const isHighOpportunity = item.opportunityLevel === "Yuksek";
      const campaignThreshold = item.basketDiscountThreshold ?? DEFAULT_MIGROS_BASKET_THRESHOLD;
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
          ? typeof item.availabilityScore === "number"
            ? item.availabilityScore >= 35
            : item.availabilityStatus !== "Pahali"
          : item.urgency === "URGENT"
            ? typeof item.availabilityScore === "number"
              ? item.availabilityScore >= 50
              : item.availabilityStatus === "Uygun" ||
                (item.availabilityStatus === "Normal" && isHighOpportunity)
            : typeof item.availabilityScore === "number"
              ? item.availabilityScore >= 70
              : item.availabilityStatus === "Uygun";
      return {
        ...item,
        shouldBuy: canBuy || campaignEligible || effectiveCampaignEligible,
      };
    });
  }, [needList, userSettings.considerEffectivePricing]);

  const suggestedBasketItems = useMemo(
    () => basketDecisionByNeed.filter((item) => item.shouldBuy),
    [basketDecisionByNeed]
  );

  const autoOpportunitySuggestions = useMemo(() => {
    const needCategories = new Set(needList.map((item) => item.categoryId));
    const all = Object.values(opportunitiesByCategory).flat();
    return all.filter((item) => !needCategories.has(item.categoryId));
  }, [needList, opportunitiesByCategory]);

  const uniqueBasketSuggestions = useMemo(() => {
    const combined = [...suggestedBasketItems, ...autoOpportunitySuggestions];
    const unique: typeof combined = [];
    const seen = new Set<string>();
    combined.forEach((item) => {
      const dedupeKey =
        item.key ||
        `${item.marketplaceCode ?? "NA"}:${item.externalId ?? "NA"}:${item.categoryId}:${item.name}`;
      if (seen.has(dedupeKey)) {
        return;
      }
      seen.add(dedupeKey);
      unique.push(item);
    });
    return unique;
  }, [suggestedBasketItems, autoOpportunitySuggestions]);

  const basketSuggestionCount = uniqueBasketSuggestions.length;
  const basketSuggestionPreviewByMarketplace = useMemo(() => {
    return {
      YS: uniqueBasketSuggestions.filter((item) => item.marketplaceCode === "YS").slice(0, 3),
      MG: uniqueBasketSuggestions.filter((item) => item.marketplaceCode === "MG").slice(0, 3),
    };
  }, [uniqueBasketSuggestions]);
  const basketSuggestionTotals = useMemo(() => {
    const totals = { YS: 0, MG: 0 };
    uniqueBasketSuggestions.forEach((item) => {
      if (item.marketplaceCode !== "YS" && item.marketplaceCode !== "MG") {
        return;
      }
      const price =
        "urgency" in item
          ? resolveThresholdPrice(item, userSettings.considerEffectivePricing)
          : item.price;
      totals[item.marketplaceCode] += price ?? 0;
    });
    return totals;
  }, [uniqueBasketSuggestions, userSettings.considerEffectivePricing]);
  const mgMinimumShortfall = Math.max(
    0,
    (userSettings.mgMinimumBasketAmount ?? 0) - basketSuggestionTotals.MG
  );

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
              const statusPrices = points.reduce<Record<AvailabilityStatus, number[]>>(
                (acc, point) => {
                  if (
                    point.availabilityScore === null ||
                    point.availabilityScore === undefined ||
                    !Number.isFinite(point.availabilityScore)
                  ) {
                    return acc;
                  }
                  const status = resolveAvailabilityStatus(point.availabilityScore);
                  acc[status].push(point.price);
                  return acc;
                },
                { Uygun: [], Normal: [], Pahali: [] }
              );
              const hasUygun = statusPrices.Uygun.length > 0;
              const hasNormal = statusPrices.Normal.length > 0;
              const hasPahali = statusPrices.Pahali.length > 0;
              let suitableUpper = hasUygun ? Math.max(...statusPrices.Uygun) : 0;
              let normalUpper = hasNormal ? Math.max(...statusPrices.Normal) : suitableUpper;
              if (hasPahali) {
                normalUpper = Math.max(normalUpper, Math.min(...statusPrices.Pahali));
              }
              if (hasUygun && !hasNormal && !hasPahali) {
                suitableUpper = range;
                normalUpper = range;
              } else if (!hasUygun && hasNormal && !hasPahali) {
                suitableUpper = 0;
                normalUpper = range;
              } else if (!hasUygun && !hasNormal && hasPahali) {
                suitableUpper = 0;
                normalUpper = 0;
              } else {
                if (!hasUygun) {
                  suitableUpper = Math.min(range * 0.333, normalUpper);
                }
                if (!hasNormal && !hasPahali) {
                  normalUpper = suitableUpper;
                }
              }
              suitableUpper = Math.min(Math.max(suitableUpper, 0), range);
              normalUpper = Math.min(Math.max(normalUpper, suitableUpper), range);
              const ySuitableTop = axisBottom - (suitableUpper / range) * ySpan;
              const yNormalTop = axisBottom - (normalUpper / range) * ySpan;
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
                  <g key="price-band-uygun">
                    <rect
                      x={plotLeft}
                      y={ySuitableTop}
                      width={plotWidth}
                      height={Math.max(axisBottom - ySuitableTop, 0)}
                      fill="#bbf7d0"
                      fillOpacity="0.26"
                    />
                  </g>
                  <g key="price-band-normal">
                    <rect
                      x={plotLeft}
                      y={yNormalTop}
                      width={plotWidth}
                      height={Math.max(ySuitableTop - yNormalTop, 0)}
                      fill="#bae6fd"
                      fillOpacity="0.26"
                    />
                  </g>
                  <g key="price-band-pahali">
                    <rect
                      x={plotLeft}
                      y={axisTop}
                      width={plotWidth}
                      height={Math.max(yNormalTop - axisTop, 0)}
                      fill="#fed7aa"
                      fillOpacity="0.26"
                    />
                  </g>
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
    <div className="flex flex-col gap-8 overflow-x-hidden">
      <section className="rounded-3xl border border-black/10 bg-white/80 p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div className="flex flex-wrap items-center gap-3">
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-amber-700">
              Toplam kategori
            </p>
            <p className="display text-2xl">{categories.length}</p>
          </div>
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-emerald-700">
              Toplam urun
            </p>
            <p className="display text-2xl">{totalMarketplaceProductCount}</p>
          </div>
          <div className="rounded-2xl border border-fuchsia-200 bg-fuchsia-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-fuchsia-700">
              Eslesen urun
            </p>
            <p className="display text-2xl">{matchedMarketplaceProductCount}</p>
          </div>
          <div className="rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-sky-700">
              Alinabilecek urun
            </p>
            <p className="display text-2xl">{basketSuggestionCount}</p>
          </div>
          <div className="rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-teal-700">
              Ihtiyac urun sayisi
            </p>
            <p className="display text-2xl">{needProductCount}</p>
          </div>
        </div>
      </section>

      <section className="grid gap-6">
        <div className="rounded-3xl border border-black/10 bg-white p-5 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] overflow-hidden">
          <div className="flex items-center justify-between">
            <div>
              <button
                type="button"
                className="display text-left text-xl transition hover:text-[#9a5c00]"
                onClick={() => setIsCategoryListCollapsed((prev) => !prev)}
                aria-label="Kategori listesini ac veya kapat"
                title="Kategori listesini ac veya kapat"
              >
                Kategori Listesi {isCategoryListCollapsed ? "+" : "-"}
              </button>
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

          {!isCategoryListCollapsed && (
          <div className="mt-6 space-y-3">
            <div className="rounded-2xl border border-black/10 bg-[#fcfaf7] p-3">
              <label
                htmlFor="category-filter"
                className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#9a5c00]"
              >
                Kategori / Urun Filtrele
              </label>
              <div className="mt-2 flex gap-2">
                <input
                  id="category-filter"
                  type="text"
                  value={categoryFilterQuery}
                  list="category-filter-suggestions"
                  onChange={(event) => setCategoryFilterQuery(event.target.value)}
                  placeholder="Kategori veya urun adi yaz..."
                  className="h-10 w-full rounded-xl border border-black/10 bg-white px-3 text-sm text-[#111] outline-none ring-[#d97706] placeholder:text-[#9ca3af] focus:ring-2"
                />
                {categoryFilterQuery.trim().length > 0 && (
                  <button
                    type="button"
                    onClick={() => setCategoryFilterQuery("")}
                    className="h-10 shrink-0 rounded-xl border border-black/10 bg-white px-3 text-xs font-semibold uppercase tracking-[0.12em] text-[#6b655c] transition hover:bg-amber-50"
                  >
                    Temizle
                  </button>
                )}
              </div>
              <datalist id="category-filter-suggestions">
                {categoryFilterSuggestions.map((suggestion) => (
                  <option key={`category-filter-option-${suggestion}`} value={suggestion} />
                ))}
              </datalist>
              {isCategoryFilterActive && (
                <p className="mt-2 text-xs text-[#6b655c]">
                  {visibleCategories.length} kategori bulundu.
                </p>
              )}
            </div>
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
                      className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 transition hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
                      onClick={(event) => {
                        event.stopPropagation();
                        void handleAddCategoryToNeedList(category);
                      }}
                      disabled={busy}
                      title="Kategoriyi ihtiyac listesine ekle"
                    >
                      <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                        <path
                          d="M2.2 8h11.6M8 2.2v11.6"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.6"
                          strokeLinecap="round"
                        />
                      </svg>
                      <span>Ihtiyac</span>
                    </button>
                    {needList.some((item) => item.categoryId === category.id) && (
                      <button
                        type="button"
                        className="inline-flex items-center gap-1.5 rounded-full border border-sky-200 bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700 transition hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-60"
                        onClick={(event) => {
                          event.stopPropagation();
                          handleRemoveCategoryFromNeedList(category);
                        }}
                        disabled={busy}
                        title="Kategoriyi ihtiyac listesinden cikar"
                      >
                        <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                          <path
                            d="M2.2 8h11.6"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.6"
                            strokeLinecap="round"
                          />
                        </svg>
                        <span>Cikar</span>
                      </button>
                    )}
                    <button
                      type="button"
                      className="inline-flex items-center gap-1.5 rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-60"
                      onClick={(event) => {
                        event.stopPropagation();
                        onDeleteCategoryClick(category)();
                      }}
                      disabled={busy}
                      title="Kategoriyi sil"
                    >
                      <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                        <path
                          d="M3 4.5h10M6.2 4.5V3.3a.8.8 0 0 1 .8-.8h2a.8.8 0 0 1 .8.8v1.2M6 7v4.5M8 7v4.5M10 7v4.5M4.8 4.5l.5 8.1a1 1 0 0 0 1 .9h3.4a1 1 0 0 0 1-.9l.5-8.1"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                      <span>Sil</span>
                    </button>
                  </div>
                </div>
                {(() => {
                  const needProducts = needProductPreviewByCategory[category.id] ?? [];
                  if (needProducts.length === 0) {
                    return null;
                  }
                  const preview = needProducts.slice(0, 2).join(", ");
                  const remaining = needProducts.length - 2;
                  return (
                    <p className="mt-2 px-1 text-xs text-[#0f766e]">
                      Ihtiyac urunleri: {preview}
                      {remaining > 0 ? ` +${remaining}` : ""}
                    </p>
                  );
                })()}

                {expandedCategoryId === category.id && (
                  <div className="mt-4 space-y-4">
                    <div className="flex flex-wrap items-center justify-end gap-2">
                      <button
                        type="button"
                        className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-700 transition hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
                        onClick={() => {
                          void handleAddCategoryToNeedList(category);
                        }}
                        disabled={busy}
                        title="Kategori altindaki urunleri ihtiyac listesine ekle"
                      >
                        <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                          <path
                            d="M2.2 8h11.6M8 2.2v11.6"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.6"
                            strokeLinecap="round"
                          />
                        </svg>
                        <span>Urunleri Ihtiyaca Ekle</span>
                      </button>
                      {needList.some((item) => item.categoryId === category.id) && (
                        <button
                          type="button"
                          className="inline-flex items-center gap-1.5 rounded-full border border-sky-200 bg-sky-50 px-3 py-1 text-xs font-semibold text-sky-700 transition hover:bg-sky-100 disabled:cursor-not-allowed disabled:opacity-60"
                          onClick={() => {
                            handleRemoveCategoryFromNeedList(category);
                          }}
                          disabled={busy}
                          title="Kategori altindaki ihtiyac urunlerini listeden cikar"
                        >
                          <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                            <path
                              d="M2.2 8h11.6"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="1.6"
                              strokeLinecap="round"
                            />
                          </svg>
                          <span>Ihtiyactan Cikar</span>
                        </button>
                      )}
                    </div>
                    {activeProducts ? (
                      <>
                        <div className="space-y-3 xl:hidden">
                          {(() => {
                            const mobileAddedListKey = `${category.id}:added:ALL`;
                            const combinedAddedProducts = marketplaceSections
                              .flatMap((section) => addedProductsByMarketplace[section.code] ?? [])
                              .sort((left, right) => {
                                const leftPrice = resolveEntryDisplayPrice(left);
                                const rightPrice = resolveEntryDisplayPrice(right);
                                if (leftPrice !== rightPrice) {
                                  return leftPrice - rightPrice;
                                }
                                return (left.name ?? "").localeCompare(right.name ?? "", "tr");
                              });
                            const showAllMobileAdded =
                              expandedLists[mobileAddedListKey] ||
                              combinedAddedProducts.length <= LIST_VISIBLE_COUNT;
                            const visibleMobileAdded = showAllMobileAdded
                              ? combinedAddedProducts
                              : combinedAddedProducts.slice(0, LIST_VISIBLE_COUNT);
                            return (
                              <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
                                <div className="flex items-center justify-between">
                                  <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">
                                    Eklenen Urunler
                                  </p>
                                  <span className="text-xs text-[#6b655c]">
                                    {combinedAddedProducts.length} urun
                                  </span>
                                </div>
                                <div className="mt-2 space-y-2">
                                  {combinedAddedProducts.length === 0 ? (
                                    <p className="text-xs text-[#6b655c]">Bu kategori icin urun yok.</p>
                                  ) : (
                                    visibleMobileAdded.map((item, index) => {
                                      const marketplaceCode = asMarketplaceCode(item.marketplaceCode);
                                      return (
                                        <div
                                          key={`mobile-added-${item.externalId}-${index}`}
                                          role="button"
                                          tabIndex={0}
                                          className="grid grid-cols-[1fr_34px] items-center gap-2 rounded-xl border border-black/10 bg-white px-3 py-2 text-left"
                                          onClick={onSelectAddedProductClick(item, category)}
                                          onKeyDown={onSelectAddedProductKeyDown(item, category)}
                                        >
                                          <div className="min-w-0">
                                            <p className="truncate text-sm font-semibold text-[#111]">{item.name}</p>
                                            <p className="truncate text-xs text-[#6b655c]">
                                              {item.marketplaceCode === "YS" ? "Yemeksepeti" : "Migros"} |{" "}
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
                                          </div>
                                          <button
                                            type="button"
                                            className="flex h-8 w-8 items-center justify-center rounded-full border border-rose-200 bg-rose-50 text-rose-700 transition hover:bg-rose-100"
                                            onClick={(event) => {
                                              event.stopPropagation();
                                              if (marketplaceCode) {
                                                onRemoveAddedProductClick(category, item, marketplaceCode)(event);
                                              }
                                            }}
                                            title="Urun sil"
                                          >
                                            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                                              <path
                                                d="M3 4.5h10M6.2 4.5V3.3a.8.8 0 0 1 .8-.8h2a.8.8 0 0 1 .8.8v1.2M6 7v4.5M8 7v4.5M10 7v4.5M4.8 4.5l.5 8.1a1 1 0 0 0 1 .9h3.4a1 1 0 0 0 1-.9l.5-8.1"
                                                fill="none"
                                                stroke="currentColor"
                                                strokeWidth="1.2"
                                                strokeLinecap="round"
                                                strokeLinejoin="round"
                                              />
                                            </svg>
                                          </button>
                                        </div>
                                      );
                                    })
                                  )}
                                  {combinedAddedProducts.length > LIST_VISIBLE_COUNT && (
                                    <button
                                      type="button"
                                      className="w-full rounded-2xl border border-black/10 bg-white px-3 py-2 text-xs uppercase tracking-[0.2em] text-[#9a5c00] transition hover:bg-amber-50"
                                      onClick={() => toggleList(mobileAddedListKey)}
                                    >
                                      {expandedLists[mobileAddedListKey]
                                        ? "Daha Az Goster"
                                        : "Tumunu Goster"}
                                    </button>
                                  )}
                                </div>
                              </div>
                            );
                          })()}
                        </div>
                      <div className="hidden gap-3 xl:grid xl:grid-cols-2">
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
                            const leftPrice = resolveCandidateDisplayPrice(left);
                            const rightPrice = resolveCandidateDisplayPrice(right);
                            if (leftPrice !== rightPrice) {
                              return leftPrice - rightPrice;
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
                          const leftPrice = resolveEntryDisplayPrice(left);
                          const rightPrice = resolveEntryDisplayPrice(right);
                          if (leftPrice !== rightPrice) {
                            return leftPrice - rightPrice;
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
                              <Image src={section.iconSrc} alt={section.name} width={20} height={20} className="h-5 w-5 rounded-sm object-contain" />
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
                                      className={`relative grid h-[92px] w-full grid-cols-[40px_minmax(0,1fr)_36px] items-center gap-3 overflow-hidden rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 text-left transition hover:bg-white ${
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
                                        <span className="absolute left-2 top-1 inline-flex items-center rounded-full border border-emerald-300 bg-emerald-100 p-1 text-[10px] font-semibold text-emerald-800">
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
                                        </span>
                                      )}
                                      <div className="relative h-10 w-10 overflow-hidden rounded-xl border border-black/10 bg-white">
                                        {item.imageUrl ? (
                                          <Image src={item.imageUrl} alt={item.name} fill sizes="40px" className="object-cover" />
                                        ) : (
                                          <div className="flex h-full w-full items-center justify-center text-[10px] text-[#6b655c]">
                                            Gorsel yok
                                          </div>
                                        )}
                                      </div>
                                      <div className="min-w-0">
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
                                            <Image src="/migros-money.png" alt="Money" width={12} height={12} className="h-3 w-3 object-contain" />
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
                                      <div className="flex flex-col items-center gap-1">
                                        {isNeedProductAdded(
                                          category.id,
                                          section.code,
                                          item.externalId
                                        ) ? (
                                          <button
                                            type="button"
                                            className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-sky-200 bg-sky-50 text-sky-700 transition hover:bg-sky-100"
                                            onClick={(event) => {
                                              event.stopPropagation();
                                              handleRemoveSingleProductFromNeedList(category, item);
                                            }}
                                            title="Ihtiyactan cikar"
                                          >
                                            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                                              <path
                                                d="M2.2 8h11.6"
                                                fill="none"
                                                stroke="currentColor"
                                                strokeWidth="1.6"
                                                strokeLinecap="round"
                                              />
                                            </svg>
                                          </button>
                                        ) : !isNeedCategoryAdded(category.id) ? (
                                          <button
                                            type="button"
                                            className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-amber-200 bg-amber-50 text-amber-700 transition hover:bg-amber-100"
                                            onClick={(event) => {
                                              event.stopPropagation();
                                              handleAddSingleProductToNeedList(category, item);
                                            }}
                                            title="Ihtiyaca ekle"
                                          >
                                            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                                              <path
                                                d="M2.2 8h11.6M8 2.2v11.6"
                                                fill="none"
                                                stroke="currentColor"
                                                strokeWidth="1.6"
                                                strokeLinecap="round"
                                              />
                                            </svg>
                                          </button>
                                        ) : null}
                                        <button
                                          type="button"
                                          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-rose-200 bg-rose-50 text-rose-700 transition hover:bg-rose-100"
                                          onClick={onRemoveAddedProductClick(category, item, section.code)}
                                          disabled={sectionBusy}
                                          title="Urun sil"
                                        >
                                          <svg viewBox="0 0 16 16" className="h-4 w-4" aria-hidden>
                                            <path
                                              d="M3 4.5h10M6.2 4.5V3.3a.8.8 0 0 1 .8-.8h2a.8.8 0 0 1 .8.8v1.2M6 7v4.5M8 7v4.5M10 7v4.5M4.8 4.5l.5 8.1a1 1 0 0 0 1 .9h3.4a1 1 0 0 0 1-.9l.5-8.1"
                                              fill="none"
                                              stroke="currentColor"
                                              strokeWidth="1.2"
                                              strokeLinecap="round"
                                              strokeLinejoin="round"
                                            />
                                          </svg>
                                        </button>
                                      </div>
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
                              sectionCandidates={sectionCandidates}
                              visibleSectionCandidates={visibleSectionCandidates}
                              matchedCandidateKeys={suggestedMatchKeySet}
                              suggestedListKey={suggestedListKey}
                              isMigrosMoneyMember={userSettings.migrosMoneyMember}
                              considerEffectivePricing={userSettings.considerEffectivePricing}
                              candidateBusy={sectionBusy}
                              addedCandidateIds={addedCandidateIds}
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
                      </>
                    ) : (
                      <p className="text-xs text-[#6b655c]">
                        Urunler yukleniyor...
                      </p>
                    )}
                  </div>
                )}
              </div>
            ))}
            {!isCategoryFilterActive &&
              currentCategoryPage === 0 &&
              pagedCategories.length > CATEGORY_VISIBLE_COUNT && (
              <button
                type="button"
                className="w-full rounded-2xl border border-black/10 bg-white px-4 py-2 text-xs uppercase tracking-[0.2em] text-[#9a5c00] transition hover:bg-amber-50"
                onClick={() => setShowAllCategories((prev) => !prev)}
              >
                {showAllCategories ? "Daha Az Kategori Goster" : "Daha Fazla Kategori Gor"}
              </button>
            )}
            {!isCategoryFilterActive && categoryTotalPages > 1 && (
              <div className="flex items-center justify-between gap-2">
                <button
                  type="button"
                  className="rounded-xl border border-black/10 bg-white px-3 py-1.5 text-xs font-semibold text-[#6b655c] transition hover:bg-[#f4ede3] disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={() => {
                    setCategoryPage((prev) => Math.max(0, prev - 1));
                    setShowAllCategories(false);
                  }}
                  disabled={currentCategoryPage === 0}
                >
                  Onceki
                </button>
                <p className="text-xs text-[#6b655c]">
                  Sayfa {currentCategoryPage + 1}/{categoryTotalPages}
                </p>
                <button
                  type="button"
                  className="rounded-xl border border-black/10 bg-white px-3 py-1.5 text-xs font-semibold text-[#6b655c] transition hover:bg-[#f4ede3] disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={() => {
                    setCategoryPage((prev) => Math.min(categoryTotalPages - 1, prev + 1));
                    setShowAllCategories(false);
                  }}
                  disabled={currentCategoryPage >= categoryTotalPages - 1}
                >
                  Sonraki
                </button>
              </div>
            )}
          </div>
          )}
        </div>

      </section>

      <section className="rounded-3xl border border-black/10 bg-white/70 p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)] backdrop-blur">
        <div className="flex flex-col gap-4">
          <div>
            <p className="text-xs uppercase tracking-[0.3em] text-[#9a5c00]">
              Kategori Merkezi
            </p>
            <button
              type="button"
              className="display text-left text-3xl font-semibold transition hover:text-[#9a5c00]"
              onClick={() => setIsInfoSectionCollapsed((prev) => !prev)}
              aria-label="Bilgilendirme bolumunu ac veya kapat"
              title="Bilgilendirme bolumunu ac veya kapat"
            >
              Bilgilendirme {isInfoSectionCollapsed ? "+" : "-"}
            </button>
            <p className="mt-2 text-sm text-[#6b655c]">
              Sayfadaki terimler ve kisa yollarin ne anlama geldigini burada
              bulabilirsiniz.
            </p>
          </div>
          {!isInfoSectionCollapsed && (
          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Efektif Fiyat
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Money uyesi indirimi ve kampanyali adet fiyati varsa tekil birim
                maliyetini hesaplar. Ayarlarda acik ise karsilastirmalarda bu
                fiyat kullanilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Ayarlar
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Money Uyelik acildiginda Migros Money fiyatlari baz alinir.
                Efektif Fiyat acildiginda kampanyalar tekil fiyata indirgenir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Ihtiyac Listesi
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Urun modalinda &quot;Ihtiyac Listesine Ekle&quot; urunu,
                &quot;Kategoriyi Ihtiyac Listesine Ekle&quot; tum kategoriyi
                listeye ekler.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Sepet Onerileri
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Ihtiyac listesine gore zorunlu onerileri ve ihtiyac disi genel
                firsat onerilerini ayri katmanlarda sunar. Minimum sepet tutari
                icin gerekli ek urun ihtiyacini gosterir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Ihtiyactan Onerilenler
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Ihtiyac listesinde olan urun/kategorilerden uretilir. Aciliyet
                ve alinabilirlik skoruna gore onceliklendirilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Genel Firsatlar
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Ihtiyac listesinde olmayan ama fiyat seviyesi dusuk veya kampanyali
                gorunen urunleri gosterir. Zorunlu degil, firsat odaklidir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Alinabilirlik
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Son fiyat gecmisi icindeki siralama ve bugunku fiyatin konumu
                kullanilarak hesaplanir. Skor &gt;= 65 Uygun, 40-64 Normal, 40
                altinda Pahali. Skor yoksa Normal kabul edilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Marketler
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Desteklenen marketler: Yemeksepeti (YS) ve Migros (MG). Urunler
                her market icin ayri baglanir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/10 bg-[#f9f4ee] p-3">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#9a5c00]">
                Ihtiyac Aciliyeti
              </p>
              <p className="mt-2 text-sm text-[#6b655c]">
                Acil / Normal / Acil Degil secimi, sepet onerilerindeki oncelik
                sirasini ve alinabilirlik sinyallerini etkiler.
              </p>
            </div>
          </div>
          )}
        </div>
      </section>

      <ProductInfoModal
        isOpen={showProductInfoModal}
        product={
          selectedAddedProduct
            ? {
                name: selectedAddedProduct.name || "Isimsiz urun",
                imageUrl: selectedAddedProduct.imageUrl,
                brandName: selectedAddedProduct.brandName || "Marka yok",
                marketplaceCode: selectedAddedProduct.marketplaceCode,
              }
            : null
        }
        onCloseAction={() => setShowProductInfoModal(false)}
        priceLabel={
          selectedAddedProduct
            ? formatMarketplacePriceLabel(
                selectedAddedProduct.price,
                selectedAddedProduct.moneyPrice,
                selectedAddedProduct.effectivePrice,
                selectedAddedProduct.campaignBuyQuantity,
                selectedAddedProduct.campaignPayQuantity,
                selectedAddedProduct.marketplaceCode,
                userSettings.migrosMoneyMember,
                userSettings.considerEffectivePricing
              )
            : "-"
        }
        showMoneyBadge={
          selectedAddedProduct
            ? isMoneyDisplayPrice(
                selectedAddedProduct.marketplaceCode,
                selectedAddedProduct.price,
                selectedAddedProduct.moneyPrice,
                selectedAddedProduct.effectivePrice,
                selectedAddedProduct.campaignBuyQuantity,
                selectedAddedProduct.campaignPayQuantity,
                userSettings.migrosMoneyMember,
                userSettings.considerEffectivePricing
              )
            : false
        }
        moneyBadgeText={selectedAddedProduct ? formatPrice(selectedAddedProduct.moneyPrice) : null}
        showBasketDiscount={
          selectedAddedProduct
            ? hasBasketDiscount(
                selectedAddedProduct.marketplaceCode,
                selectedAddedProduct.basketDiscountThreshold,
                selectedAddedProduct.basketDiscountPrice
              )
            : false
        }
        basketDiscountThresholdText={
          selectedAddedProduct ? formatTl(selectedAddedProduct.basketDiscountThreshold, 0) : null
        }
        basketDiscountPriceText={
          selectedAddedProduct ? formatTl(selectedAddedProduct.basketDiscountPrice) : null
        }
        showEffectiveCampaign={
          selectedAddedProduct
            ? hasEffectiveCampaign(
                selectedAddedProduct.marketplaceCode,
                selectedAddedProduct.price,
                selectedAddedProduct.moneyPrice,
                selectedAddedProduct.campaignBuyQuantity,
                selectedAddedProduct.campaignPayQuantity,
                selectedAddedProduct.effectivePrice
              )
            : false
        }
        effectiveCampaignText={
          selectedAddedProduct
            ? `${formatEffectiveCampaignBadge(
                selectedAddedProduct.campaignBuyQuantity,
                selectedAddedProduct.campaignPayQuantity
              )} - Efektif ${formatTl(
                resolveEffectivePriceValue(
                  selectedAddedProduct.price,
                  selectedAddedProduct.moneyPrice,
                  selectedAddedProduct.effectivePrice,
                  selectedAddedProduct.campaignBuyQuantity,
                  selectedAddedProduct.campaignPayQuantity,
                  userSettings.migrosMoneyMember
                )
              )}`
            : null
        }
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
              onKeyDown={(event) => {
                if (event.key !== "Enter" || busy) {
                  return;
                }
                event.preventDefault();
                void handleCreateCategory();
              }}
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

      {headerActionsEl &&
        createPortal(
          <>
            <div
              className="relative"
              onMouseEnter={() => {
                clearSettingsMenuTimeout();
                setShowSettingsMenu(true);
              }}
              onMouseLeave={() => {
                scheduleSettingsMenuClose();
              }}
            >
              <button
                type="button"
                className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
                onClick={() => {
                  clearSettingsMenuTimeout();
                  setShowSettingsMenu(false);
                }}
                title="Ayarlar"
                aria-label="Ayarlar"
              >
                ⚙️
              </button>
              <div
                className={`absolute right-0 top-12 w-[min(20rem,calc(100vw-1.5rem))] rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] ${
                  showSettingsMenu ? "block" : "hidden"
                }`}
                onMouseEnter={() => {
                  clearSettingsMenuTimeout();
                  setShowSettingsMenu(true);
                }}
                onMouseLeave={() => {
                  scheduleSettingsMenuClose();
                }}
                onMouseDown={() => {
                  clearSettingsMenuTimeout();
                }}
                onFocus={() => {
                  clearSettingsMenuTimeout();
                  setShowSettingsMenu(true);
                }}
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
                          setUserSettings((prev) => ({
                            ...prev,
                            considerEffectivePricing: false,
                          }))
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
                          setUserSettings((prev) => ({
                            ...prev,
                            considerEffectivePricing: true,
                          }))
                        }
                      >
                        Aktif
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
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
              <div className="absolute right-0 top-12 hidden w-[min(18rem,calc(100vw-1.5rem))] rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] group-hover:block">
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
              <div className="absolute right-0 top-12 hidden w-[min(20rem,calc(100vw-1.5rem))] rounded-2xl border border-black/10 bg-white p-3 text-xs shadow-[0_20px_45px_-28px_rgba(0,0,0,0.45)] group-hover:block">
                <p className="text-[10px] uppercase tracking-[0.18em] text-[#9a5c00]">
                  Sepet Onerileri
                </p>
                <div className="mt-2 space-y-1">
                  {basketSuggestionCount === 0 ? (
                    <p className="text-[#6b655c]">Su an onerilen urun yok.</p>
                  ) : (
                    <>
                      <div className="rounded-lg border border-black/10 bg-[#f9f4ee] p-2">
                        <p className="text-[10px] uppercase tracking-[0.14em] text-[#9a5c00]">
                          Migros
                        </p>
                        <div className="mt-1 space-y-1">
                          {basketSuggestionPreviewByMarketplace.MG.length === 0 ? (
                            <p className="text-[#6b655c]">Oneri yok.</p>
                          ) : (
                            basketSuggestionPreviewByMarketplace.MG.map((item) => (
                              <p key={`basket-preview-mg-${item.key}`} className="text-[#14532d]">
                                {item.name}
                              </p>
                            ))
                          )}
                        </div>
                      </div>
                      <div className="rounded-lg border border-black/10 bg-[#f9f4ee] p-2">
                        <p className="text-[10px] uppercase tracking-[0.14em] text-[#9a5c00]">
                          Yemeksepeti
                        </p>
                        <div className="mt-1 space-y-1">
                          {basketSuggestionPreviewByMarketplace.YS.length === 0 ? (
                            <p className="text-[#6b655c]">Oneri yok.</p>
                          ) : (
                            basketSuggestionPreviewByMarketplace.YS.map((item) => (
                              <p key={`basket-preview-ys-${item.key}`} className="text-[#14532d]">
                                {item.name}
                              </p>
                            ))
                          )}
                        </div>
                      </div>
                    </>
                  )}
                </div>
                <div className="mt-2 rounded-xl border border-black/10 bg-[#f9f4ee] p-2 text-[11px] text-[#6b655c]">
                  <p>Yemeksepeti toplam tutar: {formatTl(basketSuggestionTotals.YS)}</p>
                  <p>Migros toplam tutar: {formatTl(basketSuggestionTotals.MG)}</p>
                </div>
                <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-2 text-[11px] text-[#6b655c]">
                  <p>
                    Migros min sepet: {formatTl(userSettings.mgMinimumBasketAmount)} | Oneri
                    toplami: {formatTl(basketSuggestionTotals.MG)}
                  </p>
                  <p>Min sepet icin ek urun tutari: {formatTl(mgMinimumShortfall)}</p>
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
          </>,
          headerActionsEl
        )}

      <div className="pointer-events-none fixed left-3 right-3 top-24 z-50 flex w-auto flex-col gap-2 sm:left-auto sm:right-6 sm:w-[320px]">
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
