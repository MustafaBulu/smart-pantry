"use client";

import { useEffect, useMemo, useState } from "react";

type NeedItem = {
  key: string;
  categoryId: number;
  name: string;
  marketplaceCode: "YS" | "MG" | null;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  urgency: "VERY_URGENT" | "URGENT" | "NOT_URGENT";
  availabilityStatus: "Uygun" | "Normal" | "Pahali";
  opportunityLevel: string | null;
};

type OpportunityItem = {
  key: string;
  categoryId: number;
  name: string;
  availabilityStatus: "Uygun" | "Normal" | "Pahali";
  opportunityLevel: string;
};

const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";
const OPPORTUNITY_FEED_STORAGE_KEY = "smart-pantry:opportunity-feed";
const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const DEFAULT_MIGROS_BASKET_THRESHOLD = 50;

const resolveThresholdPrice = (item: NeedItem, considerEffectivePricing: boolean) => {
  if (item.marketplaceCode !== "MG") {
    return item.price;
  }
  if (
    considerEffectivePricing &&
    item.effectivePrice !== null
  ) {
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

const urgencyLabel = (urgency: NeedItem["urgency"]) => {
  if (urgency === "VERY_URGENT") {
    return "Cok Acil";
  }
  if (urgency === "URGENT") {
    return "Acil";
  }
  return "Acil Degil";
};

export default function BasketPage() {
  const [needList, setNeedList] = useState<NeedItem[]>([]);
  const [opportunities, setOpportunities] = useState<OpportunityItem[]>([]);
  const [minimumBasketAmount, setMinimumBasketAmount] = useState<number | null>(null);
  const [considerEffectivePricing, setConsiderEffectivePricing] = useState(false);

  useEffect(() => {
    try {
      const needsRaw = window.localStorage.getItem(NEED_LIST_STORAGE_KEY);
      const oppRaw = window.localStorage.getItem(OPPORTUNITY_FEED_STORAGE_KEY);
      const settingsRaw = window.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
      const needs = needsRaw ? (JSON.parse(needsRaw) as NeedItem[]) : [];
      const opp = oppRaw ? (JSON.parse(oppRaw) as OpportunityItem[]) : [];
      const settings = settingsRaw
        ? (JSON.parse(settingsRaw) as { minimumBasketAmount?: number; considerEffectivePricing?: boolean })
        : {};
      setNeedList(Array.isArray(needs) ? needs : []);
      setOpportunities(Array.isArray(opp) ? opp : []);
      setMinimumBasketAmount(
        typeof settings.minimumBasketAmount === "number" ? settings.minimumBasketAmount : null
      );
      setConsiderEffectivePricing(Boolean(settings.considerEffectivePricing));
    } catch {
      setNeedList([]);
      setOpportunities([]);
      setMinimumBasketAmount(null);
      setConsiderEffectivePricing(false);
    }
  }, []);

  const suggestedFromNeeds = useMemo(() => {
    const defaultThreshold = minimumBasketAmount ?? DEFAULT_MIGROS_BASKET_THRESHOLD;
    const migrosBasketTotal = needList
      .filter((item) => item.marketplaceCode === "MG")
      .reduce((sum, item) => {
        const price = resolveThresholdPrice(item, considerEffectivePricing);
        return sum + (price ?? 0);
      }, 0);

    return needList.filter((item) => {
      const isHighOpportunity = item.opportunityLevel === "Yuksek";
      const campaignThreshold = item.basketDiscountThreshold ?? defaultThreshold;
      const campaignEligible =
        item.marketplaceCode === "MG" &&
        item.basketDiscountPrice !== null &&
        migrosBasketTotal >= campaignThreshold;
      const effectiveCampaignEligible = considerEffectivePricing && hasEffectiveCampaign(item);
      if (item.urgency === "VERY_URGENT") {
        return item.availabilityStatus !== "Pahali" || campaignEligible || effectiveCampaignEligible;
      }
      if (item.urgency === "URGENT") {
        return (
          item.availabilityStatus === "Uygun" ||
          (item.availabilityStatus === "Normal" && isHighOpportunity) ||
          campaignEligible ||
          effectiveCampaignEligible
        );
      }
      return item.availabilityStatus === "Uygun" || campaignEligible || effectiveCampaignEligible;
    });
  }, [needList, minimumBasketAmount, considerEffectivePricing]);

  const autoSuggestions = useMemo(() => {
    const needCategories = new Set(needList.map((item) => item.categoryId));
    return opportunities.filter((item) => !needCategories.has(item.categoryId));
  }, [needList, opportunities]);

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-8">
      <div className="rounded-3xl border border-black/10 bg-white p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <h1 className="display text-3xl">Sepet Onerileri</h1>
        <p className="mt-1 text-sm text-[#6b655c]">
          Ihtiyac ve genel firsat sinyallerinden uretilen alisveris onceligi.
        </p>

        <div className="mt-5 rounded-2xl border border-black/10 bg-[#f9f4ee] p-4">
          <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">Ihtiyactan Onerilenler</p>
          <div className="mt-2 space-y-1">
            {suggestedFromNeeds.length === 0 ? (
              <p className="text-sm text-[#6b655c]">Ihtiyac listesine gore su an alinacak urun yok.</p>
            ) : (
              suggestedFromNeeds.map((item) => (
                <p key={`need-${item.key}`} className="text-sm text-[#14532d]">
                  {item.name} - {urgencyLabel(item.urgency)}
                </p>
              ))
            )}
          </div>
        </div>

        <div className="mt-4 rounded-2xl border border-black/10 bg-[#f9f4ee] p-4">
          <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">Genel Firsatlar</p>
          <div className="mt-2 space-y-1">
            {autoSuggestions.length === 0 ? (
              <p className="text-sm text-[#6b655c]">Ihtiyac disi guclu firsat bulunmuyor.</p>
            ) : (
              autoSuggestions.map((item) => (
                <p key={`auto-${item.key}`} className="text-sm text-[#14532d]">
                  {item.name} - Iyi indirim firsati
                </p>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
