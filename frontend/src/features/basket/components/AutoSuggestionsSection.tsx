"use client";

import Image from "next/image";

type AutoSuggestionItem = {
  key: string;
  name: string;
  imageUrl: string;
  marketplaceCode: "YS" | "MG" | null;
  availabilityScore: number | null;
  displayPrice: number | null;
};

type AutoSuggestionsSectionProps<TItem extends AutoSuggestionItem> = Readonly<{
  items: TItem[];
  onOpen: (item: TItem) => void;
  marketplaceLabel: (marketplaceCode: "YS" | "MG" | null) => string;
  formatAvailabilityScore: (score: number | null) => string;
  formatPrice: (price: number | null) => string;
}>;

export default function AutoSuggestionsSection<TItem extends AutoSuggestionItem>({
  items,
  onOpen,
  marketplaceLabel,
  formatAvailabilityScore,
  formatPrice,
}: AutoSuggestionsSectionProps<TItem>) {
  return (
    <div className="mt-4 rounded-2xl border border-black/10 bg-[#f9f4ee] p-4">
      <p className="text-xs uppercase tracking-[0.2em] text-[#9a5c00]">Genel Firsatlar</p>
      <div className="mt-2 space-y-2">
        {items.length === 0 ? (
          <div className="rounded-xl border border-dashed border-black/20 bg-white px-4 py-5">
            <p className="text-sm font-medium text-[#14532d]">Firsat urunleri burada listelenecek</p>
            <p className="mt-1 text-sm text-[#6b655c]">Su anda gosterilecek urun yok.</p>
          </div>
        ) : (
          items.map((item) => (
            <button
              type="button"
              key={`auto-${item.key}`}
              className="flex w-full items-center gap-2 rounded-lg border border-black/10 bg-white px-2 py-1.5 text-left transition hover:bg-amber-50"
              onClick={() => onOpen(item)}
            >
              <div className="relative h-9 w-9 overflow-hidden rounded-md border border-black/10 bg-[#f3f4f6]">
                {item.imageUrl ? (
                  <Image src={item.imageUrl} alt={item.name} fill sizes="36px" className="object-cover" unoptimized />
                ) : null}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-[#14532d]">{item.name}</p>
                <p className="text-[11px] text-[#6b655c]">{marketplaceLabel(item.marketplaceCode)}</p>
              </div>
              <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                Skor: {formatAvailabilityScore(item.availabilityScore)}
              </span>
              <p className="text-sm font-semibold text-[#111]">{formatPrice(item.displayPrice)}</p>
            </button>
          ))
        )}
      </div>
    </div>
  );
}
