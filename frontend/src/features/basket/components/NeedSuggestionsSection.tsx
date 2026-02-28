"use client";

import type { ReactNode } from "react";

type NeedSuggestionsSectionProps<TItem, TNotice> = Readonly<{
  cards: Array<{
    code: "MG" | "YS";
    title: string;
    items: TItem[];
    crossNotices: TNotice[];
    total: number;
    min: number;
    shortfall: number;
    showMinimumSettings: boolean;
  }>;
  settingsLoaded: boolean;
  mgMinimumLabel: string;
  mgShortfallLabel: string;
  renderNeedSuggestionRow: (item: TItem, rowKey: string) => ReactNode;
  renderCrossNoticeRow: (notice: TNotice, rowKey: string) => ReactNode;
  getNeedItemKeyPart: (item: TItem) => string;
  getCrossNoticeKeyPart: (notice: TNotice) => string;
}>;

export default function NeedSuggestionsSection<TItem, TNotice>({
  cards,
  settingsLoaded,
  mgMinimumLabel,
  mgShortfallLabel,
  renderNeedSuggestionRow,
  renderCrossNoticeRow,
  getNeedItemKeyPart,
  getCrossNoticeKeyPart,
}: NeedSuggestionsSectionProps<TItem, TNotice>) {
  return (
    <div className="mt-5 rounded-2xl border border-black/10 bg-[#f9f4ee] p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">Ihtiyactan Onerilenler</p>
      <div className="mt-2 grid gap-3 md:grid-cols-2">
        {cards.map((card) => (
          <div key={`card-${card.code}`} className="rounded-xl border border-black/10 bg-white p-3">
            <p className="text-[11px] uppercase tracking-[0.14em] text-[#9a5c00]">{card.title}</p>
            <div className="mt-2 space-y-2">
              {card.items.length === 0
                ? <p className="text-sm text-[#6b655c]">Oneri yok.</p>
                : card.items.map((item) =>
                  renderNeedSuggestionRow(item, `need-${card.code}-${getNeedItemKeyPart(item)}`),
                )}
            </div>
            {(card.items.length > 0 || card.crossNotices.length > 0) && (
              <div className="mt-3 border-t border-dashed border-black/15 pt-2 text-xs text-[#6b655c]">
                <p className="font-semibold text-[#111]">Sepet Tutari: {card.total.toFixed(2)} TL</p>
                {card.min > 0 && card.shortfall > 0 && (
                  <p>Min sepet icin eklenmesi gereken: {card.shortfall.toFixed(2)} TL</p>
                )}
                {card.crossNotices.length > 0 && (
                  <div className="mt-2 space-y-2">
                    <p className="text-[11px] text-[#6b655c]">Kucuk farkla buradan da alabilirsiniz:</p>
                    {card.crossNotices.slice(0, 3).map((notice) =>
                      renderCrossNoticeRow(
                        notice,
                        `cross-${card.code}-${getCrossNoticeKeyPart(notice)}`,
                      ),
                    )}
                  </div>
                )}
              </div>
            )}
            {card.showMinimumSettings && settingsLoaded && (
              <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-2 text-xs text-[#6b655c]">
                <p className="mb-1 text-[10px] uppercase tracking-[0.14em] text-amber-700">
                  Sepet Minimum Tutar Bilgisi
                </p>
                <p>Min sepet tutari: {mgMinimumLabel}</p>
                <p>Min sepet icin eklenmesi gereken: {mgShortfallLabel}</p>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
