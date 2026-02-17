export type Category = { id: number; name: string };

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
  sku: string;
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
};

export type MarketplaceProductEntryResponse = {
  marketplaceCode: string;
  externalId: string;
  sku: string;
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
};
