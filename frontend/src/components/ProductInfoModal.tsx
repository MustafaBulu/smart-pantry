"use client";

import type { ReactNode } from "react";
import ProductInsightsPanel from "@/components/ProductInsightsPanel";
import Image from "next/image";

type NeedUrgency = "VERY_URGENT" | "URGENT" | "NOT_URGENT";

type ProductInfoModalProduct = {
  name: string;
  imageUrl: string;
  brandName: string;
  marketplaceCode: string;
};

type ProductInfoModalProps = Readonly<{
  isOpen: boolean;
  product: ProductInfoModalProduct | null;
  onCloseAction: () => void;
  priceLabel: string;
  showMoneyBadge: boolean;
  moneyBadgeText: string | null;
  showBasketDiscount: boolean;
  basketDiscountThresholdText: string | null;
  basketDiscountPriceText: string | null;
  showEffectiveCampaign: boolean;
  effectiveCampaignText: string | null;
  selectedOpportunityLevel: string;
  todayRecommendationLabel: string;
  todayRecommendationReason: string;
  selectedNeedUrgency: NeedUrgency;
  urgencyLabelAction: (urgency: NeedUrgency) => string;
  onSelectNeedUrgencyAction: (urgency: NeedUrgency) => void;
  onAddSelectedToNeedListAction: () => void;
  selectedInNeedList: boolean;
  showNeedActions?: boolean;
  showOnlySelectedMarketplace?: boolean;
  showAddCategoryButton: boolean;
  onAddActiveCategoryToNeedListAction: () => void;
  historyCountLabel: string;
  historyContent: ReactNode;
}>;

export default function ProductInfoModal({
  isOpen,
  product,
  onCloseAction,
  priceLabel,
  showMoneyBadge,
  moneyBadgeText,
  showBasketDiscount,
  basketDiscountThresholdText,
  basketDiscountPriceText,
  showEffectiveCampaign,
  effectiveCampaignText,
  selectedOpportunityLevel,
  todayRecommendationLabel,
  todayRecommendationReason,
  selectedNeedUrgency,
  urgencyLabelAction,
  onSelectNeedUrgencyAction,
  onAddSelectedToNeedListAction,
  selectedInNeedList,
  showNeedActions = true,
  showOnlySelectedMarketplace = false,
  showAddCategoryButton,
  onAddActiveCategoryToNeedListAction,
  historyCountLabel,
  historyContent,
}: ProductInfoModalProps) {
  if (!isOpen || !product) {
    return null;
  }

  const selectedNeedActionLabel = selectedInNeedList
    ? "Ihtiyac Listesinde Guncelle"
    : "Ihtiyac Listesine Ekle";

  const selectedMarketplaceBadge =
    product.marketplaceCode === "YS" ? (
      <span className="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-rose-700">
        <Image src="/yemeksepeti-logo.png" alt="Yemeksepeti" width={16} height={16} className="h-4 w-4 rounded-sm object-contain" />
        <span>Yemeksepeti</span>
      </span>
    ) : (
      <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-amber-700">
        <Image src="/migros-logo.png" alt="Migros" width={16} height={16} className="h-4 w-4 rounded-sm object-contain" />
        <span>Migros</span>
      </span>
    );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <button
        type="button"
        aria-label="Kapat"
        className="absolute inset-0 h-full w-full bg-transparent"
        onClick={onCloseAction}
      />
      <dialog
        open
        aria-modal="true"
        className="relative z-10 m-0 max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-3xl border border-black/10 bg-white p-5 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.55)]"
      >
        <div className="flex items-center justify-between">
          <h3 className="display text-xl">Urun Bilgisi</h3>
          <button
            className="text-sm text-[#6b655c] transition hover:text-[#111]"
            type="button"
            onClick={onCloseAction}
          >
            Kapat
          </button>
        </div>
        <div className="mt-4 rounded-2xl border border-black/5 bg-[#f9f4ee] p-4">
          <div className="flex items-center gap-3">
            <div className="relative h-14 w-14 overflow-hidden rounded-2xl border border-black/10 bg-white">
              {product.imageUrl ? (
                <Image src={product.imageUrl} alt={product.name} fill sizes="56px" className="object-cover" />
              ) : (
                <div className="flex h-full w-full items-center justify-center text-xs text-[#6b655c]">
                  Gorsel yok
                </div>
              )}
            </div>
            <div>
              <p className="text-base font-semibold text-[#111]">{product.name || "Isimsiz urun"}</p>
              <p className="text-xs text-[#6b655c]">{product.brandName || "Marka yok"}</p>
              <p className="text-xs text-[#6b655c]">{priceLabel}</p>
              {showMoneyBadge && moneyBadgeText && (
                <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">
                  <Image src="/migros-money.png" alt="Money" width={12} height={12} className="h-3 w-3 object-contain" />
                  <span>Money ile</span>
                  <span>{moneyBadgeText}</span>
                </div>
              )}
              {showBasketDiscount && basketDiscountThresholdText && basketDiscountPriceText && (
                <div className="mt-1 inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-0.5 text-[10px] font-semibold text-sky-700">
                  <span>Sepette {basketDiscountThresholdText}</span>
                  <span>{basketDiscountPriceText}</span>
                </div>
              )}
              {showEffectiveCampaign && effectiveCampaignText && (
                <p className="mt-1 text-[11px] font-semibold text-emerald-700">
                  Kampanya: {effectiveCampaignText}
                </p>
              )}
            </div>
          </div>
          <div className="mt-3 grid gap-2 text-xs text-[#6b655c]">
            <div className="flex items-center gap-2 rounded-xl border border-black/5 bg-white px-3 py-2">
              <span>Marketler:</span>
              {showOnlySelectedMarketplace ? (
                selectedMarketplaceBadge
              ) : (
                <>
                  <span
                    className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 ${
                      product.marketplaceCode === "YS"
                        ? "border-rose-200 bg-rose-50 text-rose-700"
                        : "border-black/10 bg-white text-[#6b655c]"
                    }`}
                  >
                    <Image src="/yemeksepeti-logo.png" alt="Yemeksepeti" width={16} height={16} className="h-4 w-4 rounded-sm object-contain" />
                    <span>Yemeksepeti</span>
                  </span>
                  <span
                    className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 ${
                      product.marketplaceCode === "MG"
                        ? "border-amber-200 bg-amber-50 text-amber-700"
                        : "border-black/10 bg-white text-[#6b655c]"
                    }`}
                  >
                    <Image src="/migros-logo.png" alt="Migros" width={16} height={16} className="h-4 w-4 rounded-sm object-contain" />
                    <span>Migros</span>
                  </span>
                </>
              )}
            </div>
            <div className="flex items-center justify-between rounded-xl border border-black/5 bg-white px-3 py-2">
              <span>Firsat Durumu:</span>
              <span className="font-semibold text-[#111]">{selectedOpportunityLevel}</span>
            </div>
            <ProductInsightsPanel
              todayRecommendationLabel={todayRecommendationLabel}
              todayRecommendationReason={todayRecommendationReason}
              historyCountLabel={historyCountLabel}
              historyContent={historyContent}
            />
            {showNeedActions && (
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
                        onClick={() => onSelectNeedUrgencyAction(urgency)}
                      >
                        {urgencyLabelAction(urgency)}
                      </button>
                    )
                  )}
                </div>
                <button
                  type="button"
                  className="mt-2 rounded-xl border border-black/10 bg-white px-3 py-1.5 text-xs font-semibold text-[#9a5c00] transition hover:bg-amber-50"
                  onClick={onAddSelectedToNeedListAction}
                >
                  {selectedNeedActionLabel}
                </button>
                {showAddCategoryButton && (
                  <button
                    type="button"
                    className="ml-2 mt-2 rounded-xl border border-black/10 bg-[#f9f4ee] px-3 py-1.5 text-xs font-semibold text-[#6b655c] transition hover:bg-amber-50"
                    onClick={onAddActiveCategoryToNeedListAction}
                  >
                    Kategoriyi Ihtiyac Listesine Ekle
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </dialog>
    </div>
  );
}
