﻿"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type MouseEvent,
  type ReactNode,
} from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
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
  MarketplaceManualMatchRequest,
  MarketplaceProductAddedResponse,
  MarketplaceProductCandidateResponse,
  MarketplaceProductEntryResponse,
  MarketplaceProductMatchPairResponse,
  MarketplaceProductMatchRequest,
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
const MATCH_MIN_SCORE = 0.55;
const CANDIDATE_DRAG_TYPE = "application/x-smart-pantry-candidate";
const CATEGORY_DRAG_TYPE = "application/x-smart-pantry-category";
const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";
const OPPORTUNITY_FEED_STORAGE_KEY = "smart-pantry:opportunity-feed";
const MAIN_CATEGORY_STORAGE_KEY = "smart-pantry:main-categories";
const MAIN_CATEGORY_ASSIGNMENT_STORAGE_KEY = "smart-pantry:main-category-assignments";
const UNCATEGORIZED_MAIN_CATEGORY_KEY = "__uncategorized__";
const UNCATEGORIZED_MAIN_CATEGORY_LABEL = "Ana Kategori Yok";
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

const isReadRequestFailure = (error: unknown) => {
  if (!(error instanceof Error)) {
    return false;
  }
  const message = error.message.toLocaleLowerCase("tr-TR");
  return message.includes("failed to read request");
};
const ADDED_PRODUCTS_DROPZONE_CLASS =
  "mt-2 space-y-2 rounded-2xl border-2 border-dashed border-[#374151] bg-[#fff4e0] p-2 shadow-[0_12px_30px_-20px_rgba(217,119,6,0.7)]";

const normalizeProductName = (value: string) =>
  value
    .toLocaleLowerCase("tr-TR")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\d+([.,]\d+)?\s*(g|gr|kg|ml|l|lt)\b/g, " ")
    .replace(/\b(li|lu|paket|adet|pet|sise|Ãƒâ€¦Ã…Â¸iÃƒâ€¦Ã…Â¸e)\b/g, " ")
    .replace(/[^a-z0-9\s]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

const tokenSet = (value: string) =>
  new Set(
    normalizeProductName(value)
      .split(" ")
      .filter((token) => token.length > 1)
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

type ProductWithQuantityMeta = {
  name: string;
  unit?: string | null;
  unitValue?: number | null;
  packCount?: number | null;
};

const formatQuantityTag = (quantity: QuantityInfo) => {
  if (quantity.amount === null || !quantity.unit) {
    return "";
  }
  const amountText = Number.isInteger(quantity.amount)
    ? `${quantity.amount}`
    : `${quantity.amount}`.replace(/\.0+$/, "");
  return `${amountText} ${quantity.unit}`;
};

const getProductQuantityText = (item: ProductWithQuantityMeta) => {
  const rawUnit = (item.unit ?? "").toLocaleLowerCase("tr-TR").trim();
  const normalizedUnit: "g" | "ml" | null =
    rawUnit === "g" || rawUnit === "gr" || rawUnit === "kg"
      ? "g"
      : rawUnit === "ml" || rawUnit === "l" || rawUnit === "lt"
        ? "ml"
        : null;
  const quantity: QuantityInfo = {
    amount:
      normalizedUnit && typeof item.unitValue === "number" && Number.isFinite(item.unitValue) && item.unitValue > 0
        ? item.unitValue
        : null,
    unit: normalizedUnit,
    packCount:
      typeof item.packCount === "number" && Number.isFinite(item.packCount) && item.packCount > 0
        ? item.packCount
        : null,
  };
  const quantityTag = formatQuantityTag(quantity);
  return quantityTag || null;
};

const formatProductNameWithQuantity = (item: ProductWithQuantityMeta) => {
  const quantityTag = getProductQuantityText(item);
  if (!quantityTag) {
    return item.name;
  }
  return `${item.name} (${quantityTag})`;
};

const candidateKey = (item: MarketplaceProductCandidateResponse) =>
  `${item.marketplaceCode}:${item.externalId}`;

const addedToCandidate = (
  item: MarketplaceProductAddedResponse
): MarketplaceProductCandidateResponse => ({
  marketplaceCode: item.marketplaceCode,
  externalId: item.externalId,
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
  unit: item.unit ?? null,
  unitValue: item.unitValue ?? null,
  packCount: item.packCount ?? null,
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
  manualMatchSource: {
    categoryId: number;
    marketplaceCode: MarketplaceCode;
    externalId: string;
  } | null;
  manualMatchBusy: boolean;
  onStartManualMatch: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  onSelectManualMatchTarget: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  onRemoveManualMatch: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  isNeedProductAddedForCandidate: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => boolean;
  onToggleNeedForCandidate: (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => void;
  suggestedMatchPairByKey: Record<string, MarketplaceProductMatchPairResponse>;
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
  manualMatchSource,
  manualMatchBusy,
  onStartManualMatch,
  onSelectManualMatchTarget,
  onRemoveManualMatch,
  isNeedProductAddedForCandidate,
  onToggleNeedForCandidate,
  suggestedMatchPairByKey,
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
          const matchedPair = suggestedMatchPairByKey[itemKey];
          const isAdded = addedCandidateIds.has(itemKey);
          const isMatched = matchedCandidateKeys.has(itemKey);
          const isManualMatched = Boolean(matchedPair?.manualMatch);
          const isMatchSource =
            manualMatchSource?.categoryId === category.id &&
            manualMatchSource.marketplaceCode === marketplaceCode &&
            manualMatchSource.externalId === item.externalId;
          const isNeedAdded = isNeedProductAddedForCandidate(category, item);
          const isManualTargetSelectable =
            manualMatchSource?.categoryId === category.id &&
            manualMatchSource.marketplaceCode !== marketplaceCode &&
            !isAdded;
          const matchedTone =
            marketplaceCode === "YS"
              ? "border-rose-300 bg-rose-50/70"
              : "border-amber-300 bg-amber-50/70";
          return (
            <div
              key={`${item.externalId}-${index}`}
              role={isManualTargetSelectable ? "button" : undefined}
              tabIndex={isManualTargetSelectable ? 0 : undefined}
              className={`relative grid h-[92px] grid-cols-[40px_minmax(0,1fr)_36px] items-center gap-3 rounded-2xl border border-black/5 bg-[#f9f4ee] px-3 py-2 ${
                isMatched ? matchedTone : ""
              } ${
                isMatchSource ? "ring-2 ring-[#d97706] ring-offset-1 ring-offset-white" : ""
              } ${
                isAdded ? "opacity-40" : "cursor-grab active:cursor-grabbing"
              } ${
                isManualTargetSelectable ? "cursor-pointer border-[#d97706] bg-amber-50/60" : ""
              }`}
              draggable={!isAdded && !candidateBusy}
              onDragStart={(event) => onDragStartCandidate(event, item)}
              onClick={() => {
                if (!isManualTargetSelectable || manualMatchBusy) {
                  return;
                }
                onSelectManualMatchTarget(category, item);
              }}
              onKeyDown={(event) => {
                if (!isManualTargetSelectable || manualMatchBusy) {
                  return;
                }
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  onSelectManualMatchTarget(category, item);
                }
              }}
            >
              {isMatched && (
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
                  <Image src={item.imageUrl} alt={item.name} fill sizes="40px" className="object-cover" unoptimized />
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
                {getProductQuantityText(item) && (
                  <p className="truncate text-[11px] text-[#6b655c]">
                    {getProductQuantityText(item)}
                  </p>
                )}
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
              <div className="flex flex-col items-center gap-1">
                {!isAdded && (
                  <button
                    type="button"
                    className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#d97706] text-lg font-semibold text-white transition hover:bg-[#b45309]"
                    onClick={(event) => {
                      event.stopPropagation();
                      onAddCandidate(category, item, isMatched);
                    }}
                    disabled={candidateBusy}
                    title="Urun ekle"
                  >
                    +
                  </button>
                )}
                <button
                  type="button"
                  className={`inline-flex h-7 w-7 items-center justify-center rounded-full border transition disabled:opacity-50 ${
                    isNeedAdded
                      ? "border-sky-200 bg-sky-50 text-sky-700 hover:bg-sky-100"
                      : "border-amber-200 bg-amber-50 text-amber-700 hover:bg-amber-100"
                  }`}
                  onClick={(event) => {
                    event.stopPropagation();
                    onToggleNeedForCandidate(category, item);
                  }}
                  disabled={candidateBusy || manualMatchBusy}
                  title={isNeedAdded ? "Ihtiyactan cikar" : "Ihtiyaca ekle"}
                >
                  <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                    {isNeedAdded ? (
                      <path
                        d="M2.2 8h11.6"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.6"
                        strokeLinecap="round"
                      />
                    ) : (
                      <path
                        d="M2.2 8h11.6M8 2.2v11.6"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.6"
                        strokeLinecap="round"
                      />
                    )}
                  </svg>
                </button>
                <button
                  type="button"
                  className={`inline-flex h-7 w-7 items-center justify-center rounded-full border transition hover:bg-amber-100 disabled:opacity-50 ${
                    isManualMatched
                      ? "border-rose-200 bg-rose-50 text-rose-700"
                      : "border-amber-200 bg-amber-50 text-amber-700"
                  }`}
                  onClick={(event) => {
                    event.stopPropagation();
                    if (isManualMatched) {
                      onRemoveManualMatch(category, item);
                      return;
                    }
                    onStartManualMatch(category, item);
                  }}
                  disabled={candidateBusy || manualMatchBusy}
                  title={isManualMatched ? "Eslestirmeyi sil" : "Eslestirme icin sec"}
                >
                  <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                    {isManualMatched ? (
                      <path
                        d="M3.2 3.2l9.6 9.6M12.8 3.2l-9.6 9.6"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.6"
                        strokeLinecap="round"
                      />
                    ) : (
                      <path
                        d="M6.2 5.2 4.7 3.7a2.4 2.4 0 1 0-3.4 3.4l1.5 1.5a2.4 2.4 0 0 0 3.4 0m3.6-2.4 1.5-1.5a2.4 2.4 0 1 1 3.4 3.4l-1.5 1.5a2.4 2.4 0 0 1-3.4 0M5.1 10.9l5.8-5.8"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.4"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    )}
                  </svg>
                </button>
              </div>
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
  const router = useRouter();
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryName, setCategoryName] = useState("");
  const [mainCategoryName, setMainCategoryName] = useState("");
  const [customMainCategories, setCustomMainCategories] = useState<string[]>([]);
  const [mainCategoryAssignments, setMainCategoryAssignments] = useState<Record<string, string | null>>({});
  const [showMainCategoryModal, setShowMainCategoryModal] = useState(false);
  const [showInlineMainCategoryInput, setShowInlineMainCategoryInput] = useState(false);
  const [newMainCategoryDraft, setNewMainCategoryDraft] = useState("");
  const [renameMainCategorySource, setRenameMainCategorySource] = useState("");
  const [renameMainCategoryTarget, setRenameMainCategoryTarget] = useState("");
  const [selectedMainCategoryFilterKey, setSelectedMainCategoryFilterKey] = useState<string | null>(null);
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [editingCategoryName, setEditingCategoryName] = useState("");
  const [draggedCategoryId, setDraggedCategoryId] = useState<number | null>(null);
  const [activeMainCategoryDropKey, setActiveMainCategoryDropKey] = useState<string | null>(null);
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
  const [suggestedMatchPairs, setSuggestedMatchPairs] = useState<MarketplaceProductMatchPairResponse[]>([]);
  const [manualMatchBusy, setManualMatchBusy] = useState(false);
  const [manualMatchSource, setManualMatchSource] = useState<{
    categoryId: number;
    marketplaceCode: MarketplaceCode;
    externalId: string;
    name: string;
  } | null>(null);
  const [userSettings, setUserSettings] = useState<UserSettings>(
    DEFAULT_USER_SETTINGS
  );
  const [showSettingsMenu, setShowSettingsMenu] = useState(false);
  const [pendingNavigationPath, setPendingNavigationPath] = useState<"/needs" | "/basket" | null>(
    null
  );
  const settingsMenuTimeoutRef = useRef<number | null>(null);
  const [headerActionsEl, setHeaderActionsEl] = useState<HTMLElement | null>(null);
  const addFlowLocksRef = useRef<Set<string>>(new Set());
  const marketplaceCategoryIdCacheRef = useRef<Record<string, number | null>>({});
  const marketplaceCategoryIdInFlightRef = useRef<Record<string, Promise<number | null>>>({});

  const navigateToPage = useCallback(
    (pathname: "/needs" | "/basket") => {
      setShowProductInfoModal(false);
      setShowAddCategory(false);
      setSelectedAddedProduct(null);
      setSelectedProductCategory(null);
      if (expandedCategoryId !== null) {
        setPendingNavigationPath(pathname);
        setExpandedCategoryId(null);
        return;
      }
      router.push(pathname);
    },
    [expandedCategoryId, router]
  );

  useEffect(() => {
    if (pendingNavigationPath === null) {
      return;
    }
    if (expandedCategoryId !== null) {
      return;
    }
    router.push(pendingNavigationPath);
    setPendingNavigationPath(null);
  }, [expandedCategoryId, pendingNavigationPath, router]);

  const mainCategoryOptions = useMemo(
    () =>
      Array.from(
        new Set(
          [...customMainCategories, ...categories.map((category) => (category.mainCategory ?? "").trim())]
            .filter((value) => value.length > 0)
        )
      ).sort((left, right) => left.localeCompare(right, "tr-TR")),
    [categories, customMainCategories]
  );

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

  const loadCategories = useCallback(async () => {
    const data = await request<Category[]>("/categories");
    const merged = data.map((item) => {
      const override = mainCategoryAssignments[String(item.id)];
      if (override === undefined) {
        return item;
      }
      return { ...item, mainCategory: override };
    });
    setCategories(merged);
    marketplaceCategoryIdCacheRef.current = {};
    marketplaceCategoryIdInFlightRef.current = {};
  }, [mainCategoryAssignments]);

  const matchMarketplacePairs = useCallback(
    async (
      categoryId: number | undefined,
      ys: MarketplaceProductCandidateResponse[],
      mg: MarketplaceProductCandidateResponse[],
      minScore: number,
      silent = false
    ): Promise<MarketplaceProductMatchPairResponse[]> => {
      if (ys.length === 0 || mg.length === 0) {
        return [];
      }
      try {
        const payload: MarketplaceProductMatchRequest = {
          categoryId,
          ys,
          mg,
          minScore,
        };
        return await request<MarketplaceProductMatchPairResponse[]>(
          "/categories/marketplace-products/match",
          {
            method: "POST",
            body: JSON.stringify(payload),
          }
        );
      } catch (err) {
        if (!silent) {
          addNotice(`Eslestirme backend hatasi: ${(err as Error).message}`, "error");
        }
        return [];
      }
    },
    [addNotice]
  );

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
    const pairGroups = await Promise.all(
      [...byCategory.entries()].map(([categoryId, group]) =>
        matchMarketplacePairs(
          categoryId,
          group.ys.map(addedToCandidate),
          group.mg.map(addedToCandidate),
          0.72,
          true
        )
      )
    );
    const matchedPairs = pairGroups.reduce((sum, items) => sum + items.length, 0);
    setMatchedMarketplaceProductCount(matchedPairs);
    setTotalMarketplaceProductCount(ysRows.length + mgRows.length - matchedPairs);
  }, [matchMarketplacePairs]);

  useEffect(() => {
    loadCategories().catch((err) =>
      addNotice(`Kategoriler yuklenemedi: ${err.message}`, "error")
    );
  }, [addNotice, loadCategories]);

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
    try {
      const raw = window.localStorage.getItem(MAIN_CATEGORY_STORAGE_KEY);
      if (!raw) {
        setCustomMainCategories([]);
        return;
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        setCustomMainCategories([]);
        return;
      }
      const sanitized = parsed
        .map((item) => (typeof item === "string" ? item.trim() : ""))
        .filter((item) => item.length > 0);
      setCustomMainCategories(Array.from(new Set(sanitized)));
    } catch {
      setCustomMainCategories([]);
    }
  }, []);

  useEffect(() => {
    window.localStorage.setItem(MAIN_CATEGORY_STORAGE_KEY, JSON.stringify(customMainCategories));
  }, [customMainCategories]);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(MAIN_CATEGORY_ASSIGNMENT_STORAGE_KEY);
      if (!raw) {
        setMainCategoryAssignments({});
        return;
      }
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        setMainCategoryAssignments({});
        return;
      }
      const normalized: Record<string, string | null> = {};
      Object.entries(parsed).forEach(([key, value]) => {
        if (typeof value === "string") {
          normalized[key] = value.trim() || null;
          return;
        }
        if (value === null) {
          normalized[key] = null;
        }
      });
      setMainCategoryAssignments(normalized);
    } catch {
      setMainCategoryAssignments({});
    }
  }, []);

  useEffect(() => {
    window.localStorage.setItem(
      MAIN_CATEGORY_ASSIGNMENT_STORAGE_KEY,
      JSON.stringify(mainCategoryAssignments)
    );
  }, [mainCategoryAssignments]);

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
    if (categories.length === 0) {
      return;
    }
    setCategories((prev) =>
      prev.map((item) => {
        const override = mainCategoryAssignments[String(item.id)];
        if (override === undefined) {
          return item;
        }
        return { ...item, mainCategory: override };
      })
    );
  }, [categories.length, mainCategoryAssignments]);

  useEffect(() => {
    if (!showMainCategoryModal) {
      return;
    }
    if (mainCategoryOptions.length === 0) {
      setRenameMainCategorySource("");
      return;
    }
    if (!renameMainCategorySource || !mainCategoryOptions.includes(renameMainCategorySource)) {
      setRenameMainCategorySource(mainCategoryOptions[0]);
    }
  }, [mainCategoryOptions, renameMainCategorySource, showMainCategoryModal]);

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
    const normalizedMainCategory = mainCategoryName.trim();
    setBusy(true);
    try {
      let created: Category;
      try {
        created = await requestForMarketplace<Category>("MG", "/categories", {
          method: "POST",
          body: JSON.stringify({
            name: categoryName,
            mainCategory: normalizedMainCategory || null,
          }),
        });
      } catch (err) {
        if (!isReadRequestFailure(err)) {
          throw err;
        }
        created = await requestForMarketplace<Category>("MG", "/categories", {
          method: "POST",
          body: JSON.stringify({
            name: categoryName,
          }),
        });
      }
      try {
        try {
          await requestForMarketplace<Category>("YS", "/categories", {
            method: "POST",
            body: JSON.stringify({
              name: categoryName,
              mainCategory: normalizedMainCategory || null,
            }),
          });
        } catch (err) {
          if (!isReadRequestFailure(err)) {
            throw err;
          }
          await requestForMarketplace<Category>("YS", "/categories", {
            method: "POST",
            body: JSON.stringify({
              name: categoryName,
            }),
          });
        }
      } catch {
        // Keep MG as source of truth if YS mirror write fails.
      }
      setCategoryName("");
      setMainCategoryName("");
      setShowAddCategory(false);
      setShowAllCategories(true);
      await loadCategories();
      if (normalizedMainCategory) {
        const assignmentValue = normalizedMainCategory;
        setMainCategoryAssignments((prev) => ({
          ...prev,
          [String(created.id)]: assignmentValue,
        }));
        setCategories((prev) =>
          prev.map((item) =>
            item.id === created.id ? { ...item, mainCategory: assignmentValue } : item
          )
        );
      }
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

  const syncCategoryMainCategory = async (
    category: Category,
    nextMainCategory: string | null
  ) => {
    const normalizedMainCategory = (nextMainCategory ?? "").trim() || null;
    let mainCategorySentToBackend = true;
    try {
      await requestForMarketplace<Category>("MG", `/categories/${category.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: category.name,
          mainCategory: normalizedMainCategory,
        }),
      });
    } catch (err) {
      if (!isReadRequestFailure(err)) {
        throw err;
      }
      mainCategorySentToBackend = false;
      await requestForMarketplace<Category>("MG", `/categories/${category.id}`, {
        method: "PUT",
        body: JSON.stringify({
          name: category.name,
        }),
      });
    }
    try {
      const ysCategories = await requestForMarketplace<Category[]>("YS", "/categories");
      const mirrorCategory = ysCategories.find(
        (item) =>
          item.name.trim().toLocaleLowerCase("tr-TR") ===
          category.name.trim().toLocaleLowerCase("tr-TR")
      );
      if (mirrorCategory) {
        try {
          await requestForMarketplace<Category>("YS", `/categories/${mirrorCategory.id}`, {
            method: "PUT",
            body: JSON.stringify({
              name: mirrorCategory.name,
              mainCategory: normalizedMainCategory,
            }),
          });
        } catch (err) {
          if (!isReadRequestFailure(err)) {
            throw err;
          }
          mainCategorySentToBackend = false;
          await requestForMarketplace<Category>("YS", `/categories/${mirrorCategory.id}`, {
            method: "PUT",
            body: JSON.stringify({
              name: mirrorCategory.name,
            }),
          });
        }
      }
    } catch {
      // Keep MG as source of truth if YS mirror update fails.
    }
    if (!mainCategorySentToBackend) {
      setMainCategoryAssignments((prev) => ({
        ...prev,
        [String(category.id)]: normalizedMainCategory,
      }));
    } else {
      setMainCategoryAssignments((prev) => {
        const next = { ...prev };
        delete next[String(category.id)];
        return next;
      });
    }
    setCategories((prev) =>
      prev.map((item) =>
        item.id === category.id
          ? { ...item, mainCategory: normalizedMainCategory }
          : item
      )
    );
  };

  const handleCreateMainCategory = () => {
    const normalized = newMainCategoryDraft.trim();
    if (!normalized) {
      addNotice("Ana kategori adi gerekli.", "error");
      return false;
    }
    const exists = mainCategoryOptions.some(
      (item) => item.toLocaleLowerCase("tr-TR") === normalized.toLocaleLowerCase("tr-TR")
    );
    if (exists) {
      addNotice("Bu ana kategori zaten var.", "info");
      return false;
    }
    setCustomMainCategories((prev) => [...prev, normalized]);
    setRenameMainCategorySource(normalized);
    setSelectedMainCategoryFilterKey(UNCATEGORIZED_MAIN_CATEGORY_KEY);
    setShowInlineMainCategoryInput(false);
    setNewMainCategoryDraft("");
    addNotice("Ana kategori olusturuldu.", "success");
    return true;
  };

  const renameMainCategory = async (sourceRaw: string, targetRaw: string) => {
    const source = sourceRaw.trim();
    const target = targetRaw.trim();
    if (!source) {
      addNotice("Degistirilecek ana kategoriyi secin.", "error");
      return false;
    }
    if (!target) {
      addNotice("Yeni ana kategori adi gerekli.", "error");
      return false;
    }
    if (source.toLocaleLowerCase("tr-TR") === target.toLocaleLowerCase("tr-TR")) {
      addNotice("Yeni ad mevcut ad ile ayni.", "info");
      return false;
    }
    const hasConflict = mainCategoryOptions.some(
      (item) =>
        item.toLocaleLowerCase("tr-TR") === target.toLocaleLowerCase("tr-TR") &&
        item.toLocaleLowerCase("tr-TR") !== source.toLocaleLowerCase("tr-TR")
    );
    if (hasConflict) {
      addNotice("Bu isimde baska bir ana kategori var.", "error");
      return false;
    }
    setBusy(true);
    try {
      const impactedCategories = categories.filter(
        (category) => (category.mainCategory ?? "").trim() === source
      );
      await Promise.all(
        impactedCategories.map((category) => syncCategoryMainCategory(category, target))
      );
      setCustomMainCategories((prev) =>
        Array.from(
          new Set(
            prev.map((item) => (item === source ? target : item)).filter((item) => item.trim().length > 0)
          )
        )
      );
      setRenameMainCategorySource(target);
      setRenameMainCategoryTarget("");
      addNotice("Ana kategori adi guncellendi.", "success");
      return true;
    } catch (err) {
      addNotice(`Ana kategori guncellenemedi: ${(err as Error).message}`, "error");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const handleRenameMainCategory = async () => {
    await renameMainCategory(renameMainCategorySource, renameMainCategoryTarget);
  };

  const onRenameMainCategoryClick = (event: MouseEvent, source: string) => {
    event.stopPropagation();
    const nextName = window.prompt("Yeni ana kategori adi:", source) ?? "";
    if (!nextName.trim()) {
      return;
    }
    void renameMainCategory(source, nextName);
  };

  const deleteMainCategory = async (sourceRaw: string) => {
    const source = sourceRaw.trim();
    if (!source) {
      addNotice("Silinecek ana kategoriyi secin.", "error");
      return false;
    }
    setBusy(true);
    try {
      const impactedCategories = categories.filter(
        (category) => (category.mainCategory ?? "").trim() === source
      );
      await Promise.all(
        impactedCategories.map((category) => syncCategoryMainCategory(category, null))
      );
      setCustomMainCategories((prev) =>
        prev.filter((item) => item.toLocaleLowerCase("tr-TR") !== source.toLocaleLowerCase("tr-TR"))
      );
      setCategories((prev) =>
        prev.map((item) =>
          (item.mainCategory ?? "").trim() === source ? { ...item, mainCategory: null } : item
        )
      );
      if (selectedMainCategoryFilterKey === source) {
        setSelectedMainCategoryFilterKey(UNCATEGORIZED_MAIN_CATEGORY_KEY);
      }
      addNotice("Ana kategori silindi. Kategoriler korunarak ana kategori bagi kaldirildi.", "success");
      return true;
    } catch (err) {
      addNotice(`Ana kategori silinemedi: ${(err as Error).message}`, "error");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const onDeleteMainCategoryClick = (event: MouseEvent, source: string) => {
    event.stopPropagation();
    void deleteMainCategory(source);
  };

  const handleAssignCategoryToMainCategory = async (
    categoryId: number,
    mainCategoryKey: string
  ) => {
    const category = categories.find((item) => item.id === categoryId);
    if (!category) {
      return;
    }
    const targetMainCategory =
      mainCategoryKey === UNCATEGORIZED_MAIN_CATEGORY_KEY ? null : mainCategoryKey;
    const currentMainCategory = (category.mainCategory ?? "").trim() || null;
    const normalizedTarget = (targetMainCategory ?? "").trim() || null;
    if (currentMainCategory === normalizedTarget) {
      return;
    }
    setBusy(true);
    try {
      await syncCategoryMainCategory(category, normalizedTarget);
      if (normalizedTarget) {
        setCustomMainCategories((prev) => (prev.includes(normalizedTarget) ? prev : [...prev, normalizedTarget]));
      }
      addNotice("Kategori ana kategoriye tasindi.", "success");
    } catch (err) {
      addNotice(`Kategori tasinamadi: ${(err as Error).message}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const handleRemoveCategoryFromMainCategory = async (event: MouseEvent, categoryId: number) => {
    event.stopPropagation();
    const category = categories.find((item) => item.id === categoryId);
    if (!category) {
      return;
    }
    const currentMainCategory = (category.mainCategory ?? "").trim();
    if (!currentMainCategory) {
      return;
    }
    setBusy(true);
    try {
      await syncCategoryMainCategory(category, null);
      addNotice("Kategori ana kategoriden cikarildi. Kategori korunuyor.", "success");
    } catch (err) {
      addNotice(`Kategori ana kategoriden cikarilamadi: ${(err as Error).message}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const onCategoryDragStart = (event: DragEvent, categoryId: number) => {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData(CATEGORY_DRAG_TYPE, String(categoryId));
    setDraggedCategoryId(categoryId);
  };

  const onCategoryDragEnd = () => {
    setDraggedCategoryId(null);
    setActiveMainCategoryDropKey(null);
  };

  const onMainCategoryDragOver = (event: DragEvent, mainCategoryKey: string) => {
    if (!event.dataTransfer.types.includes(CATEGORY_DRAG_TYPE)) {
      return;
    }
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    setActiveMainCategoryDropKey(mainCategoryKey);
  };

  const onMainCategoryDrop = (event: DragEvent, mainCategoryKey: string) => {
    if (!event.dataTransfer.types.includes(CATEGORY_DRAG_TYPE)) {
      return;
    }
    event.preventDefault();
    const raw = event.dataTransfer.getData(CATEGORY_DRAG_TYPE);
    const categoryId = Number.parseInt(raw, 10);
    setActiveMainCategoryDropKey(null);
    setDraggedCategoryId(null);
    if (Number.isFinite(categoryId)) {
      void handleAssignCategoryToMainCategory(categoryId, mainCategoryKey);
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
      const marketplaceCategories = await requestForMarketplace<Category[]>(
        marketplaceCode,
        "/categories"
      );
      const normalizedCategoryName = normalizeProductName(category.name);
      const exact = marketplaceCategories.find(
        (item) => normalizeProductName(item.name) === normalizedCategoryName
      );
      if (exact) {
        return exact.id;
      }
      const loose = marketplaceCategories.find((item) => {
        const normalized = normalizeProductName(item.name);
        return (
          normalized.includes(normalizedCategoryName) ||
          normalizedCategoryName.includes(normalized)
        );
      });
      if (loose) {
        return loose.id;
      }
      const sameId = marketplaceCategories.find((item) => item.id === category.id);
      return sameId?.id ?? null;
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
      const fetchCandidatesWithFallback = async (
        marketplaceCode: MarketplaceCode,
        resolvedCategoryId: number | null
      ) => {
        const idCandidates = Array.from(
          new Set(
            [resolvedCategoryId, category.id].filter(
              (id): id is number => typeof id === "number" && Number.isFinite(id) && id > 0
            )
          )
        );
        if (idCandidates.length === 0) {
          return [] as MarketplaceProductCandidateResponse[];
        }
        let lastError: Error | null = null;
        const mergedByKey = new Map<string, MarketplaceProductCandidateResponse>();
        let successCount = 0;
        for (let index = 0; index < idCandidates.length; index += 1) {
          const categoryId = idCandidates[index];
          try {
            const rows = await requestForMarketplace<MarketplaceProductCandidateResponse[]>(
              marketplaceCode,
              `/categories/${categoryId}/marketplace-products`
            );
            const filtered = rows.filter(
              (item) => item.marketplaceCode === marketplaceCode
            );
            filtered.forEach((item) => {
              const key = `${item.marketplaceCode}:${item.externalId}`;
              if (!mergedByKey.has(key)) {
                mergedByKey.set(key, item);
              }
            });
            successCount += 1;
          } catch (err) {
            lastError = err as Error;
          }
        }
        if (mergedByKey.size > 0 || successCount > 0) {
          return [...mergedByKey.values()];
        }
        if (lastError) {
          throw lastError;
        }
        return [] as MarketplaceProductCandidateResponse[];
      };
      const [ysCandidates, mgCandidates] = await Promise.all([
        fetchCandidatesWithFallback("YS", ysCategoryId),
        fetchCandidatesWithFallback("MG", mgCategoryId),
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
    const existingCandidates = candidateItemsByCategory[category.id] ?? [];
    const hasYsCandidates = existingCandidates.some((item) => item.marketplaceCode === "YS");
    const hasMgCandidates = existingCandidates.some((item) => item.marketplaceCode === "MG");
    if (existingCandidates.length === 0 || !hasYsCandidates || !hasMgCandidates) {
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
    unit: candidate.unit,
    unitValue: candidate.unitValue,
    packCount: candidate.packCount,
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
      try {
        await refreshTotalMarketplaceProductCount();
      } catch {
        // Counter refresh failure should not block successful delete UX.
      }
      addNotice("Kategori silindi.", "success");
    } catch (err) {
      addNotice(`Kategori silinemedi: ${(err as Error).message}`, "error");
    } finally {
      setBusy(false);
    }
  };

  const renameCategory = async (category: Category, nextNameRaw: string) => {
    const nextName = nextNameRaw.trim();
    if (!nextName) {
      addNotice("Kategori adi bos olamaz.", "error");
      return false;
    }
    if (nextName.toLocaleLowerCase("tr-TR") === category.name.trim().toLocaleLowerCase("tr-TR")) {
      addNotice("Yeni kategori adi mevcut ad ile ayni.", "info");
      return false;
    }
    setBusy(true);
    try {
      const mainCategory = (category.mainCategory ?? "").trim() || null;
      try {
        await requestForMarketplace<Category>("MG", `/categories/${category.id}`, {
          method: "PUT",
          body: JSON.stringify({
            name: nextName,
            mainCategory,
          }),
        });
      } catch (err) {
        if (!isReadRequestFailure(err)) {
          throw err;
        }
        await requestForMarketplace<Category>("MG", `/categories/${category.id}`, {
          method: "PUT",
          body: JSON.stringify({
            name: nextName,
          }),
        });
      }
      try {
        const ysCategories = await requestForMarketplace<Category[]>("YS", "/categories");
        const mirrorCategory = ysCategories.find(
          (item) =>
            item.name.trim().toLocaleLowerCase("tr-TR") ===
            category.name.trim().toLocaleLowerCase("tr-TR")
        );
        if (mirrorCategory) {
          try {
            await requestForMarketplace<Category>("YS", `/categories/${mirrorCategory.id}`, {
              method: "PUT",
              body: JSON.stringify({
                name: nextName,
                mainCategory,
              }),
            });
          } catch (err) {
            if (!isReadRequestFailure(err)) {
              throw err;
            }
            await requestForMarketplace<Category>("YS", `/categories/${mirrorCategory.id}`, {
              method: "PUT",
              body: JSON.stringify({
                name: nextName,
              }),
            });
          }
        }
      } catch {
        // Keep MG as source of truth if YS mirror update fails.
      }
      setCategories((prev) =>
        prev.map((item) =>
          item.id === category.id
            ? { ...item, name: nextName }
            : item
        )
      );
      setNeedList((prev) =>
        prev.map((item) =>
          item.categoryId === category.id
            ? {
                ...item,
                categoryName: nextName,
                name:
                  item.type === "CATEGORY"
                    ? `${formatCategoryTitle(nextName)} (Kategori)`
                    : item.name,
              }
            : item
        )
      );
      addNotice("Kategori adi guncellendi.", "success");
      return true;
    } catch (err) {
      addNotice(`Kategori adi guncellenemedi: ${(err as Error).message}`, "error");
      return false;
    } finally {
      setBusy(false);
    }
  };

  const startInlineCategoryRename = (event: MouseEvent, category: Category) => {
    event.stopPropagation();
    setEditingCategoryId(category.id);
    setEditingCategoryName(category.name);
  };

  const submitInlineCategoryRename = async (category: Category) => {
    const nextName = editingCategoryName.trim();
    if (!nextName) {
      addNotice("Kategori adi bos olamaz.", "error");
      return;
    }
    const renamed = await renameCategory(category, nextName);
    if (renamed) {
      setEditingCategoryId(null);
      setEditingCategoryName("");
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
    const matchedPair = addedMatchPairs.find(
      (pair) =>
        (pair.ys.marketplaceCode === candidate.marketplaceCode &&
          pair.ys.externalId === candidate.externalId) ||
        (pair.mg.marketplaceCode === candidate.marketplaceCode &&
          pair.mg.externalId === candidate.externalId)
    );
    if (!matchedPair) {
      return null;
    }
    const target =
      matchedPair.ys.marketplaceCode === candidate.marketplaceCode &&
      matchedPair.ys.externalId === candidate.externalId
        ? matchedPair.mg
        : matchedPair.ys;
    return {
      target,
      score: matchedPair.score,
      autoLinkEligible: matchedPair.autoLinkEligible,
    };
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
    const autoRemoveTarget = bestMatch ? bestMatch.target : null;
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

  const isNeedProductAdded = useCallback((
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
    ), [needList]);

  const isNeedCategoryAdded = (categoryId: number) =>
    needList.some((item) => item.type === "CATEGORY" && item.categoryId === categoryId);

  const handleAddSingleProductToNeedList = useCallback((
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
  }, [addNotice, isNeedProductAdded, opportunitiesByCategory, selectedNeedUrgency]);

  const handleRemoveSingleProductFromNeedList = useCallback((
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
  }, [addNotice]);

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
        unit: item.unit ?? null,
        unitValue: item.unitValue ?? null,
        packCount: item.packCount ?? null,
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
  const mainCategoryScopedCategories = useMemo(() => {
    if (!selectedMainCategoryFilterKey) {
      return filteredSortedCategories;
    }
    if (selectedMainCategoryFilterKey === UNCATEGORIZED_MAIN_CATEGORY_KEY) {
      return filteredSortedCategories.filter(
        (category) => !(category.mainCategory ?? "").trim()
      );
    }
    return filteredSortedCategories.filter(
      (category) => (category.mainCategory ?? "").trim() === selectedMainCategoryFilterKey
    );
  }, [filteredSortedCategories, selectedMainCategoryFilterKey]);
  const categoryTotalPages = isCategoryFilterActive
    ? 1
    : Math.max(1, Math.ceil(mainCategoryScopedCategories.length / CATEGORY_PAGE_SIZE));
  const currentCategoryPage = isCategoryFilterActive
    ? 0
    : Math.min(categoryPage, categoryTotalPages - 1);
  const pagedCategories = useMemo(() => {
    if (isCategoryFilterActive) {
      return mainCategoryScopedCategories;
    }
    const start = currentCategoryPage * CATEGORY_PAGE_SIZE;
    return mainCategoryScopedCategories.slice(start, start + CATEGORY_PAGE_SIZE);
  }, [currentCategoryPage, isCategoryFilterActive, mainCategoryScopedCategories]);
  const visibleCategories = isCategoryFilterActive
    ? mainCategoryScopedCategories
    : currentCategoryPage === 0 && !showAllCategories
      ? pagedCategories.slice(0, CATEGORY_VISIBLE_COUNT)
      : pagedCategories;
  const standaloneVisibleCategories = useMemo(() => {
    if (selectedMainCategoryFilterKey) {
      return visibleCategories;
    }
    const standalonePagedCategories = pagedCategories.filter(
      (category) => !(category.mainCategory ?? "").trim()
    );
    if (isCategoryFilterActive) {
      return mainCategoryScopedCategories.filter((category) => !(category.mainCategory ?? "").trim());
    }
    if (currentCategoryPage === 0 && !showAllCategories) {
      return standalonePagedCategories.slice(0, CATEGORY_VISIBLE_COUNT);
    }
    return standalonePagedCategories;
  }, [
    currentCategoryPage,
    isCategoryFilterActive,
    mainCategoryScopedCategories,
    selectedMainCategoryFilterKey,
    pagedCategories,
    showAllCategories,
    visibleCategories,
  ]);
  const mainCategoryGroups = useMemo(() => {
    const groups = new Map<string, { key: string; label: string; categories: Category[] }>();
    groups.set(UNCATEGORIZED_MAIN_CATEGORY_KEY, {
      key: UNCATEGORIZED_MAIN_CATEGORY_KEY,
      label: UNCATEGORIZED_MAIN_CATEGORY_LABEL,
      categories: [],
    });
    mainCategoryOptions.forEach((name) => {
      groups.set(name, { key: name, label: name, categories: [] });
    });
    filteredSortedCategories.forEach((category) => {
      const normalizedMainCategory = (category.mainCategory ?? "").trim();
      const key = normalizedMainCategory || UNCATEGORIZED_MAIN_CATEGORY_KEY;
      const existing = groups.get(key);
      if (existing) {
        existing.categories.push(category);
        return;
      }
      groups.set(key, {
        key,
        label: normalizedMainCategory || UNCATEGORIZED_MAIN_CATEGORY_LABEL,
        categories: [category],
      });
    });
    const groupList = Array.from(groups.values());
    return groupList.sort((left, right) => {
      if (left.key === UNCATEGORIZED_MAIN_CATEGORY_KEY) {
        return -1;
      }
      if (right.key === UNCATEGORIZED_MAIN_CATEGORY_KEY) {
        return 1;
      }
      return left.label.localeCompare(right.label, "tr-TR", { sensitivity: "base" });
    });
  }, [filteredSortedCategories, mainCategoryOptions]);
  const displayedMainCategoryGroups = useMemo(() => mainCategoryGroups, [mainCategoryGroups]);
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
    setCategoryPage(0);
    setShowAllCategories(false);
  }, [selectedMainCategoryFilterKey]);

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

  useEffect(() => {
    let cancelled = false;
    const ys = activeCandidates.filter((item) => item.marketplaceCode === "YS");
    const mg = activeCandidates.filter((item) => item.marketplaceCode === "MG");
    if (!activeCategory || ys.length === 0 || mg.length === 0) {
      setSuggestedMatchPairs([]);
      return () => {
        cancelled = true;
      };
    }
    void matchMarketplacePairs(activeCategory.id, ys, mg, MATCH_MIN_SCORE, true).then((pairs) => {
      if (!cancelled) {
        setSuggestedMatchPairs(pairs);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [activeCandidates, activeCategory, matchMarketplacePairs]);

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

  const suggestedMatchPairByKey = useMemo(() => {
    const pairByKey: Record<string, MarketplaceProductMatchPairResponse> = {};
    suggestedMatchPairs.forEach((pair) => {
      const ysKey = candidateKey(pair.ys);
      const mgKey = candidateKey(pair.mg);
      const ysExisting = pairByKey[ysKey];
      const mgExisting = pairByKey[mgKey];
      if (!ysExisting || pair.score.score > ysExisting.score.score) {
        pairByKey[ysKey] = pair;
      }
      if (!mgExisting || pair.score.score > mgExisting.score.score) {
        pairByKey[mgKey] = pair;
      }
    });
    return pairByKey;
  }, [suggestedMatchPairs]);

  const addedMatchPairs = useMemo(
    () =>
      suggestedMatchPairs.filter(
        (pair) =>
          addedCandidateIds.has(`${pair.ys.marketplaceCode}:${pair.ys.externalId}`) &&
          addedCandidateIds.has(`${pair.mg.marketplaceCode}:${pair.mg.externalId}`)
      ),
    [suggestedMatchPairs, addedCandidateIds]
  );

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

  const manualMatchOptions = useMemo(() => {
    if (!selectedAddedProduct || !selectedProductCategory) {
      return [];
    }
    const oppositeMarketplaceCode: MarketplaceCode =
      selectedAddedProduct.marketplaceCode === "YS" ? "MG" : "YS";
    const categoryRows = addedProductsByCategory[selectedProductCategory.id]?.[oppositeMarketplaceCode] ?? [];
    return categoryRows
      .filter((item) => item.externalId !== selectedAddedProduct.externalId)
      .map((item) => ({
        externalId: item.externalId,
        label: `${item.name || "Isimsiz urun"} | ${item.brandName || "Marka yok"}`,
      }));
  }, [addedProductsByCategory, selectedAddedProduct, selectedProductCategory]);

  const saveManualMatch = useCallback(async (
    categoryId: number,
    sourceMarketplaceCode: MarketplaceCode,
    sourceExternalId: string,
    targetExternalId: string
  ) => {
    const payload: MarketplaceManualMatchRequest =
      sourceMarketplaceCode === "YS"
        ? {
            ysExternalId: sourceExternalId,
            mgExternalId: targetExternalId,
          }
        : {
            ysExternalId: targetExternalId,
            mgExternalId: sourceExternalId,
          };
    setManualMatchBusy(true);
    try {
      await request<void>(
        `/categories/${categoryId}/marketplace-products/manual-match`,
        {
          method: "POST",
          body: JSON.stringify(payload),
        }
      );
      addNotice("Manuel eslestirme kaydedildi.", "success");
      if (activeCategory?.id === categoryId) {
        const ysCandidates = activeCandidates.filter((item) => item.marketplaceCode === "YS");
        const mgCandidates = activeCandidates.filter((item) => item.marketplaceCode === "MG");
        if (ysCandidates.length > 0 && mgCandidates.length > 0) {
          const refreshedSuggestedPairs = await matchMarketplacePairs(
            categoryId,
            ysCandidates,
            mgCandidates,
            MATCH_MIN_SCORE,
            true
          );
          setSuggestedMatchPairs(refreshedSuggestedPairs);
        }
      }
      setManualMatchSource(null);
    } catch (err) {
      addNotice(`Manuel eslestirme kaydedilemedi: ${(err as Error).message}`, "error");
    } finally {
      setManualMatchBusy(false);
    }
  }, [
    addNotice,
    matchMarketplacePairs,
    activeCategory,
    activeCandidates,
  ]);

  const handleCreateManualMatch = useCallback(async (targetExternalId: string) => {
    if (!selectedAddedProduct || !selectedProductCategory) {
      return;
    }
    const sourceMarketplaceCode = asMarketplaceCode(selectedAddedProduct.marketplaceCode);
    if (!sourceMarketplaceCode) {
      addNotice("Manuel eslestirme icin gecersiz marketplace.", "error");
      return;
    }
    await saveManualMatch(
      selectedProductCategory.id,
      sourceMarketplaceCode,
      selectedAddedProduct.externalId,
      targetExternalId
    );
  }, [selectedAddedProduct, selectedProductCategory, addNotice, saveManualMatch]);

  const handleStartManualMatchFromCandidate = useCallback((
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const marketplaceCode = asMarketplaceCode(candidate.marketplaceCode);
    if (!marketplaceCode) {
      addNotice("Manuel eslestirme icin gecersiz marketplace.", "error");
      return;
    }
    setManualMatchSource({
      categoryId: category.id,
      marketplaceCode,
      externalId: candidate.externalId,
      name: candidate.name || `Urun ${candidate.externalId}`,
    });
    const targetMarketplace = marketplaceCode === "YS" ? "Migros" : "Yemeksepeti";
    addNotice(`Eslestirme modu acildi. Simdi ${targetMarketplace} tarafindan hedef urun sec.`, "info");
  }, [addNotice]);

  const candidateToAddedEntry = useCallback(
    (candidate: MarketplaceProductCandidateResponse): MarketplaceProductEntryResponse => ({
      marketplaceCode: candidate.marketplaceCode,
      externalId: candidate.externalId,
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
      unit: candidate.unit,
      unitValue: candidate.unitValue,
      packCount: candidate.packCount,
    }),
    []
  );

  const isNeedProductAddedForCandidate = useCallback(
    (category: Category, candidate: MarketplaceProductCandidateResponse) => {
      const marketplaceCode = asMarketplaceCode(candidate.marketplaceCode);
      if (!marketplaceCode) {
        return false;
      }
      return isNeedProductAdded(category.id, marketplaceCode, candidate.externalId);
    },
    [isNeedProductAdded]
  );

  const handleToggleNeedForCandidate = useCallback(
    (category: Category, candidate: MarketplaceProductCandidateResponse) => {
      const entry = candidateToAddedEntry(candidate);
      if (isNeedProductAddedForCandidate(category, candidate)) {
        handleRemoveSingleProductFromNeedList(category, entry);
        return;
      }
      handleAddSingleProductToNeedList(category, entry);
    },
    [
      candidateToAddedEntry,
      isNeedProductAddedForCandidate,
      handleRemoveSingleProductFromNeedList,
      handleAddSingleProductToNeedList,
    ]
  );

  const handleSelectManualMatchTarget = useCallback(async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    if (!manualMatchSource) {
      return;
    }
    if (manualMatchSource.categoryId !== category.id) {
      addNotice("Eslestirme ayni kategori icinde yapilmali.", "error");
      return;
    }
    if (manualMatchSource.marketplaceCode === candidate.marketplaceCode) {
      addNotice("Hedef urun karsi markette olmali.", "error");
      return;
    }
    await saveManualMatch(
      category.id,
      manualMatchSource.marketplaceCode,
      manualMatchSource.externalId,
      candidate.externalId
    );
  }, [manualMatchSource, saveManualMatch, addNotice]);

  const handleRemoveManualMatchFromCandidate = useCallback(async (
    category: Category,
    candidate: MarketplaceProductCandidateResponse
  ) => {
    const pair = suggestedMatchPairs.find(
      (item) =>
        (item.ys.marketplaceCode === candidate.marketplaceCode &&
          item.ys.externalId === candidate.externalId) ||
        (item.mg.marketplaceCode === candidate.marketplaceCode &&
          item.mg.externalId === candidate.externalId)
    );
    if (!pair || !pair.manualMatch) {
      addNotice("Bu urun icin silinebilecek manuel eslestirme bulunamadi.", "info");
      return;
    }
    setManualMatchBusy(true);
    try {
      const qs = new URLSearchParams({
        ysExternalId: pair.ys.externalId,
        mgExternalId: pair.mg.externalId,
      });
      await request<void>(
        `/categories/${category.id}/marketplace-products/manual-match?${qs.toString()}`,
        { method: "DELETE" }
      );
      addNotice("Manuel eslestirme silindi.", "success");
      const ysCandidates = activeCandidates.filter((item) => item.marketplaceCode === "YS");
      const mgCandidates = activeCandidates.filter((item) => item.marketplaceCode === "MG");
      if (ysCandidates.length > 0 && mgCandidates.length > 0) {
        const refreshedSuggestedPairs = await matchMarketplacePairs(
          category.id,
          ysCandidates,
          mgCandidates,
          MATCH_MIN_SCORE,
          true
        );
        setSuggestedMatchPairs(refreshedSuggestedPairs);
      }
    } catch (err) {
      addNotice(`Manuel eslestirme silinemedi: ${(err as Error).message}`, "error");
    } finally {
      setManualMatchBusy(false);
      setManualMatchSource(null);
    }
  }, [
    suggestedMatchPairs,
    addNotice,
    activeCandidates,
    matchMarketplacePairs,
  ]);

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
          <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-center text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-amber-700">
              Toplam kategori
            </p>
            <p className="display text-2xl">{categories.length}</p>
          </div>
          <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-center text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-emerald-700">
              Toplam urun
            </p>
            <p className="display text-2xl">{totalMarketplaceProductCount}</p>
          </div>
          <div className="rounded-2xl border border-fuchsia-200 bg-fuchsia-50 px-4 py-3 text-center text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-fuchsia-700">
              Eslesen urun
            </p>
            <p className="display text-2xl">{matchedMarketplaceProductCount}</p>
          </div>
          <div className="rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-center text-sm">
            <p className="text-xs uppercase tracking-[0.2em] text-sky-700">
              Alinabilecek urun
            </p>
            <p className="display text-2xl">{basketSuggestionCount}</p>
          </div>
          <div className="rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-center text-sm">
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
            <div className="flex items-center gap-2">
              <button
                className="flex h-10 w-10 items-center justify-center rounded-full bg-[#d97706] text-xl font-semibold text-white transition hover:bg-[#b45309]"
                type="button"
                onClick={() => {
                  setCategoryName("");
                  setMainCategoryName("");
                  setShowAddCategory(true);
                }}
                title="Kategori ekle"
              >
                +
              </button>
            </div>
          </div>

          {!isCategoryListCollapsed && (
          <div className="mt-6 space-y-3">
            <div className="rounded-2xl border border-black/10 bg-[#fff8ef] p-2.5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#9a5c00]">
                Ana Kategoriler
              </p>
              <p className="mt-1 text-[11px] text-[#6b655c]">
                Bir kategori kartini surukleyip bir ana kategori kutusuna birakin.
              </p>
              <div className="mt-1.5 flex gap-1.5">
                <div className="relative w-[220px] max-w-full sm:w-[260px]">
                  <svg
                    viewBox="0 0 16 16"
                    className="pointer-events-none absolute left-2 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-[#9ca3af]"
                    aria-hidden
                  >
                    <path
                      d="M11.2 10.2 14 13m-3.5-6A4.5 4.5 0 1 1 1.5 7a4.5 4.5 0 0 1 9 0Z"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                  <input
                    id="category-filter"
                    type="text"
                    value={categoryFilterQuery}
                    list="category-filter-suggestions"
                    onChange={(event) => setCategoryFilterQuery(event.target.value)}
                    placeholder="Ara..."
                    className="h-7 w-full rounded-lg border border-black/10 bg-white pl-7 pr-2 text-[11px] text-[#111] outline-none ring-[#d97706] placeholder:text-[#9ca3af] focus:ring-2"
                  />
                </div>
                {categoryFilterQuery.trim().length > 0 && (
                  <button
                    type="button"
                    onClick={() => setCategoryFilterQuery("")}
                    className="h-7 shrink-0 rounded-lg border border-black/10 bg-white px-2 text-[10px] font-semibold uppercase tracking-[0.08em] text-[#6b655c] transition hover:bg-amber-50"
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
                  {standaloneVisibleCategories.length} kategori bulundu.
                </p>
              )}
              <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
                {displayedMainCategoryGroups.map((group) => {
                  const isActiveDropZone = activeMainCategoryDropKey === group.key;
                  const isSelected = selectedMainCategoryFilterKey === group.key;
                  return (
                    <div key={`main-category-drop-${group.key}`} className="inline-flex items-center gap-2">
                      <div
                        className={`inline-flex h-8 w-fit cursor-default select-none items-center gap-2 rounded-full border px-4 transition ${
                          isActiveDropZone
                            ? "border-[#d97706] bg-amber-100"
                            : isSelected
                              ? "border-[#b45309] bg-[#d97706] text-white shadow-[0_8px_18px_-14px_rgba(180,83,9,0.9)]"
                            : "border-black/10 bg-white"
                        }`}
                        role="button"
                        tabIndex={0}
                        onClick={() =>
                          setSelectedMainCategoryFilterKey((current) =>
                            current === group.key ? null : group.key
                          )
                        }
                        onKeyDown={(event) => {
                          if (event.key !== "Enter" && event.key !== " ") {
                            return;
                          }
                          event.preventDefault();
                          setSelectedMainCategoryFilterKey((current) =>
                            current === group.key ? null : group.key
                          );
                        }}
                        onDragOver={(event) => onMainCategoryDragOver(event, group.key)}
                        onDragEnter={() => setActiveMainCategoryDropKey(group.key)}
                        onDragLeave={() => {
                          if (activeMainCategoryDropKey === group.key) {
                            setActiveMainCategoryDropKey(null);
                          }
                        }}
                        onDrop={(event) => onMainCategoryDrop(event, group.key)}
                      >
                        <p
                          className={`truncate text-xs font-semibold uppercase leading-none tracking-[0.12em] ${
                            isSelected ? "text-white" : "text-[#6b655c]"
                          }`}
                        >
                          {group.label}
                        </p>
                        {group.key !== UNCATEGORIZED_MAIN_CATEGORY_KEY && (
                          <>
                            <button
                              type="button"
                              className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-amber-200 bg-amber-50 text-[#9a5c00] transition hover:bg-amber-100"
                              onClick={(event) => onRenameMainCategoryClick(event, group.key)}
                              title="Ana kategori adini degistir"
                            >
                              <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                                <path
                                  d="M3 11.8 11.7 3a1.2 1.2 0 0 1 1.7 0l.3.3a1.2 1.2 0 0 1 0 1.7L5 13.8 2.5 14l.2-2.2Z"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="1.2"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                              </svg>
                            </button>
                            <button
                              type="button"
                              className="inline-flex h-6 w-6 items-center justify-center rounded-full border border-rose-200 bg-rose-50 text-rose-700 transition hover:bg-rose-100"
                              onClick={(event) => onDeleteMainCategoryClick(event, group.key)}
                              title="Ana kategoriyi sil (kategoriler silinmez)"
                            >
                              <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                                <path
                                  d="M3.2 4.5h9.6M6 4.5V3.4c0-.4.3-.7.7-.7h2.6c.4 0 .7.3.7.7v1.1M4.6 4.5l.6 7.8c0 .5.4.9.9.9h4c.5 0 .9-.4.9-.9l.6-7.8"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="1.2"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                />
                              </svg>
                            </button>
                          </>
                        )}
                      </div>
                    </div>
                  );
                })}
                <div className="ml-auto inline-flex items-center gap-2">
                  {showInlineMainCategoryInput && (
                    <input
                      className="h-8 w-44 rounded-full border border-amber-300 bg-white px-3 text-xs text-[#111] outline-none ring-[#d97706] focus:ring-2"
                      placeholder="Ana kategori adi"
                      value={newMainCategoryDraft}
                      autoFocus
                      onChange={(event) => setNewMainCategoryDraft(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          handleCreateMainCategory();
                          return;
                        }
                        if (event.key === "Escape") {
                          event.preventDefault();
                          setShowInlineMainCategoryInput(false);
                          setNewMainCategoryDraft("");
                        }
                      }}
                    />
                  )}
                  <button
                    type="button"
                    className={`h-8 rounded-full border border-[#0f766e] bg-[#14b8a6] px-4 text-[11px] font-semibold uppercase tracking-[0.12em] text-white transition hover:bg-[#0d9488] ${
                      showInlineMainCategoryInput ? "ml-auto" : ""
                    }`}
                    onClick={() => {
                      if (!showInlineMainCategoryInput) {
                        setShowInlineMainCategoryInput(true);
                        return;
                      }
                      handleCreateMainCategory();
                    }}
                    title="Ana kategori ekle"
                  >
                    {showInlineMainCategoryInput ? "Ekle" : "Ana Kategori Ekle"}
                  </button>
                </div>
              </div>
            </div>
            {standaloneVisibleCategories.map((category) => (
              <div
                key={category.id}
                ref={(node) => {
                  if (node) {
                    categoryRefs.current.set(category.id, node);
                  } else {
                    categoryRefs.current.delete(category.id);
                  }
                }}
                draggable={!busy}
                onDragStart={(event) => onCategoryDragStart(event, category.id)}
                onDragEnd={onCategoryDragEnd}
                className={`rounded-2xl border border-black/5 bg-[#f9f4ee] p-4 transition overflow-hidden ${
                  expandedCategoryId === category.id
                    ? "shadow-[0_20px_40px_-35px_rgba(0,0,0,0.5)]"
                    : ""
                } ${
                  draggedCategoryId === category.id ? "opacity-60" : ""
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
                  {editingCategoryId === category.id ? (
                    <input
                      className={`category-title h-9 rounded-xl border border-amber-300 bg-white px-3 text-lg text-[#111] outline-none ring-[#d97706] focus:ring-2 ${
                        expandedCategoryId === category.id
                          ? "mx-auto text-center"
                          : "text-left"
                      }`}
                      value={editingCategoryName}
                      autoFocus
                      onClick={(event) => event.stopPropagation()}
                      onChange={(event) => setEditingCategoryName(event.target.value)}
                      onKeyDown={(event) => {
                        event.stopPropagation();
                        if (event.key === "Enter") {
                          event.preventDefault();
                          void submitInlineCategoryRename(category);
                          return;
                        }
                        if (event.key === "Escape") {
                          event.preventDefault();
                          setEditingCategoryId(null);
                          setEditingCategoryName("");
                        }
                      }}
                      onBlur={() => {
                        setEditingCategoryId(null);
                        setEditingCategoryName("");
                      }}
                    />
                  ) : (
                    <p
                      className={`category-title text-lg transition-all ${
                        expandedCategoryId === category.id
                          ? "mx-auto text-center"
                          : "text-left"
                      }`}
                    >
                      {formatCategoryTitle(category.name)}
                    </p>
                  )}
                  {category.mainCategory && category.mainCategory.trim() && (
                    <span className="ml-2 inline-flex items-center gap-1 rounded-full border border-[#f4d8aa] bg-[#fff4df] px-2 py-0.5 text-[11px] font-medium text-[#9a5c00]">
                      {category.mainCategory}
                      <button
                        type="button"
                        className="inline-flex h-4 w-4 items-center justify-center rounded-full border border-amber-200 bg-amber-50 text-[#9a5c00] transition hover:bg-amber-100"
                        onClick={(event) => {
                          void handleRemoveCategoryFromMainCategory(event, category.id);
                        }}
                        title="Bu kategoriyi ana kategoriden cikar"
                      >
                        <svg viewBox="0 0 16 16" className="h-2.5 w-2.5" aria-hidden>
                          <path
                            d="M4 8h8"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.6"
                            strokeLinecap="round"
                          />
                        </svg>
                      </button>
                    </span>
                  )}
                  <div
                    className={`flex items-center gap-2 ${
                      expandedCategoryId === category.id ? "absolute right-0" : "ml-3"
                    }`}
                  >
                    <button
                      type="button"
                      className="inline-flex h-7 w-7 items-center justify-center rounded-full border border-amber-200 bg-amber-50 text-[#9a5c00] transition hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
                      onClick={(event) => startInlineCategoryRename(event, category)}
                      disabled={busy}
                      title="Kategori adini degistir"
                    >
                      <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" aria-hidden>
                        <path
                          d="M3 11.8 11.7 3a1.2 1.2 0 0 1 1.7 0l.3.3a1.2 1.2 0 0 1 0 1.7L5 13.8 2.5 14l.2-2.2Z"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    </button>
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
                                            {getProductQuantityText(item) && (
                                              <p className="truncate text-[11px] text-[#6b655c]">
                                                {getProductQuantityText(item)}
                                              </p>
                                            )}
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
                      {manualMatchSource && manualMatchSource.categoryId === category.id && (
                        <div className="mt-3 rounded-2xl border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-[#9a5c00]">
                          <span className="font-semibold">Eslestirme modu:</span>{" "}
                          {manualMatchSource.name} secildi. Simdi{" "}
                          {manualMatchSource.marketplaceCode === "YS" ? "Migros" : "Yemeksepeti"} tarafindan hedef urunu sec.
                          <button
                            type="button"
                            className="ml-2 rounded-lg border border-black/10 bg-white px-2 py-0.5 text-[11px] font-semibold text-[#6b655c] transition hover:bg-[#f4ede3]"
                            onClick={() => setManualMatchSource(null)}
                          >
                            Iptal
                          </button>
                        </div>
                      )}
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
                                          <Image src={item.imageUrl} alt={item.name} fill sizes="40px" className="object-cover" unoptimized />
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
                                        {getProductQuantityText(item) && (
                                          <p className="truncate text-[11px] text-[#6b655c]">
                                            {getProductQuantityText(item)}
                                          </p>
                                        )}
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
                              manualMatchSource={manualMatchSource}
                              manualMatchBusy={manualMatchBusy}
                              onStartManualMatch={handleStartManualMatchFromCandidate}
                              onSelectManualMatchTarget={handleSelectManualMatchTarget}
                              onRemoveManualMatch={handleRemoveManualMatchFromCandidate}
                              isNeedProductAddedForCandidate={isNeedProductAddedForCandidate}
                              onToggleNeedForCandidate={handleToggleNeedForCandidate}
                              suggestedMatchPairByKey={suggestedMatchPairByKey}
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
              (selectedMainCategoryFilterKey
                ? pagedCategories.length > CATEGORY_VISIBLE_COUNT
                : pagedCategories.filter((category) => !(category.mainCategory ?? "").trim()).length >
                  CATEGORY_VISIBLE_COUNT) && (
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
                    if (expandedCategoryId !== null) {
                      setExpandedCategoryId(null);
                      setSelectedAddedProduct(null);
                      setSelectedProductCategory(null);
                    }
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
                    if (expandedCategoryId !== null) {
                      setExpandedCategoryId(null);
                      setSelectedAddedProduct(null);
                      setSelectedProductCategory(null);
                    }
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
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fffdf1_0%,#fff6b8_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Efektif Fiyat
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Money uyesi indirimi ve kampanyali adet fiyati varsa tekil birim
                maliyetini hesaplar. Ayarlarda acik ise karsilastirmalarda bu
                fiyat kullanilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff8f2_0%,#ffd6b3_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Ayarlar
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Money Uyelik acildiginda Migros Money fiyatlari baz alinir.
                Efektif Fiyat acildiginda kampanyalar tekil fiyata indirgenir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff6f6_0%,#f6a9a9_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Ihtiyac Listesi
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Urun modalinda &quot;Ihtiyac Listesine Ekle&quot; urunu,
                &quot;Kategoriyi Ihtiyac Listesine Ekle&quot; tum kategoriyi
                listeye ekler.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fffdf1_0%,#fff6b8_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Sepet Onerileri
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Ihtiyac listesine gore zorunlu onerileri ve ihtiyac disi genel
                firsat onerilerini ayri katmanlarda sunar. Minimum sepet tutari
                icin gerekli ek urun ihtiyacini gosterir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff8f2_0%,#ffd6b3_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Ihtiyactan Onerilenler
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Ihtiyac listesinde olan urun/kategorilerden uretilir. Aciliyet
                ve alinabilirlik skoruna gore onceliklendirilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff6f6_0%,#f6a9a9_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Genel Firsatlar
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Ihtiyac listesinde olmayan ama fiyat seviyesi dusuk veya kampanyali
                gorunen urunleri gosterir. Zorunlu degil, firsat odaklidir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fffdf1_0%,#fff6b8_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Alinabilirlik
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Son fiyat gecmisi icindeki siralama ve bugunku fiyatin konumu
                kullanilarak hesaplanir. Skor &gt;= 65 Uygun, 40-64 Normal, 40
                altinda Pahali. Skor yoksa Normal kabul edilir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff8f2_0%,#ffd6b3_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Marketler
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
                Desteklenen marketler: Yemeksepeti (YS) ve Migros (MG). Urunler
                her market icin ayri baglanir.
              </p>
            </div>
            <div className="rounded-2xl border border-black/5 bg-[linear-gradient(160deg,#fff6f6_0%,#f6a9a9_100%)] p-3 shadow-[0_8px_20px_-16px_rgba(0,0,0,0.35)]">
              <p className="text-[11px] uppercase tracking-[0.16em] text-[#7c3f00]">
                Ihtiyac Aciliyeti
              </p>
              <p className="mt-2 text-sm text-[#4f4a44]">
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
        manualMatchOptions={manualMatchOptions}
        onCreateManualMatchAction={handleCreateManualMatch}
        manualMatchBusy={manualMatchBusy}
      />

      {showMainCategoryModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-md rounded-3xl border border-black/10 bg-white p-6 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.55)]">
            <div className="flex items-center justify-between">
              <h3 className="display text-xl">Ana Kategori Yonetimi</h3>
              <button
                className="text-sm text-[#6b655c] transition hover:text-[#111]"
                type="button"
                onClick={() => setShowMainCategoryModal(false)}
              >
                Kapat
              </button>
            </div>

            <div className="mt-4 rounded-2xl border border-black/10 bg-[#fcfaf7] p-3">
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#9a5c00]">
                Yeni Ana Kategori
              </p>
              <div className="mt-2 flex gap-2">
                <input
                  className="h-10 w-full rounded-xl border border-black/10 bg-white px-3 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                  placeholder="Ana kategori adi"
                  value={newMainCategoryDraft}
                  onChange={(event) => setNewMainCategoryDraft(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter" || busy) {
                      return;
                    }
                    event.preventDefault();
                    handleCreateMainCategory();
                  }}
                />
                <button
                  className="h-10 shrink-0 rounded-xl bg-[#d97706] px-3 text-xs font-semibold uppercase tracking-[0.12em] text-white transition hover:bg-[#b45309] disabled:cursor-not-allowed disabled:opacity-60"
                  type="button"
                  onClick={handleCreateMainCategory}
                  disabled={busy}
                >
                  Ekle
                </button>
              </div>
            </div>

            <div className="mt-3 rounded-2xl border border-black/10 bg-[#fcfaf7] p-3">
              <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#9a5c00]">
                Ana Kategori Adi Degistir
              </p>
              <select
                className="mt-2 h-10 w-full rounded-xl border border-black/10 bg-white px-3 text-sm text-[#111] outline-none ring-[#d97706] focus:ring-2"
                value={renameMainCategorySource}
                onChange={(event) => setRenameMainCategorySource(event.target.value)}
              >
                <option value="">Ana kategori secin</option>
                {mainCategoryOptions.map((option) => (
                  <option key={`main-category-rename-${option}`} value={option}>
                    {option}
                  </option>
                ))}
              </select>
              <input
                className="mt-2 h-10 w-full rounded-xl border border-black/10 bg-white px-3 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
                placeholder="Yeni ad"
                value={renameMainCategoryTarget}
                onChange={(event) => setRenameMainCategoryTarget(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key !== "Enter" || busy) {
                    return;
                  }
                  event.preventDefault();
                  void handleRenameMainCategory();
                }}
              />
              <div className="mt-3 flex justify-end">
                <button
                  className="rounded-xl bg-[#d97706] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[#b45309] disabled:cursor-not-allowed disabled:opacity-60"
                  type="button"
                  onClick={() => {
                    void handleRenameMainCategory();
                  }}
                  disabled={busy || mainCategoryOptions.length === 0}
                >
                  Kaydet
                </button>
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
              Eklemek istediginiz kategori adini ve opsiyonel ana kategoriyi yazin.
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
            <input
              className="mt-3 w-full rounded-xl border border-black/10 bg-white px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-4 focus:ring-(--ring)"
              placeholder="Ana kategori (opsiyonel)"
              value={mainCategoryName}
              list="main-category-suggestions"
              onChange={(event) => setMainCategoryName(event.target.value)}
            />
            <datalist id="main-category-suggestions">
              {mainCategoryOptions.map((option) => (
                <option key={`main-category-option-${option}`} value={option} />
              ))}
            </datalist>
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
          <div className="relative z-[70] flex items-start gap-2">
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
                <svg viewBox="0 0 16 16" className="h-5 w-5 text-[#6b655c]" aria-hidden>
                  <path
                    d="M9.9 1.6 10.3 3a5.3 5.3 0 0 1 1.2.7l1.3-.5 1 1.8-1.1.9c.1.4.1.8.1 1.2s0 .8-.1 1.2l1.1.9-1 1.8-1.3-.5a5.3 5.3 0 0 1-1.2.7l-.4 1.4H7.7l-.4-1.4a5.3 5.3 0 0 1-1.2-.7l-1.3.5-1-1.8 1.1-.9A5 5 0 0 1 4.8 8c0-.4 0-.8.1-1.2L3.8 5.9l1-1.8 1.3.5c.4-.3.8-.5 1.2-.7l.4-1.4h2.2ZM8.8 10.1a2.1 2.1 0 1 0 0-4.2 2.1 2.1 0 0 0 0 4.2Z"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
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
              <a
                href="/needs"
                className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
                onClick={() => navigateToPage("/needs")}
                title="Ihtiyac Listesi"
              >
                <svg viewBox="0 0 16 16" className="h-5 w-5 text-[#6b655c]" aria-hidden>
                  <path
                    d="M4 3.2h8m-8 3h8m-8 3h5M2.8 1.8h10.4c.6 0 1 .4 1 1v10.4c0 .6-.4 1-1 1H2.8a1 1 0 0 1-1-1V2.8c0-.6.4-1 1-1Z"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </a>
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
                        {formatProductNameWithQuantity(item)}
                      </p>
                    ))
                  )}
                </div>
                <button
                  type="button"
                  className="mt-2 rounded-lg border border-black/10 bg-[#f9f4ee] px-2 py-1 text-[11px] text-[#6b655c]"
                  onClick={() => {
                    navigateToPage("/needs");
                  }}
                >
                  Tum Listeyi Gor
                </button>
              </div>
            </div>
            <div className="group relative">
              <a
                href="/basket"
                className="relative flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50"
                onClick={() => navigateToPage("/basket")}
                title="Sepet Onerileri"
              >
                <svg viewBox="0 0 16 16" className="h-5 w-5 text-[#6b655c]" aria-hidden>
                  <path
                    d="M2.2 3h1.7l1 6h6.5l1.2-4.3H4.1M6.3 12.7a.9.9 0 1 1 0-1.8.9.9 0 0 1 0 1.8Zm4.6 0a.9.9 0 1 1 0-1.8.9.9 0 0 1 0 1.8Z"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                {basketSuggestionCount > 0 && (
                  <span className="absolute -bottom-1 -right-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[#d97706] px-1 text-[10px] font-semibold text-white">
                    {basketSuggestionCount}
                  </span>
                )}
              </a>
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
                                {formatProductNameWithQuantity(item)}
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
                                {formatProductNameWithQuantity(item)}
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
                    navigateToPage("/basket");
                  }}
                >
                  Tum Sepeti Gor
                </button>
              </div>
            </div>
          </div>,
          headerActionsEl
        )}

      <div className="pointer-events-none fixed left-3 right-3 top-24 z-40 flex w-auto flex-col gap-2 sm:left-auto sm:right-6 sm:w-[320px]">
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
