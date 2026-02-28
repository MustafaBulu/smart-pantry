export type AvailabilityStatus = "Uygun" | "Normal" | "Pahali";
export type NeedUrgency = "VERY_URGENT" | "URGENT" | "NOT_URGENT";

type DisplayPriceSource = "regular" | "money" | "effective";

type ThresholdPriceInput = {
  marketplaceCode: string | null;
  price: number | null;
  moneyPrice: number | null;
  basketDiscountPrice: number | null;
  campaignBuyQuantity: number | null;
  campaignPayQuantity: number | null;
  effectivePrice: number | null;
};

export const formatPriceSuffix = (price: number | null) => {
  if (price === null) {
    return "";
  }
  return ` - ${price.toFixed(2)} TL`;
};

export const formatPrice = (price: number | null) => {
  if (price === null) {
    return "";
  }
  return `${price.toFixed(2)} TL`;
};

export const formatTl = (value: number | null, fractionDigits = 2) => {
  if (value === null) {
    return "-";
  }
  return `${value.toLocaleString("tr-TR", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })} TL`;
};

export const hasMoneyDiscount = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null
) => {
  if (marketplaceCode !== "MG" || moneyPrice === null) {
    return false;
  }
  if (price === null) {
    return true;
  }
  return moneyPrice < price;
};

export const hasBasketDiscount = (
  marketplaceCode: string,
  basketDiscountThreshold: number | null,
  basketDiscountPrice: number | null
) =>
  marketplaceCode === "MG" &&
  basketDiscountThreshold !== null &&
  basketDiscountPrice !== null;

export const resolveEffectivePriceValue = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  preferMoneyBasePrice: boolean
) => {
  if (effectivePrice !== null) {
    return effectivePrice;
  }
  const basePrice =
    preferMoneyBasePrice && moneyPrice !== null ? moneyPrice : (price ?? moneyPrice);
  if (
    basePrice === null ||
    campaignBuyQuantity === null ||
    campaignPayQuantity === null ||
    campaignBuyQuantity <= campaignPayQuantity ||
    campaignBuyQuantity <= 0
  ) {
    return null;
  }
  return Number(((basePrice * campaignPayQuantity) / campaignBuyQuantity).toFixed(2));
};

export const hasEffectiveCampaign = (
  marketplaceCode: string | null,
  price: number | null,
  moneyPrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  effectivePrice: number | null
) =>
  marketplaceCode === "MG" &&
  campaignBuyQuantity !== null &&
  campaignPayQuantity !== null &&
  campaignBuyQuantity > campaignPayQuantity &&
  resolveEffectivePriceValue(
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    true
  ) !== null;

export const formatEffectiveCampaignBadge = (
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null
) => {
  if (
    campaignBuyQuantity === null ||
    campaignPayQuantity === null ||
    campaignBuyQuantity <= campaignPayQuantity
  ) {
    return "Money Hediye";
  }
  if (campaignBuyQuantity === 4 && campaignPayQuantity === 3) {
    return "2.si %50 Indirimli";
  }
  const refundQuantity = campaignBuyQuantity - campaignPayQuantity;
  return `${campaignBuyQuantity} Ode ${refundQuantity}'i Money Hediye`;
};

export const resolveDisplayPrice = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  if (marketplaceCode !== "MG") {
    return { price, source: "regular" as DisplayPriceSource };
  }
  const resolvedEffectivePrice = resolveEffectivePriceValue(
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember
  );
  if (considerEffectivePricing && resolvedEffectivePrice !== null) {
    return { price: resolvedEffectivePrice, source: "effective" as DisplayPriceSource };
  }
  const options: { value: number; source: DisplayPriceSource }[] = [];
  if (price !== null) {
    options.push({ value: price, source: "regular" });
  }
  if (migrosMoneyMember && moneyPrice !== null) {
    options.push({ value: moneyPrice, source: "money" });
  }
  if (options.length === 0) {
    return { price: null, source: "regular" as DisplayPriceSource };
  }
  const selected = options.reduce((best, current) =>
    current.value < best.value ? current : best
  );
  return { price: selected.value, source: selected.source };
};

export const formatMarketplacePriceSuffix = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  marketplaceCode: string,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  const resolved = resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  );
  const suffix = formatPriceSuffix(resolved.price);
  if (!suffix) {
    return "";
  }
  if (marketplaceCode === "MG" && resolved.source === "money") {
    return `${suffix} (Money)`;
  }
  if (marketplaceCode === "MG" && resolved.source === "effective") {
    return `${suffix} (Efektif)`;
  }
  return suffix;
};

export const formatPriceOrDash = (price: number | null) => {
  if (price === null) {
    return "-";
  }
  return `${price.toFixed(2)} TL`;
};

export const formatPriceOrLabel = (price: number | null) => {
  if (price === null) {
    return "Fiyat yok";
  }
  return `${price.toFixed(2)} TL`;
};

export const formatMarketplacePriceLabel = (
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  marketplaceCode: string,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) => {
  const resolved = resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  );
  const label = formatPriceOrLabel(resolved.price);
  if (label === "Fiyat yok") {
    return label;
  }
  if (marketplaceCode === "MG" && resolved.source === "money") {
    return `${label} (Money)`;
  }
  if (marketplaceCode === "MG" && resolved.source === "effective") {
    return `${label} (Efektif)`;
  }
  return label;
};

export const isMoneyDisplayPrice = (
  marketplaceCode: string,
  price: number | null,
  moneyPrice: number | null,
  effectivePrice: number | null,
  campaignBuyQuantity: number | null,
  campaignPayQuantity: number | null,
  migrosMoneyMember: boolean,
  considerEffectivePricing: boolean
) =>
  resolveDisplayPrice(
    marketplaceCode,
    price,
    moneyPrice,
    effectivePrice,
    campaignBuyQuantity,
    campaignPayQuantity,
    migrosMoneyMember,
    considerEffectivePricing
  ).source === "money";

export const resolveThresholdPrice = (
  item: ThresholdPriceInput,
  considerEffectivePricing: boolean
) => {
  if (item.marketplaceCode !== "MG") {
    return item.price;
  }
  const resolvedEffectivePrice = resolveEffectivePriceValue(
    item.price,
    item.moneyPrice,
    item.effectivePrice,
    item.campaignBuyQuantity,
    item.campaignPayQuantity,
    true
  );
  if (considerEffectivePricing && resolvedEffectivePrice !== null) {
    return resolvedEffectivePrice;
  }
  const options = [item.price, item.moneyPrice, item.basketDiscountPrice].filter(
    (value): value is number => value !== null
  );
  if (options.length === 0) {
    return null;
  }
  return Math.min(...options);
};

export const formatCategoryTitle = (name: string) => name.toLocaleUpperCase("tr-TR");

export const marketplaceLabel = (code: string) => {
  if (code === "YS") {
    return "Yemeksepeti";
  }
  if (code === "MG") {
    return "Migros";
  }
  return code;
};

export const formatMonthTick = (timestamp: number) => {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = String(date.getFullYear()).slice(-2);
  return `${month}/${year}`;
};

export const buildNicePriceAxis = (maxValue: number, targetTickCount = 5) => {
  const safeMax = Number.isFinite(maxValue) && maxValue > 0 ? maxValue : 1;
  const roughStep = safeMax / targetTickCount;
  const exponent = Math.floor(Math.log10(roughStep));
  const magnitude = 10 ** exponent;
  const fraction = roughStep / magnitude;
  let niceFraction = 1;
  if (fraction > 1 && fraction <= 2) {
    niceFraction = 2;
  } else if (fraction > 2 && fraction <= 5) {
    niceFraction = 5;
  } else if (fraction > 5) {
    niceFraction = 10;
  }
  const step = niceFraction * magnitude;
  const maxTick = Math.ceil(safeMax / step) * step;
  const ticks: number[] = [];
  for (let value = 0; value <= maxTick + step / 2; value += step) {
    ticks.push(Number(value.toFixed(4)));
  }
  return { step, maxTick, ticks };
};

export const formatAxisTickLabel = (value: number, step: number) => {
  if (Number.isInteger(value)) {
    return value.toFixed(0);
  }
  if (step >= 1) {
    return value.toFixed(1);
  }
  return value.toFixed(2);
};

export const resolveAvailabilityStatus = (score: number | null): AvailabilityStatus => {
  if (score === null || Number.isNaN(score)) {
    return "Normal";
  }
  if (score >= 70) {
    return "Uygun";
  }
  if (score >= 50) {
    return "Normal";
  }
  return "Pahali";
};

export const urgencyLabel = (urgency: NeedUrgency) => {
  if (urgency === "VERY_URGENT") {
    return "Acil";
  }
  if (urgency === "URGENT") {
    return "Normal";
  }
  return "Acil Degil";
};
