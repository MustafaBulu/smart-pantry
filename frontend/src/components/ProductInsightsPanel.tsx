"use client";

import type { ReactNode } from "react";

type ProductInsightsPanelProps = Readonly<{
  todayRecommendationLabel: string;
  todayRecommendationReason: string;
  historyCountLabel: string;
  historyContent: ReactNode;
}>;

export default function ProductInsightsPanel({
  todayRecommendationLabel,
  todayRecommendationReason,
  historyCountLabel,
  historyContent,
}: ProductInsightsPanelProps) {
  return (
    <>
      <div className="rounded-xl border border-black/5 bg-white px-3 py-2">
        <p className="text-[11px] uppercase tracking-[0.18em] text-[#9a5c00]">
          Bugun Alim Durumu
        </p>
        <p
          className={`mt-1 text-sm font-semibold ${
            todayRecommendationLabel === "Bugun Alinabilir"
              ? "text-emerald-700"
              : "text-rose-700"
          }`}
        >
          {todayRecommendationLabel}
        </p>
        <p className="mt-1 text-[11px] text-[#6b655c]">{todayRecommendationReason}</p>
      </div>
      <div className="mt-4 rounded-2xl border border-black/5 bg-white p-3">
        <div className="flex items-center justify-between">
          <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">Fiyat Gecmisi</p>
          <span className="text-xs text-[#6b655c]">{historyCountLabel}</span>
        </div>
        {historyContent}
      </div>
    </>
  );
}
