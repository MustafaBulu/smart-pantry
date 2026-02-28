"use client";

import { useState } from "react";
import { requestForMarketplace } from "@/lib/api";

type TriggerResponse = {
  marketplaceCode: string;
  runDate: string;
  executed: boolean;
  message: string;
};

export default function DailyPriceRefreshButton() {
  const [isBusy, setIsBusy] = useState(false);
  const [resultText, setResultText] = useState<string | null>(null);

  const onRefresh = async () => {
    setIsBusy(true);
    setResultText(null);
    try {
      const [mg, ys] = await Promise.all([
        requestForMarketplace<TriggerResponse>("MG", "/settings/daily-details/trigger", {
          method: "POST",
        }),
        requestForMarketplace<TriggerResponse>("YS", "/settings/daily-details/trigger", {
          method: "POST",
        }),
      ]);

      const message = [mg, ys]
        .map((item) => `${item.marketplaceCode}: ${item.message}`)
        .join(" | ");
      setResultText(message);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Guncelleme basarisiz.";
      setResultText(message);
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        onClick={() => void onRefresh()}
        disabled={isBusy}
        title="Gunluk fiyat kayitlarini tetikle"
        className="flex h-11 w-11 items-center justify-center rounded-2xl border border-black/10 bg-white text-xl text-[#6b655c] shadow-[0_12px_30px_-20px_rgba(0,0,0,0.45)] transition hover:bg-amber-50 disabled:cursor-not-allowed disabled:opacity-60"
      >
        <svg viewBox="0 0 24 24" fill="none" className={`h-5 w-5 ${isBusy ? "animate-spin" : ""}`}>
          <path
            d="M20 12a8 8 0 1 1-2.34-5.66M20 4v6h-6"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      {resultText && (
        <p className="max-w-[320px] truncate text-[11px] text-[#6b655c]" title={resultText}>
          {resultText}
        </p>
      )}
    </div>
  );
}
