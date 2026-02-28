"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { requestForMarketplace } from "@/lib/api";

const USER_SETTINGS_STORAGE_KEY = "smart-pantry:user-settings";
const DEFAULT_LATITUDE = 41.0082;
const DEFAULT_LONGITUDE = 28.9784;
const LEAFLET_CSS_ID = "leaflet-css-cdn";
const LEAFLET_SCRIPT_ID = "leaflet-js-cdn";
const LEAFLET_CSS_URL = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
const LEAFLET_SCRIPT_URL = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
const RESOLVE_MODAL_AUTO_CLOSE_MS = 3000;

type LatLng = {
  lat: number;
  lng: number;
};

type LeafletMap = {
  setView: (coords: [number, number], zoom: number) => LeafletMap;
  on: (event: string, callback: (event: { latlng: LatLng }) => void) => void;
  remove: () => void;
};

type LeafletMarker = {
  addTo: (map: LeafletMap) => LeafletMarker;
  setLatLng: (coords: [number, number]) => void;
  getLatLng: () => LatLng;
  on: (event: string, callback: () => void) => void;
};

type LeafletApi = {
  map: (element: HTMLElement, options?: Record<string, unknown>) => LeafletMap;
  tileLayer: (
    template: string,
    options?: Record<string, unknown>,
  ) => { addTo: (map: LeafletMap) => void };
  marker: (
    coords: [number, number],
    options?: Record<string, unknown>,
  ) => LeafletMarker;
};

type NominatimResult = {
  place_id: number;
  display_name: string;
  lat: string;
  lon: string;
};

type NominatimReverseResult = {
  display_name?: string;
};

type AddressPrediction = {
  id: string;
  label: string;
  lat: number;
  lng: number;
};

type StoredSettings = {
  customerLocation?: {
    city?: string;
    district?: string;
    latitude?: number | null;
    longitude?: number | null;
  };
  [key: string]: unknown;
};

type ResolveModalState = {
  latitude: number;
  longitude: number;
  migrosStoreId: number | null;
  yemeksepetiRedirectionUrl: string | null;
};

type StoreResolveErrorMessages = {
  migros: string | null;
  yemeksepeti: string | null;
};

const getBrowserWindow = (): (Window & { L?: LeafletApi }) | null => {
  return globalThis.window === undefined
    ? null
    : (globalThis.window as Window & { L?: LeafletApi });
};

const getPromiseErrorMessage = (reason: unknown, fallbackMessage: string): string => {
  return reason instanceof Error ? reason.message : fallbackMessage;
};

const getStoreResolveErrors = (
  migrosResult: PromiseSettledResult<{ storeId: number }>,
  yemeksepetiResult: PromiseSettledResult<{ redirectionUrl: string }>,
): StoreResolveErrorMessages => {
  const migros =
    migrosResult.status === "rejected"
      ? getPromiseErrorMessage(migrosResult.reason, "Migros cozumlenemedi.")
      : null;
  const yemeksepeti =
    yemeksepetiResult.status === "rejected"
      ? getPromiseErrorMessage(yemeksepetiResult.reason, "Yemeksepeti cozumlenemedi.")
      : null;
  return { migros, yemeksepeti };
};

const readStoredSettings = (): StoredSettings => {
  const browserWindow = getBrowserWindow();
  if (!browserWindow) {
    return {};
  }
  try {
    const raw = browserWindow.localStorage.getItem(USER_SETTINGS_STORAGE_KEY);
    if (!raw) {
      return {};
    }
    return JSON.parse(raw) as StoredSettings;
  } catch {
    return {};
  }
};

const parseCoordinate = (value: string): number | null => {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const numeric = Number(trimmed.replace(",", "."));
  return Number.isFinite(numeric) ? numeric : null;
};

const ensureLeafletStylesheet = () => {
  if (globalThis.document === undefined) {
    return;
  }
  if (document.getElementById(LEAFLET_CSS_ID)) {
    return;
  }
  const link = document.createElement("link");
  link.id = LEAFLET_CSS_ID;
  link.rel = "stylesheet";
  link.href = LEAFLET_CSS_URL;
  document.head.appendChild(link);
};

const loadLeaflet = (): Promise<LeafletApi> => {
  const browserWindow = getBrowserWindow();
  if (!browserWindow) {
    return Promise.reject(new Error("Tarayici ortami bulunamadi."));
  }

  ensureLeafletStylesheet();

  if (browserWindow.L) {
    return Promise.resolve(browserWindow.L);
  }

  return new Promise((resolve, reject) => {
    const existing = document.getElementById(LEAFLET_SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener("load", () => {
        if (browserWindow.L) {
          resolve(browserWindow.L);
          return;
        }
        reject(new Error("Leaflet yuklenemedi."));
      });
      existing.addEventListener("error", () => {
        reject(new Error("Harita kutuphanesi yuklenemedi."));
      });
      return;
    }

    const script = document.createElement("script");
    script.id = LEAFLET_SCRIPT_ID;
    script.src = LEAFLET_SCRIPT_URL;
    script.async = true;
    script.onload = () => {
      if (browserWindow.L) {
        resolve(browserWindow.L);
        return;
      }
      reject(new Error("Leaflet yuklenemedi."));
    };
    script.onerror = () => {
      reject(new Error("Harita kutuphanesi yuklenemedi."));
    };
    document.body.appendChild(script);
  });
};

export default function HeaderLocationInputs() {
  const initialLocation = useMemo(() => {
    const settings = readStoredSettings();
    const location = settings.customerLocation ?? {};
    return {
      latitude:
        typeof location.latitude === "number" && Number.isFinite(location.latitude)
          ? String(location.latitude)
          : "",
      longitude:
        typeof location.longitude === "number" && Number.isFinite(location.longitude)
          ? String(location.longitude)
          : "",
    };
  }, []);

  const [latitude, setLatitude] = useState(initialLocation.latitude);
  const [longitude, setLongitude] = useState(initialLocation.longitude);
  const [isResolving, setIsResolving] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);
  const [resolveResult, setResolveResult] = useState<ResolveModalState | null>(null);
  const [isMapPickerOpen, setIsMapPickerOpen] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const [addressQuery, setAddressQuery] = useState("");
  const [selectedAddressLabel, setSelectedAddressLabel] = useState("");
  const [predictions, setPredictions] = useState<AddressPrediction[]>([]);
  const [isSearchingPredictions, setIsSearchingPredictions] = useState(false);
  const initialLatitudeRef = useRef(parseCoordinate(initialLocation.latitude) ?? DEFAULT_LATITUDE);
  const initialLongitudeRef = useRef(parseCoordinate(initialLocation.longitude) ?? DEFAULT_LONGITUDE);
  const latitudeRef = useRef(latitude);
  const longitudeRef = useRef(longitude);
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<LeafletMap | null>(null);
  const markerRef = useRef<LeafletMarker | null>(null);
  const reverseLookupRequestRef = useRef(0);

  const fillAddressFromCoordinates = async (lat: number, lng: number) => {
    const requestId = reverseLookupRequestRef.current + 1;
    reverseLookupRequestRef.current = requestId;
    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${encodeURIComponent(String(lat))}&lon=${encodeURIComponent(String(lng))}`;
      const response = await fetch(url, {
        headers: {
          "Accept-Language": "tr",
        },
      });
      if (!response.ok) {
        return;
      }
      const payload = (await response.json()) as NominatimReverseResult;
      if (reverseLookupRequestRef.current !== requestId) {
        return;
      }
      const label = payload.display_name?.trim();
      if (!label) {
        return;
      }
      setSelectedAddressLabel(label);
      setAddressQuery(label);
    } catch {
      // Reverse geocode failed; keep selected coordinates without label update.
    }
  };

  useEffect(() => {
    latitudeRef.current = latitude;
    longitudeRef.current = longitude;
  }, [latitude, longitude]);

  useEffect(() => {
    if (!resolveResult && !resolveError) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => {
      setResolveResult(null);
      setResolveError(null);
    }, RESOLVE_MODAL_AUTO_CLOSE_MS);

    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [resolveResult, resolveError]);

  useEffect(() => {
    if (!isMapPickerOpen) {
      return;
    }

    let disposed = false;

    const mountMap = async () => {
      if (!mapContainerRef.current) {
        return;
      }

      try {
        const L = await loadLeaflet();
        if (disposed || !mapContainerRef.current || mapRef.current) {
          return;
        }

        setMapError(null);
        const initialLat = parseCoordinate(latitudeRef.current) ?? initialLatitudeRef.current;
        const initialLng = parseCoordinate(longitudeRef.current) ?? initialLongitudeRef.current;
        const map = L.map(mapContainerRef.current, { zoomControl: true }).setView(
          [initialLat, initialLng],
          12,
        );

        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "&copy; OpenStreetMap katkida bulunanlari",
          maxZoom: 19,
        }).addTo(map);

        const marker = L.marker([initialLat, initialLng], { draggable: true }).addTo(map);

        marker.on("dragend", () => {
          const { lat, lng } = marker.getLatLng();
          setLatitude(lat.toFixed(6));
          setLongitude(lng.toFixed(6));
          void fillAddressFromCoordinates(lat, lng);
        });

        map.on("click", (event) => {
          const { lat, lng } = event.latlng;
          marker.setLatLng([lat, lng]);
          setLatitude(lat.toFixed(6));
          setLongitude(lng.toFixed(6));
          void fillAddressFromCoordinates(lat, lng);
        });

        mapRef.current = map;
        markerRef.current = marker;
      } catch (error) {
        const message = error instanceof Error ? error.message : "Harita baslatilamadi.";
        setMapError(message);
      }
    };

    void mountMap();

    return () => {
      disposed = true;
      markerRef.current = null;
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, [isMapPickerOpen]);

  useEffect(() => {
    if (!isMapPickerOpen) {
      setPredictions([]);
      setIsSearchingPredictions(false);
      return;
    }

    const query = addressQuery.trim();
    if (query.length < 2) {
      setPredictions([]);
      setIsSearchingPredictions(false);
      return;
    }

    const controller = new AbortController();
    setIsSearchingPredictions(true);
    const timeoutId = globalThis.setTimeout(async () => {
      try {
        const url = `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&countrycodes=tr&addressdetails=1&q=${encodeURIComponent(query)}`;
        const response = await fetch(url, {
          signal: controller.signal,
          headers: {
            "Accept-Language": "tr",
          },
        });
        if (!response.ok) {
          setPredictions([]);
          return;
        }

        const items = (await response.json()) as NominatimResult[];
        const nextPredictions = items
          .map((item) => {
            const lat = Number(item.lat);
            const lng = Number(item.lon);
            if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
              return null;
            }
            return {
              id: String(item.place_id),
              label: item.display_name,
              lat,
              lng,
            } satisfies AddressPrediction;
          })
          .filter((item): item is AddressPrediction => item !== null);

        setPredictions(nextPredictions);
      } catch (error) {
        if (error instanceof Error && error.name === "AbortError") {
          return;
        }
        setPredictions([]);
      } finally {
        setIsSearchingPredictions(false);
      }
    }, 250);

    return () => {
      controller.abort();
      globalThis.clearTimeout(timeoutId);
    };
  }, [addressQuery, isMapPickerOpen]);

  const onSelectPrediction = (prediction: AddressPrediction) => {
    const map = mapRef.current;
    const marker = markerRef.current;
    if (!map || !marker) {
      return;
    }

    marker.setLatLng([prediction.lat, prediction.lng]);
    map.setView([prediction.lat, prediction.lng], 14);
    setLatitude(prediction.lat.toFixed(6));
    setLongitude(prediction.lng.toFixed(6));
    setSelectedAddressLabel(prediction.label);
    setAddressQuery(prediction.label);
    setPredictions([]);
    setMapError(null);
  };

  const persistLocation = (nextLatitude: string, nextLongitude: string) => {
    const browserWindow = getBrowserWindow();
    if (!browserWindow) {
      return;
    }
    const currentSettings = readStoredSettings();
    const nextSettings: StoredSettings = {
      ...currentSettings,
      customerLocation: {
        city: "",
        district: "",
        latitude: parseCoordinate(nextLatitude),
        longitude: parseCoordinate(nextLongitude),
      },
    };
    browserWindow.localStorage.setItem(USER_SETTINGS_STORAGE_KEY, JSON.stringify(nextSettings));
  };

  const validateCoordinates = (nextLatitude: number, nextLongitude: number): string | null => {
    if (nextLatitude < -90 || nextLatitude > 90) {
      return "X degeri -90 ile 90 arasinda olmali.";
    }
    if (nextLongitude < -180 || nextLongitude > 180) {
      return "Y degeri -180 ile 180 arasinda olmali.";
    }
    return null;
  };

  const updateResolveState = (
    nextLatitude: number,
    nextLongitude: number,
    migrosResult: PromiseSettledResult<{ storeId: number }>,
    yemeksepetiResult: PromiseSettledResult<{ redirectionUrl: string }>,
  ) => {
    const migrosStoreId =
      migrosResult.status === "fulfilled" ? migrosResult.value.storeId : null;
    const yemeksepetiRedirectionUrl =
      yemeksepetiResult.status === "fulfilled"
        ? yemeksepetiResult.value.redirectionUrl
        : null;
    const errors = getStoreResolveErrors(migrosResult, yemeksepetiResult);

    if (migrosStoreId === null && yemeksepetiRedirectionUrl === null) {
      setResolveError(
        `${errors.migros ?? "Migros cozumlenemedi."} | ${errors.yemeksepeti ?? "Yemeksepeti cozumlenemedi."}`,
      );
      return;
    }

    if (migrosStoreId === null) {
      setResolveError(errors.migros ?? "Migros cozumlenemedi.");
    } else if (yemeksepetiRedirectionUrl === null) {
      setResolveError(errors.yemeksepeti ?? "Yemeksepeti cozumlenemedi.");
    } else {
      setResolveError(null);
    }

    setResolveResult({
      latitude: nextLatitude,
      longitude: nextLongitude,
      migrosStoreId,
      yemeksepetiRedirectionUrl,
    });
  };

  const onResolve = async () => {
    const parsedLatitude = parseCoordinate(latitude);
    const parsedLongitude = parseCoordinate(longitude);

    if (parsedLatitude === null || parsedLongitude === null) {
      setResolveError("X ve Y koordinatlari gecersiz.");
      return;
    }

    const validationError = validateCoordinates(parsedLatitude, parsedLongitude);
    if (validationError) {
      setResolveError(validationError);
      return;
    }

    persistLocation(latitude, longitude);
    setResolveError(null);
    setIsResolving(true);
    try {
      const [migrosResult, yemeksepetiResult] = await Promise.allSettled([
        requestForMarketplace<{ storeId: number }>("MG", "/settings/migros/store-id-by-location", {
          method: "POST",
          body: JSON.stringify({
            latitude: parsedLatitude,
            longitude: parsedLongitude,
          }),
        }),
        requestForMarketplace<{ redirectionUrl: string }>("YS", "/settings/yemeksepeti/vendor-by-location", {
          method: "POST",
          body: JSON.stringify({
            latitude: parsedLatitude,
            longitude: parsedLongitude,
          }),
        }),
      ]);
      updateResolveState(parsedLatitude, parsedLongitude, migrosResult, yemeksepetiResult);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Konum cozumleme basarisiz.";
      setResolveError(message);
    } finally {
      setIsResolving(false);
    }
  };

  return (
    <>
      <div className="flex w-full min-w-0 flex-1 flex-wrap items-center justify-start gap-2 sm:w-auto sm:justify-end">
        <div className="relative w-full sm:w-auto">
          <div className="inline-flex w-full max-w-full items-center justify-between gap-2 rounded-xl border border-black/10 bg-white px-2.5 py-2 shadow-[0_16px_40px_-30px_rgba(0,0,0,0.7)] sm:w-auto sm:max-w-105">
            <div className="flex min-w-0 items-center gap-2">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-amber-100 text-[#9a5c00]">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  aria-hidden="true"
                  className="h-3.5 w-3.5"
                >
                  <path
                    d="M12 21s7-5.586 7-11a7 7 0 1 0-14 0c0 5.414 7 11 7 11Z"
                    stroke="currentColor"
                    strokeWidth="1.7"
                  />
                  <circle cx="12" cy="10" r="2.4" stroke="currentColor" strokeWidth="1.7" />
                </svg>
              </span>
              <div className="min-w-0">
                <p className="text-[9px] font-semibold uppercase tracking-[0.16em] text-[#9a5c00]">
                  Adres
                </p>
                {selectedAddressLabel && (
                  <p className="truncate text-[10px] text-[#6b655c]">{selectedAddressLabel}</p>
                )}
              </div>
            </div>
            <button
              type="button"
              onClick={() => setIsMapPickerOpen((prev) => !prev)}
              className="h-7 rounded-md border border-black/10 bg-[#f9f4ee] px-2.5 text-[9px] font-semibold uppercase tracking-[0.08em] text-[#6b655c] transition hover:bg-amber-50"
            >
              {isMapPickerOpen ? "Kapat" : "Harita"}
            </button>
          </div>

          {isMapPickerOpen && (
            <div className="absolute left-0 right-0 z-20 mt-2 w-auto max-w-[92vw] overflow-hidden rounded-xl border border-black/10 bg-white shadow-[0_28px_80px_-45px_rgba(0,0,0,0.65)] sm:left-auto sm:right-0 sm:w-175 sm:max-w-[88vw]">
              <div ref={mapContainerRef} className="h-48 w-full" />
              <div className="border-t border-black/10 bg-white p-2">
                <input
                  type="text"
                  value={addressQuery}
                  onChange={(event) => {
                    setAddressQuery(event.target.value);
                    setMapError(null);
                  }}
                  placeholder="Adres ara (or. kadikoy)"
                  className="h-8 w-full rounded-md border border-black/10 bg-[#fcfaf7] px-2 text-xs text-[#111] outline-none ring-[#d97706] placeholder:text-[#9ca3af] focus:ring-2"
                />
                {(predictions.length > 0 || isSearchingPredictions) && (
                  <div className="mt-1 rounded-md border border-black/10 bg-white">
                    {isSearchingPredictions && (
                      <p className="px-2 py-1.5 text-[11px] text-[#6b655c]">Araniyor...</p>
                    )}
                    {predictions.map((prediction) => (
                      <button
                        key={prediction.id}
                        type="button"
                        onClick={() => onSelectPrediction(prediction)}
                        className="block w-full border-t border-black/5 px-2 py-1.5 text-left text-[11px] text-[#374151] first:border-t-0 hover:bg-amber-50"
                      >
                        {prediction.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <div className="flex items-center justify-end border-t border-black/10 bg-[#fcfaf7] px-3 py-2">
                <button
                  type="button"
                  onClick={() => {
                    setIsMapPickerOpen(false);
                    void onResolve();
                  }}
                  disabled={isResolving}
                  className="h-7 rounded-md border border-black/10 bg-white px-2 text-[9px] font-semibold uppercase tracking-[0.08em] text-[#6b655c] transition hover:bg-amber-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isResolving ? "Sorgulaniyor..." : "Tamam"}
                </button>
              </div>
              {mapError && (
                <p className="border-t border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
                  {mapError}
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      {(resolveResult || resolveError) && (
        <div className="fixed inset-0 z-70 flex items-center justify-center bg-black/40 px-4">
          <div className="w-full max-w-lg rounded-3xl border border-black/10 bg-white p-6 shadow-[0_30px_80px_-40px_rgba(0,0,0,0.55)]">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-[#111]">Konum Sonucu</h3>
              <button
                type="button"
                onClick={() => {
                  setResolveResult(null);
                  setResolveError(null);
                }}
                className="text-sm text-[#6b655c] transition hover:text-[#111]"
              >
                Kapat
              </button>
            </div>

            {resolveError && (
              <p className="mt-3 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {resolveError}
              </p>
            )}

            {resolveResult && (
              <div className="mt-3 space-y-2 rounded-2xl border border-black/10 bg-[#f9f4ee] p-3 text-sm text-[#1f2937]">
                <p>
                  <span className="font-semibold">X:</span> {resolveResult.latitude}
                </p>
                <p>
                  <span className="font-semibold">Y:</span> {resolveResult.longitude}
                </p>
                <p>
                  <span className="font-semibold">Migros storeId:</span>{" "}
                  {resolveResult.migrosStoreId ?? "-"}
                </p>
                <p className="break-all">
                  <span className="font-semibold">Yemeksepeti Link:</span>{" "}
                  {resolveResult.yemeksepetiRedirectionUrl ?? "-"}
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  );
}
