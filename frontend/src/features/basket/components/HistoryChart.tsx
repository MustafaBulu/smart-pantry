"use client";

const formatMonthTick = (timestamp: number) => {
  const date = new Date(timestamp);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const year = String(date.getFullYear()).slice(-2);
  return `${month}/${year}`;
};

const buildNicePriceAxis = (maxValue: number, targetTickCount = 5) => {
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

const formatAxisTickLabel = (value: number, step: number) => {
  if (Number.isInteger(value)) {
    return value.toFixed(0);
  }
  if (step >= 1) {
    return value.toFixed(1);
  }
  return value.toFixed(2);
};

type ChartPoint = {
  recordedAt: string;
  marketplaceCode: string;
  price: number;
  availabilityScore: number | null;
};

export type HoveredHistoryPoint = {
  x: number;
  y: number;
  point: ChartPoint & { ts: number };
};

type HistoryChartProps = Readonly<{
  rangeFilteredHistory: ChartPoint[];
  hoveredHistoryPoint: HoveredHistoryPoint | null;
  onHoverChange: (point: HoveredHistoryPoint | null) => void;
  resolveAvailabilityStatus: (score: number | null) => string;
}>;

export default function HistoryChart({
  rangeFilteredHistory,
  hoveredHistoryPoint,
  onHoverChange,
  resolveAvailabilityStatus,
}: HistoryChartProps) {
  const axisLeft = 50;
  const axisRight = 390;
  const axisTop = 20;
  const axisBottom = 220;
  const xPadding = 18;
  const plotLeft = axisLeft + xPadding;
  const plotRight = axisRight - xPadding;
  const plotWidth = Math.max(plotRight - plotLeft, 1);
  const ySpan = axisBottom - axisTop;
  const points = [...rangeFilteredHistory]
    .map((point, index) => {
      const parsed = Date.parse(point.recordedAt);
      return { ...point, ts: Number.isFinite(parsed) ? parsed : index };
    })
    .sort((left, right) => left.ts - right.ts);
  const prices = points.map((point) => point.price);
  const max = Math.max(...prices, 0);
  const axis = buildNicePriceAxis(max);
  const range = Math.max(axis.maxTick, 1);
  const minTs = Math.min(...points.map((point) => point.ts));
  const maxTs = Math.max(...points.map((point) => point.ts));
  const timeRange = maxTs - minTs || 1;
  const toX = (ts: number) => ((ts - minTs) / timeRange) * plotWidth + plotLeft;
  const ysCoords = points
    .filter((point) => point.marketplaceCode === "YS")
    .map((point) => ({
      x: toX(point.ts),
      y: axisBottom - (point.price / range) * ySpan,
      point,
    }));
  const mgCoords = points
    .filter((point) => point.marketplaceCode === "MG")
    .map((point) => ({
      x: toX(point.ts),
      y: axisBottom - (point.price / range) * ySpan,
      point,
    }));
  const allCoords = [...ysCoords, ...mgCoords];
  const monthlyBuckets = points.reduce<Record<string, number[]>>((acc, point) => {
    const monthKey = point.recordedAt.slice(0, 7);
    if (!acc[monthKey]) {
      acc[monthKey] = [];
    }
    acc[monthKey].push(point.price);
    return acc;
  }, {});
  const monthAverages = Object.values(monthlyBuckets).map((values) => {
    const total = values.reduce((sum, value) => sum + value, 0);
    return total / values.length;
  });
  const monthlyAverage =
    monthAverages.reduce((sum, value) => sum + value, 0) / Math.max(monthAverages.length, 1);
  const averageY = axisBottom - (monthlyAverage / range) * ySpan;
  const ysLine = ysCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
  const mgLine = mgCoords.map((coord) => `${coord.x},${coord.y}`).join(" ");
  const tickValues = axis.ticks;
  const monthTickMap = new Map<string, number>();
  points.forEach((point) => {
    const date = new Date(point.ts);
    const monthKey = `${date.getFullYear()}-${date.getMonth()}`;
    if (!monthTickMap.has(monthKey)) {
      monthTickMap.set(monthKey, point.ts);
    }
  });
  const monthTicks = Array.from(monthTickMap.values()).map((ts) => ({
    ts,
    x: toX(ts),
    label: formatMonthTick(ts),
  }));
  const pointsByDate = points.reduce<
    Record<string, { ys?: (typeof points)[number]; mg?: (typeof points)[number] }>
  >((acc, point) => {
    if (!acc[point.recordedAt]) {
      acc[point.recordedAt] = {};
    }
    if (point.marketplaceCode === "YS") {
      acc[point.recordedAt].ys = point;
    }
    if (point.marketplaceCode === "MG") {
      acc[point.recordedAt].mg = point;
    }
    return acc;
  }, {});
  const hoveredDate = hoveredHistoryPoint?.point.recordedAt ?? null;
  const hoveredPair = hoveredDate ? pointsByDate[hoveredDate] : undefined;
  const availabilityStatus = resolveAvailabilityStatus(hoveredHistoryPoint?.point.availabilityScore ?? null);
  const availabilityValue =
    hoveredHistoryPoint?.point.availabilityScore !== null &&
    hoveredHistoryPoint?.point.availabilityScore !== undefined
      ? hoveredHistoryPoint.point.availabilityScore.toFixed(1)
      : "-";
  const hoverPointColor = hoveredHistoryPoint?.point.marketplaceCode === "YS" ? "#e11d48" : "#d97706";
  const hoveredYsPrice = hoveredPair?.ys?.price;
  const hoveredMgPrice = hoveredPair?.mg?.price;
  const hoverPanelWidth = axisRight - axisLeft;

  return (
    <svg viewBox="0 0 420 300" className="h-full w-full">
      <line x1={axisLeft} y1={axisTop} x2={axisLeft} y2={axisBottom} stroke="#94a3b8" strokeWidth="1" />
      <line x1={axisLeft} y1={axisBottom} x2={axisRight} y2={axisBottom} stroke="#94a3b8" strokeWidth="1" />
      {tickValues.map((value) => {
        const y = axisBottom - (value / range) * ySpan;
        return (
          <g key={`tick-${value}`}>
            <line x1={axisLeft} y1={y} x2={axisRight} y2={y} stroke="#cbd5e1" strokeWidth="1" opacity="0.45" />
            <text x={axisLeft - 6} y={y + 3} textAnchor="end" fontSize="9" fill="#6b7280">
              {formatAxisTickLabel(value, axis.step)}
            </text>
          </g>
        );
      })}
      {monthTicks.map((tick) => (
        <g key={`month-${tick.ts}`}>
          <line x1={tick.x} y1={axisBottom} x2={tick.x} y2={axisBottom + 4} stroke="#94a3b8" strokeWidth="1" />
          <text x={tick.x} y={axisBottom + 16} textAnchor="middle" fontSize="9" fill="#6b7280">
            {tick.label}
          </text>
        </g>
      ))}
      <line x1={axisLeft} y1={averageY} x2={axisRight} y2={averageY} stroke="#94a3b8" strokeDasharray="6 5" strokeWidth="2" />
      {ysLine && <polyline points={ysLine} fill="none" stroke="#f43f5e" strokeWidth="2" />}
      {mgLine && <polyline points={mgLine} fill="none" stroke="#d97706" strokeWidth="2" />}
      <rect
        x={plotLeft}
        y={axisTop}
        width={plotWidth}
        height={ySpan}
        fill="transparent"
        onMouseMove={(event) => {
          if (allCoords.length === 0) {
            return;
          }
          const rect = event.currentTarget.getBoundingClientRect();
          const scaleX = plotWidth / rect.width;
          const mouseX = (event.clientX - rect.left) * scaleX + plotLeft;
          const nearest = allCoords.reduce((best, current) => {
            if (!best) {
              return current;
            }
            const bestDistance = Math.abs(best.x - mouseX);
            const currentDistance = Math.abs(current.x - mouseX);
            return currentDistance < bestDistance ? current : best;
          }, allCoords[0]);
          onHoverChange(nearest);
        }}
        onMouseLeave={() => onHoverChange(null)}
      />
      {ysCoords.map((coord) => (
        <circle
          key={`ys-hit-${coord.point.ts}-${coord.x}`}
          cx={coord.x}
          cy={coord.y}
          r="9"
          fill="transparent"
          onMouseEnter={() => onHoverChange(coord)}
          onMouseLeave={() => onHoverChange(null)}
        />
      ))}
      {mgCoords.map((coord) => (
        <circle
          key={`mg-hit-${coord.point.ts}-${coord.x}`}
          cx={coord.x}
          cy={coord.y}
          r="9"
          fill="transparent"
          onMouseEnter={() => onHoverChange(coord)}
          onMouseLeave={() => onHoverChange(null)}
        />
      ))}
      {hoveredHistoryPoint && (
        <circle
          cx={hoveredHistoryPoint.x}
          cy={hoveredHistoryPoint.y}
          r="4.5"
          fill={hoverPointColor}
          stroke="#ffffff"
          strokeWidth="1.2"
        />
      )}
      {hoveredHistoryPoint && (
        <line
          x1={hoveredHistoryPoint.x}
          y1={axisTop}
          x2={hoveredHistoryPoint.x}
          y2={axisBottom}
          stroke="#334155"
          strokeWidth="1"
          strokeDasharray="4 4"
          opacity="0.7"
        />
      )}
      {hoveredHistoryPoint && hoveredPair && (
        <g transform={`translate(${axisLeft}, ${axisBottom + 26})`}>
          <rect
            width={hoverPanelWidth}
            height="32"
            rx="8"
            fill="#111827"
            fillOpacity="0.92"
            stroke="#374151"
            strokeWidth="1"
          />
          <text x="8" y="14" fontSize="9.2" fill="#cbd5e1">
            {hoveredDate ?? "-"}
          </text>
          <image
            href="/yemeksepeti-logo.png"
            x="78"
            y="6"
            width="10"
            height="10"
            preserveAspectRatio="xMidYMid meet"
          />
          <text x="92" y="14" fontSize="9.2" fill="#f9fafb">
            {hoveredYsPrice !== undefined ? `${hoveredYsPrice.toFixed(2)} TL` : "-"}
          </text>
          <image
            href="/migros-logo.png"
            x="150"
            y="6"
            width="10"
            height="10"
            preserveAspectRatio="xMidYMid meet"
          />
          <text x="164" y="14" fontSize="9.2" fill="#f9fafb">
            {hoveredMgPrice !== undefined ? `${hoveredMgPrice.toFixed(2)} TL` : "-"}
          </text>
          <text x="8" y="28" fontSize="9.2" fill="#cbd5e1">
            Alinabilirlik: {availabilityValue} ({availabilityStatus})
          </text>
        </g>
      )}
    </svg>
  );
}
