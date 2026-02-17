"use client";

import { useEffect, useState } from "react";

type NeedItem = {
  key: string;
  type: "PRODUCT" | "CATEGORY";
  categoryName: string;
  name: string;
  urgency: "VERY_URGENT" | "URGENT" | "NOT_URGENT";
  availabilityStatus: "Uygun" | "Normal" | "Pahali";
  opportunityLevel: string | null;
};

const NEED_LIST_STORAGE_KEY = "smart-pantry:need-list";

const urgencyLabel = (urgency: NeedItem["urgency"]) => {
  if (urgency === "VERY_URGENT") {
    return "Cok Acil";
  }
  if (urgency === "URGENT") {
    return "Acil";
  }
  return "Acil Degil";
};

export default function NeedsPage() {
  const [items, setItems] = useState<NeedItem[]>([]);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(NEED_LIST_STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as NeedItem[]) : [];
      setItems(Array.isArray(parsed) ? parsed : []);
    } catch {
      setItems([]);
    }
  }, []);

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-8">
      <div className="rounded-3xl border border-black/10 bg-white p-6 shadow-[0_20px_50px_-35px_rgba(0,0,0,0.4)]">
        <h1 className="display text-3xl">Ihtiyac Listesi</h1>
        <p className="mt-1 text-sm text-[#6b655c]">Urun ve kategori bazli ihtiyac kayitlari.</p>
        <div className="mt-5 space-y-2">
          {items.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-black/10 bg-[#f9f4ee] px-4 py-4 text-sm text-[#6b655c]">
              Liste bos.
            </div>
          ) : (
            items.map((item) => (
              <div
                key={item.key}
                className="rounded-2xl border border-black/10 bg-[#f9f4ee] px-4 py-3"
              >
                <p className="text-base font-semibold text-[#111]">{item.name}</p>
                <p className="text-xs text-[#6b655c]">
                  {item.type === "CATEGORY" ? "Kategori Bazli" : "Urun Bazli"} |{" "}
                  {item.categoryName} | {urgencyLabel(item.urgency)} | {item.availabilityStatus} |{" "}
                  {item.opportunityLevel ?? "Normal"}
                </p>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
