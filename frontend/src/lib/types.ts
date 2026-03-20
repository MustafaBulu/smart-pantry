export type Category = { id: number; name: string; mainCategory?: string | null };

export type ProductResponse = { id: number; name: string; price: number | null };

export type ProductDetailResponse = {
  id: number;
  name: string;
  brand: string | null;
  unit: string | null;
  unitValue: number | null;
  categoryId: number | null;
  categoryName: string | null;
  createdAt: string | null;
};

export type PriceHistoryResponse = {
  id: number;
  productId: number;
  productName: string;
  marketplaceCode: string;
  price: number;
  availabilityScore: number | null;
  opportunityLevel: string | null;
  recordedAt: string;
};

export type CategoryPriceSummaryResponse = {
  productId: number;
  productName: string;
  minPrice: number;
  maxPrice: number;
  avgPrice: number;
  lastPrice: number;
  lastRecordedAt: string | null;
};

export type BulkAddResultItem = {
  productId: string;
  status: number;
  message: string;
};

export type BulkAddResponse = {
  requested: number;
  created: number;
  updated: number;
  failed: number;
  results: BulkAddResultItem[];
};

export type MarketplaceProductCandidateResponse = {
  marketplaceCode: string;
  externalId: string;
  name: string;
  brandName: string;
  imageUrl: string;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  unit: string | null;
  unitValue: number | null;
  packCount: number | null;
};

export type MarketplaceProductEntryResponse = {
  marketplaceCode: string;
  externalId: string;
  name: string;
  productId: number | null;
  brandName: string;
  imageUrl: string;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  unit?: string | null;
  unitValue?: number | null;
  packCount?: number | null;
};

export type MarketplaceProductAddedResponse = {
  categoryId: number;
  marketplaceCode: string;
  externalId: string;
  name: string;
  productId: number | null;
  brandName: string;
  imageUrl: string;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  unit?: string | null;
  unitValue?: number | null;
  packCount?: number | null;
};

export type MarketplaceProductMatchScoreResponse = {
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

export type MarketplaceProductMatchPairResponse = {
  ys: MarketplaceProductCandidateResponse;
  mg: MarketplaceProductCandidateResponse;
  score: MarketplaceProductMatchScoreResponse;
  autoLinkEligible: boolean;
  manualMatch: boolean;
};

export type MarketplaceProductMatchRequest = {
  categoryId?: number;
  ys: MarketplaceProductCandidateResponse[];
  mg: MarketplaceProductCandidateResponse[];
  minScore?: number;
};

export type MarketplaceManualMatchRequest = {
  ysExternalId: string;
  mgExternalId: string;
};

export type BasketMinimumSettingsResponse = {
  ysMinimumBasketAmount: number;
  mgMinimumBasketAmount: number;
};

export type NeedListItemDto = {
  key: string;
  type: "PRODUCT" | "CATEGORY";
  categoryId: number;
  categoryName: string;
  externalId: string | null;
  marketplaceCode: "YS" | "MG" | null;
  name: string;
  imageUrl: string;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountThreshold: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
  urgency: "VERY_URGENT" | "URGENT" | "NOT_URGENT";
  availabilityScore: number | null;
  historyDayCount: number | null;
  availabilityStatus: "Uygun" | "Normal" | "Pahali";
  opportunityLevel: string | null;
};
