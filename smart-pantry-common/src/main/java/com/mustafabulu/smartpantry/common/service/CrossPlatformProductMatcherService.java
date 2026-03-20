package com.mustafabulu.smartpantry.common.service;

import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductCandidateResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchPairResponse;
import com.mustafabulu.smartpantry.common.dto.response.MarketplaceProductMatchScoreResponse;
import com.mustafabulu.smartpantry.common.model.ImageSignatureCache;
import com.mustafabulu.smartpantry.common.repository.ImageSignatureCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tartarus.snowball.ext.TurkishStemmer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CrossPlatformProductMatcherService {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final String PACKAGE_TOKEN = "paket";
    private static final Set<String> NAME_STOP_WORDS = Set.of(
            "ve", "ile", "icin", "pet", PACKAGE_TOKEN, "adet", "sise", "kutu", "boy", "mini", "maxi"
    );
    private static final Set<String> NLP_NOISE_TOKENS = Set.of(
            "pet", "sise", "sisede", "siseli", "cam", "bardak", "kutu", PACKAGE_TOKEN, "paketi", "adet",
            "pratik", "prtc", "ekonomik", "firsat", "kampanya", "boy", "mini", "maxi"
    );
    private static final Map<String, String> NLP_LEMMA_MAP = Map.ofEntries(
            Map.entry("lt", "l"),
            Map.entry("litre", "l"),
            Map.entry("litrelik", "l"),
            Map.entry("gr", "g"),
            Map.entry("gram", "g"),
            Map.entry("kg", "kg"),
            Map.entry("sisede", "sise"),
            Map.entry("siseli", "sise"),
            Map.entry("sis", "sise"),
            Map.entry("sisesi", "sise"),
            Map.entry("pratiksise", "sise")
    );
    private static final int NLP_TOKEN_CACHE_MAX_SIZE = 20_000;
    private static final Map<String, String> NLP_TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final Duration IMAGE_CONNECT_TIMEOUT = Duration.ofMillis(700);
    private static final Duration IMAGE_REQUEST_TIMEOUT = Duration.ofMillis(1200);
    private static final double AMBIGUOUS_IMAGE_MIN_SCORE = 0.72d;
    private static final HttpClient IMAGE_HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(IMAGE_CONNECT_TIMEOUT)
            .build();
    private static final int PROCESS_IMAGE_CACHE_MAX_SIZE = 8_000;
    private static final Map<String, CachedImageSignature> PROCESS_IMAGE_CACHE = new ConcurrentHashMap<>();

    private final ImageSignatureCacheRepository imageSignatureCacheRepository;

    public CrossPlatformProductMatcherService() {
        this.imageSignatureCacheRepository = null;
    }

    @Autowired
    public CrossPlatformProductMatcherService(ImageSignatureCacheRepository imageSignatureCacheRepository) {
        this.imageSignatureCacheRepository = imageSignatureCacheRepository;
    }

    public List<MarketplaceProductMatchPairResponse> buildMarketplacePairs(
            List<MarketplaceProductCandidateResponse> ys,
            List<MarketplaceProductCandidateResponse> mg,
            double minScore
    ) {
        if (ys == null || mg == null || ys.isEmpty() || mg.isEmpty()) {
            return List.of();
        }
        Map<String, ImageSignature> imageSignatureCache = new HashMap<>();
        Map<String, Integer> ysGroupCounts = countBrandQuantityGroups(ys);
        Map<String, Integer> mgGroupCounts = countBrandQuantityGroups(mg);
        List<MarketplaceProductMatchPairResponse> candidates = collectCandidatePairs(
                ys,
                mg,
                minScore,
                imageSignatureCache,
                ysGroupCounts,
                mgGroupCounts
        );
        sortCandidates(candidates);
        List<MarketplaceProductMatchPairResponse> selected = selectDistinctPairs(candidates);
        return finalizeSelectedPairs(selected, candidates);
    }

    private List<MarketplaceProductMatchPairResponse> collectCandidatePairs(
            List<MarketplaceProductCandidateResponse> ys,
            List<MarketplaceProductCandidateResponse> mg,
            double minScore,
            Map<String, ImageSignature> imageSignatureCache,
            Map<String, Integer> ysGroupCounts,
            Map<String, Integer> mgGroupCounts
    ) {
        List<MarketplaceProductMatchPairResponse> candidates = new ArrayList<>();
        for (MarketplaceProductCandidateResponse ysItem : ys) {
            String ysGroupKey = brandQuantityGroupKey(ysItem);
            for (MarketplaceProductCandidateResponse mgItem : mg) {
                MarketplaceProductMatchPairResponse pair = toCandidatePair(
                        ysItem,
                        mgItem,
                        minScore,
                        ysGroupKey,
                        imageSignatureCache,
                        ysGroupCounts,
                        mgGroupCounts
                );
                if (pair != null) {
                    candidates.add(pair);
                }
            }
        }
        return candidates;
    }

    private MarketplaceProductMatchPairResponse toCandidatePair(
            MarketplaceProductCandidateResponse ysItem,
            MarketplaceProductCandidateResponse mgItem,
            double minScore,
            String ysGroupKey,
            Map<String, ImageSignature> imageSignatureCache,
            Map<String, Integer> ysGroupCounts,
            Map<String, Integer> mgGroupCounts
    ) {
        MarketplaceProductMatchScoreResponse score = scoreCandidatePair(ysItem, mgItem, imageSignatureCache);
        if (score == null || score.score() < minScore) {
            return null;
        }
        MarketplaceProductMatchScoreResponse resolvedScore = resolveAmbiguousScore(
                ysItem,
                mgItem,
                score,
                ysGroupKey,
                imageSignatureCache,
                ysGroupCounts,
                mgGroupCounts
        );
        if (resolvedScore == null) {
            return null;
        }
        return new MarketplaceProductMatchPairResponse(ysItem, mgItem, resolvedScore, false, false);
    }

    private MarketplaceProductMatchScoreResponse resolveAmbiguousScore(
            MarketplaceProductCandidateResponse ysItem,
            MarketplaceProductCandidateResponse mgItem,
            MarketplaceProductMatchScoreResponse score,
            String ysGroupKey,
            Map<String, ImageSignature> imageSignatureCache,
            Map<String, Integer> ysGroupCounts,
            Map<String, Integer> mgGroupCounts
    ) {
        String mgGroupKey = brandQuantityGroupKey(mgItem);
        boolean ambiguous = ysGroupCounts.getOrDefault(ysGroupKey, 0) > 1
                || mgGroupCounts.getOrDefault(mgGroupKey, 0) > 1;
        if (!ambiguous) {
            return score;
        }
        double imageScore = imageSimilarity(safe(ysItem.imageUrl()), safe(mgItem.imageUrl()), imageSignatureCache);
        boolean strongBrandMatch = brandSimilarity(inferBrand(ysItem), inferBrand(mgItem)) >= 0.92d;
        if (!strongBrandMatch && imageScore < AMBIGUOUS_IMAGE_MIN_SCORE) {
            return null;
        }
        return new MarketplaceProductMatchScoreResponse(
                score.score(),
                score.nameScore(),
                score.coreNameScore(),
                score.phraseScore(),
                score.quantityScore(),
                score.brandScore(),
                Math.max(score.imageScore(), imageScore),
                score.priceScore(),
                score.profileScore()
        );
    }

    private void sortCandidates(List<MarketplaceProductMatchPairResponse> candidates) {
        candidates.sort((left, right) -> {
            int byScore = Double.compare(right.score().score(), left.score().score());
            if (byScore != 0) {
                return byScore;
            }
            int byImage = Double.compare(right.score().imageScore(), left.score().imageScore());
            if (byImage != 0) {
                return byImage;
            }
            int byName = Double.compare(right.score().nameScore(), left.score().nameScore());
            if (byName != 0) {
                return byName;
            }
            int byPhrase = Double.compare(right.score().phraseScore(), left.score().phraseScore());
            if (byPhrase != 0) {
                return byPhrase;
            }
            String leftKey = normalizeExternalId(left.ys().externalId()) + "|" + normalizeExternalId(left.mg().externalId());
            String rightKey = normalizeExternalId(right.ys().externalId()) + "|" + normalizeExternalId(right.mg().externalId());
            return leftKey.compareTo(rightKey);
        });
    }

    private List<MarketplaceProductMatchPairResponse> selectDistinctPairs(
            List<MarketplaceProductMatchPairResponse> candidates
    ) {
        Set<String> usedYs = new LinkedHashSet<>();
        Set<String> usedMg = new LinkedHashSet<>();
        List<MarketplaceProductMatchPairResponse> selected = new ArrayList<>();
        for (MarketplaceProductMatchPairResponse pair : candidates) {
            String ysKey = normalizeExternalId(pair.ys().externalId());
            String mgKey = normalizeExternalId(pair.mg().externalId());
            if (usedYs.contains(ysKey) || usedMg.contains(mgKey)) {
                continue;
            }
            usedYs.add(ysKey);
            usedMg.add(mgKey);
            selected.add(pair);
        }
        return selected;
    }

    private List<MarketplaceProductMatchPairResponse> finalizeSelectedPairs(
            List<MarketplaceProductMatchPairResponse> selected,
            List<MarketplaceProductMatchPairResponse> allCandidates
    ) {
        List<MarketplaceProductMatchPairResponse> finalized = new ArrayList<>();
        for (MarketplaceProductMatchPairResponse pair : selected) {
            double margin = computePairMargin(pair, allCandidates);
            boolean autoLinkEligible = shouldAutoLinkCandidates(pair.score(), margin);
            finalized.add(new MarketplaceProductMatchPairResponse(
                    pair.ys(),
                    pair.mg(),
                    pair.score(),
                    autoLinkEligible,
                    false
            ));
        }
        return finalized;
    }

    private Map<String, Integer> countBrandQuantityGroups(List<MarketplaceProductCandidateResponse> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (MarketplaceProductCandidateResponse item : items) {
            String key = brandQuantityGroupKey(item);
            if (key.isBlank()) {
                continue;
            }
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private String brandQuantityGroupKey(MarketplaceProductCandidateResponse candidate) {
        String brand = inferBrand(candidate);
        QuantityInfo quantity = parseQuantityInfo(candidate);
        if (brand.isBlank() || quantity.amount() == null || quantity.unit() == null) {
            return "";
        }
        String canonicalUnit = switch (quantity.unit()) {
            case "g", "ml" -> "gml";
            default -> quantity.unit();
        };
        long roundedAmount = Math.round(quantity.amount());
        return brand + "|" + canonicalUnit + "|" + roundedAmount;
    }

    private boolean shouldAutoLinkCandidates(
            MarketplaceProductMatchScoreResponse match,
            double margin
    ) {
        return match.score() >= 0.86d
                && match.coreNameScore() >= 0.62d
                && match.quantityScore() >= 0.98d
                && match.brandScore() >= 0.35d
                && match.profileScore() >= 0.60d
                && margin >= 0.08d;
    }

    private MarketplaceProductMatchScoreResponse scoreCandidatePair(
            MarketplaceProductCandidateResponse source,
            MarketplaceProductCandidateResponse target,
            Map<String, ImageSignature> imageSignatureCache
    ) {
        // Gate first: quantity and brand must stay consistent.
        Compatibility quantity = compareQuantity(source, target);
        if (!quantity.compatible()) {
            return null;
        }

        String sourceBrand = inferBrand(source);
        String targetBrand = inferBrand(target);
        if (!brandMatches(sourceBrand, targetBrand)) {
            return null;
        }
        double brandScore = brandSimilarity(sourceBrand, targetBrand);

        String sourceCoreName = extractCoreName(source);
        String targetCoreName = extractCoreName(target);

        double nameScore = jaccardSimilarity(tokenSet(safe(source.name())), tokenSet(safe(target.name())));
        double coreNameScore = jaccardSimilarity(coreTokenSet(sourceCoreName), coreTokenSet(targetCoreName));
        double phraseScore = Math.max(
                leadingPhraseScore(sourceCoreName, targetCoreName),
                phraseSimilarity(sourceCoreName, targetCoreName)
        );
        if (coreNameScore < 0.45d && phraseScore < 0.40d) {
            return null;
        }
        Compatibility profile = compareProfiles(
                extractMatchProfile(source),
                extractMatchProfile(target)
        );
        if (!profile.compatible()) {
            return null;
        }

        Compatibility form = compareFormConsistency(source, target);
        Compatibility pack = comparePackCountConsistency(source, target);
        Compatibility price = comparePriceConsistency(source, target);
        if (!price.compatible()) {
            return null;
        }
        double imageScore = imageSimilarity(
                safe(source.imageUrl()),
                safe(target.imageUrl()),
                imageSignatureCache
        );

        double profileScore = (profile.score() * 0.60d) + (form.score() * 0.25d) + (pack.score() * 0.15d);
        double score = (coreNameScore * 0.32d)
                + (phraseScore * 0.12d)
                + (quantity.score() * 0.20d)
                + (brandScore * 0.08d)
                + (profile.score() * 0.12d)
                + (form.score() * 0.06d)
                + (pack.score() * 0.04d)
                + (price.score() * 0.03d)
                + (imageScore * 0.03d);
        score = Math.clamp(score, 0d, 1d);

        return new MarketplaceProductMatchScoreResponse(
                score,
                nameScore,
                coreNameScore,
                phraseScore,
                quantity.score(),
                brandScore,
                imageScore,
                price.score(),
                profileScore
        );
    }

    private double computePairMargin(
            MarketplaceProductMatchPairResponse selectedPair,
            List<MarketplaceProductMatchPairResponse> candidates
    ) {
        double selectedScore = selectedPair.score().score();
        String ysId = normalizeExternalId(selectedPair.ys().externalId());
        String mgId = normalizeExternalId(selectedPair.mg().externalId());
        double nextBest = 0d;
        for (MarketplaceProductMatchPairResponse candidate : candidates) {
            if (candidate != selectedPair) {
                String candidateYsId = normalizeExternalId(candidate.ys().externalId());
                String candidateMgId = normalizeExternalId(candidate.mg().externalId());
                if (candidateYsId.equals(ysId) || candidateMgId.equals(mgId)) {
                    nextBest = Math.max(nextBest, candidate.score().score());
                }
            }
        }
        return Math.max(0d, selectedScore - nextBest);
    }

    private String extractCoreName(MarketplaceProductCandidateResponse candidate) {
        if (candidate == null) {
            return "";
        }
        List<String> nameTokens = semanticTokens(safe(candidate.name()));
        if (nameTokens.isEmpty()) {
            return "";
        }
        Set<String> brandTokens = tokenSet(inferBrand(candidate));
        List<String> filtered = new ArrayList<>();
        for (String token : nameTokens) {
            if (!brandTokens.contains(token)
                    && !NAME_STOP_WORDS.contains(token)
                    && token.length() > 2) {
                filtered.add(token);
            }
        }
        if (filtered.isEmpty()) {
            return String.join(" ", nameTokens);
        }
        return String.join(" ", filtered);
    }

    private Compatibility compareQuantity(
            MarketplaceProductCandidateResponse leftCandidate,
            MarketplaceProductCandidateResponse rightCandidate
    ) {
        QuantityInfo leftFromFields = parseQuantityInfo(leftCandidate);
        QuantityInfo rightFromFields = parseQuantityInfo(rightCandidate);
        boolean hasFieldInfo = leftFromFields.hasData() || rightFromFields.hasData();
        if (hasFieldInfo) {
            QuantityInfo leftResolved = leftFromFields.hasData()
                    ? leftFromFields
                    : parseQuantityInfo(safe(leftCandidate.name()));
            QuantityInfo rightResolved = rightFromFields.hasData()
                    ? rightFromFields
                    : parseQuantityInfo(safe(rightCandidate.name()));
            return compareQuantityInfo(leftResolved, rightResolved);
        }
        return compareQuantityInfo(parseQuantityInfo(safe(leftCandidate.name())), parseQuantityInfo(safe(rightCandidate.name())));
    }

    private Compatibility compareQuantityInfo(QuantityInfo left, QuantityInfo right) {
        if (left.unit() == null || right.unit() == null || left.amount() == null || right.amount() == null) {
            return new Compatibility(false, 0d);
        }
        if (!areUnitsCompatible(left.unit(), right.unit())) {
            return new Compatibility(false, 0d);
        }
        double ratio = Math.min(left.amount(), right.amount()) / Math.max(left.amount(), right.amount());
        if (ratio < 0.98d) {
            return new Compatibility(false, 0d);
        }
        return new Compatibility(true, 1d);
    }

    private boolean areUnitsCompatible(String leftUnit, String rightUnit) {
        if (leftUnit.equals(rightUnit)) {
            return true;
        }
        // Marketplace feeds can encode liquid products as g instead of ml.
        return (leftUnit.equals("g") && rightUnit.equals("ml"))
                || (leftUnit.equals("ml") && rightUnit.equals("g"));
    }

    private boolean brandMatches(String leftBrand, String rightBrand) {
        String left = normalizeBrand(leftBrand);
        String right = normalizeBrand(rightBrand);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right) || left.contains(right) || right.contains(left)) {
            return true;
        }
        List<String> leftTokens = splitWords(left);
        List<String> rightTokens = splitWords(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }
        return leftTokens.getFirst().equals(rightTokens.getFirst());
    }

    private QuantityInfo parseQuantityInfo(MarketplaceProductCandidateResponse candidate) {
        if (candidate == null) {
            return new QuantityInfo(null, null, null);
        }
        QuantityInfo normalized = normalizeFieldQuantity(candidate.unitValue(), candidate.unit());
        Integer packCount = normalizePackCount(candidate.packCount());
        return new QuantityInfo(normalized.amount(), normalized.unit(), packCount);
    }

    private QuantityInfo parseQuantityInfo(String name) {
        String lower = safe(name).toLowerCase(TR);
        int index = findNextDigitIndex(lower, 0);
        while (index >= 0) {
            ParsedToken first = readNumericToken(lower, index);
            if (first != null) {
                QuantityInfo quantity = parsePackQuantity(lower, first);
                if (quantity != null) {
                    return quantity;
                }
                quantity = parseSingleQuantity(lower, first);
                if (quantity != null) {
                    return quantity;
                }
                index = findNextDigitIndex(lower, first.end());
                continue;
            }
            index = findNextDigitIndex(lower, index + 1);
        }
        return new QuantityInfo(null, null, extractPackCount(lower));
    }

    private Double parseAmount(String rawAmount, String rawUnit) {
        double amount = parseDoubleOrNaN(rawAmount);
        if (Double.isNaN(amount)) {
            return null;
        }
        return switch (rawUnit) {
            case "kg", "lt", "l" -> amount * 1000d;
            case "gr", "g", "ml" -> amount;
            default -> null;
        };
    }

    private String parseUnit(String rawUnit) {
        return switch (rawUnit) {
            case "kg", "gr", "g" -> "g";
            case "lt", "l", "ml" -> "ml";
            default -> null;
        };
    }

    private QuantityInfo normalizeFieldQuantity(Integer rawValue, String rawUnit) {
        if (rawValue == null || rawValue <= 0 || rawUnit == null || rawUnit.isBlank()) {
            return new QuantityInfo(null, null, null);
        }
        String lower = rawUnit.trim().toLowerCase(TR);
        return switch (lower) {
            case "kg" -> new QuantityInfo(rawValue.doubleValue() * 1000d, "g", null);
            case "gr", "g" -> new QuantityInfo(rawValue.doubleValue(), "g", null);
            case "lt", "l" -> new QuantityInfo(rawValue.doubleValue() * 1000d, "ml", null);
            case "ml" -> new QuantityInfo(rawValue.doubleValue(), "ml", null);
            default -> new QuantityInfo(null, null, null);
        };
    }

    private Integer normalizePackCount(Integer packCount) {
        if (packCount == null || packCount <= 1) {
            return null;
        }
        return packCount;
    }

    private Compatibility comparePriceConsistency(
            MarketplaceProductCandidateResponse source,
            MarketplaceProductCandidateResponse target
    ) {
        Double left = resolveComparablePrice(source);
        Double right = resolveComparablePrice(target);
        if (left == null || right == null) {
            return new Compatibility(true, 0.55d);
        }
        double ratio = Math.max(left, right) / Math.min(left, right);
        if (ratio > 2.2d) {
            return new Compatibility(false, 0d);
        }
        if (ratio <= 1.15d) {
            return new Compatibility(true, 1d);
        }
        if (ratio <= 1.35d) {
            return new Compatibility(true, 0.85d);
        }
        if (ratio <= 1.6d) {
            return new Compatibility(true, 0.65d);
        }
        if (ratio <= 2.0d) {
            return new Compatibility(true, 0.45d);
        }
        return new Compatibility(true, 0.25d);
    }

    private Double resolveComparablePrice(MarketplaceProductCandidateResponse candidate) {
        List<Double> prices = new ArrayList<>();
        addPositivePrice(prices, candidate.effectivePrice());
        addPositivePrice(prices, candidate.basketDiscountPrice());
        addPositivePrice(prices, candidate.moneyPrice());
        addPositivePrice(prices, candidate.price());
        return prices.isEmpty() ? null : prices.stream().min(Double::compare).orElse(null);
    }

    private void addPositivePrice(List<Double> bucket, BigDecimal value) {
        if (value == null) {
            return;
        }
        double numeric = value.doubleValue();
        if (Double.isFinite(numeric) && numeric > 0d) {
            bucket.add(numeric);
        }
    }

    private Compatibility compareProfiles(MatchProfile left, MatchProfile right) {
        if (!sameProfileCoreFlags(left, right) || !sameFlavor(left, right)) {
            return new Compatibility(false, 0d);
        }

        Compatibility fat = resolveFatScore(left, right);
        if (!fat.compatible()) {
            return new Compatibility(false, 0d);
        }
        double processScore = resolveProfileProcessScore(left, right);
        return new Compatibility(true, (fat.score() * 0.65d) + (processScore * 0.35d));
    }

    private boolean sameProfileCoreFlags(MatchProfile left, MatchProfile right) {
        return left.goatMilk() == right.goatMilk()
                && left.lactoseFree() == right.lactoseFree()
                && left.organic() == right.organic()
                && left.protein() == right.protein();
    }

    private boolean sameFlavor(MatchProfile left, MatchProfile right) {
        if ((left.flavor() == null) != (right.flavor() == null)) {
            return false;
        }
        return left.flavor() == null || left.flavor().equals(right.flavor());
    }

    private double resolveProfileProcessScore(MatchProfile left, MatchProfile right) {
        double processScore = 0.6d;
        if (left.uht() && right.uht()) {
            processScore += 0.15d;
        }
        if (left.pasteurized() && right.pasteurized()) {
            processScore += 0.15d;
        }
        if (left.daily() && right.daily()) {
            processScore += 0.1d;
        }
        if (left.bottle() && right.bottle()) {
            processScore += 0.1d;
        }
        return Math.min(processScore, 1d);
    }

    private Compatibility compareFormConsistency(
            MarketplaceProductCandidateResponse leftCandidate,
            MarketplaceProductCandidateResponse rightCandidate
    ) {
        String leftForm = detectPackageForm(safe(leftCandidate.name()));
        String rightForm = detectPackageForm(safe(rightCandidate.name()));
        if (leftForm.isEmpty() || rightForm.isEmpty()) {
            return new Compatibility(true, 0.7d);
        }
        if (leftForm.equals(rightForm)) {
            return new Compatibility(true, 1d);
        }
        return new Compatibility(true, 0.3d);
    }

    private Compatibility comparePackCountConsistency(
            MarketplaceProductCandidateResponse leftCandidate,
            MarketplaceProductCandidateResponse rightCandidate
    ) {
        QuantityInfo left = resolveQuantityInfo(leftCandidate);
        QuantityInfo right = resolveQuantityInfo(rightCandidate);
        if (left.packCount() == null || right.packCount() == null) {
            return new Compatibility(true, 0.7d);
        }
        if (left.packCount().equals(right.packCount())) {
            return new Compatibility(true, 1d);
        }
        return new Compatibility(true, 0.35d);
    }

    private QuantityInfo resolveQuantityInfo(MarketplaceProductCandidateResponse candidate) {
        QuantityInfo fromFields = parseQuantityInfo(candidate);
        if (fromFields.hasData()) {
            return fromFields;
        }
        return parseQuantityInfo(safe(candidate.name()));
    }

    private Compatibility resolveFatScore(MatchProfile left, MatchProfile right) {
        if (left.fatPercent() != null && right.fatPercent() != null) {
            double diff = Math.abs(left.fatPercent() - right.fatPercent());
            if (diff > 0.8d) {
                return new Compatibility(false, 0d);
            }
            if (diff <= 0.2d) {
                return new Compatibility(true, 1d);
            }
            if (diff <= 0.5d) {
                return new Compatibility(true, 0.75d);
            }
            return new Compatibility(true, 0.5d);
        }
        if (left.fatClass() != null && right.fatClass() != null) {
            return left.fatClass().equals(right.fatClass())
                    ? new Compatibility(true, 1d)
                    : new Compatibility(false, 0d);
        }
        return new Compatibility(true, 0.6d);
    }

    private MatchProfile extractMatchProfile(MarketplaceProductCandidateResponse candidate) {
        String text = normalizeMatchText(safe(candidate.name()));
        Double fatPercent = parseFatPercent(text);
        String flavor = null;
        if (text.contains("kakaolu")) {
            flavor = "kakao";
        } else if (text.contains("cilekli")) {
            flavor = "cilek";
        } else if (text.contains("muzlu")) {
            flavor = "muz";
        } else if (text.contains("latte")) {
            flavor = "latte";
        } else if (text.contains("kahveli")) {
            flavor = "kahve";
        }

        String fatClass = null;
        if (text.contains("tam yagli")) {
            fatClass = "FULL";
        } else if (text.contains("yarim yagli")) {
            fatClass = "HALF";
        } else if (text.contains("az yagli") || text.contains("0,5") || text.contains("0.5")) {
            fatClass = "LOW";
        }

        return new MatchProfile(
                text.contains("laktozsuz") || text.contains("rahat"),
                text.contains("organik"),
                text.contains("protein"),
                text.contains("keci"),
                flavor,
                fatPercent,
                fatClass,
                text.contains("uht"),
                text.contains("pastorize"),
                text.contains("gunluk"),
                text.contains("sise")
        );
    }

    private String detectPackageForm(String rawName) {
        String text = normalizeMatchText(rawName);
        if (text.contains("cam sise") || text.contains("sise")) {
            return "BOTTLE";
        }
        if (text.contains("pet")) {
            return "PET";
        }
        if (text.contains("kutu")) {
            return "CARTON";
        }
        if (text.contains("bardak")) {
            return "CUP";
        }
        if (text.contains("teneke")) {
            return "TIN";
        }
        if (text.contains("kavanoz")) {
            return "JAR";
        }
        if (text.contains(PACKAGE_TOKEN)) {
            return "PACK";
        }
        return "";
    }

    private Double parseFatPercent(String normalizedText) {
        int index = findNextDigitIndex(normalizedText, 0);
        while (index >= 0) {
            ParsedToken number = readNumericToken(normalizedText, index);
            if (number != null) {
                ParsedWord word = readWordToken(normalizedText, skipMeasurementWhitespace(normalizedText, number.end()));
                if (word != null && "yag".equals(word.value())) {
                    double parsed = parseDoubleOrNaN(number.value());
                    return Double.isFinite(parsed) ? parsed : null;
                }
                index = findNextDigitIndex(normalizedText, number.end());
                continue;
            }
            index = findNextDigitIndex(normalizedText, index + 1);
        }
        return null;
    }

    private double leadingPhraseScore(String leftName, String rightName) {
        List<String> left = semanticTokens(leftName);
        List<String> right = semanticTokens(rightName);
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        String left1 = left.getFirst();
        String right1 = right.getFirst();
        String left2 = String.join(" ", left.subList(0, Math.min(2, left.size())));
        String right2 = String.join(" ", right.subList(0, Math.min(2, right.size())));
        if (!left2.isEmpty() && left2.equals(right2)) {
            return 1d;
        }
        if (left1.equals(right1)) {
            return 0.9d;
        }
        if (left1.startsWith(right1) || right1.startsWith(left1)) {
            return 0.7d;
        }
        Set<String> leftHead = new LinkedHashSet<>(left.subList(0, Math.min(3, left.size())));
        Set<String> rightHead = new LinkedHashSet<>(right.subList(0, Math.min(3, right.size())));
        return jaccardSimilarity(leftHead, rightHead) * 0.7d;
    }

    private double phraseSimilarity(String leftName, String rightName) {
        List<String> left = semanticTokens(leftName);
        List<String> right = semanticTokens(rightName);
        if (left.size() < 2 || right.size() < 2) {
            return 0d;
        }
        Set<String> leftBigrams = new LinkedHashSet<>();
        Set<String> rightBigrams = new LinkedHashSet<>();
        for (int i = 0; i < left.size() - 1; i += 1) {
            leftBigrams.add(left.get(i) + " " + left.get(i + 1));
        }
        for (int i = 0; i < right.size() - 1; i += 1) {
            rightBigrams.add(right.get(i) + " " + right.get(i + 1));
        }
        return jaccardSimilarity(leftBigrams, rightBigrams);
    }

    private String inferBrand(MarketplaceProductCandidateResponse candidate) {
        String explicit = normalizeBrand(safe(candidate.brandName()));
        if (!explicit.isEmpty()) {
            return explicit;
        }
        List<String> tokens = splitWords(normalizeMatchText(safe(candidate.name())));
        if (tokens.isEmpty()) {
            return "";
        }
        return tokens.getFirst();
    }

    private String normalizeBrand(String brand) {
        String normalized = normalizeMatchText(safe(brand));
        if (normalized.isEmpty() || normalized.equals("marka yok")) {
            return "";
        }
        return normalized;
    }

    private double brandSimilarity(String leftBrand, String rightBrand) {
        String left = normalizeBrand(leftBrand);
        String right = normalizeBrand(rightBrand);
        if (left.isEmpty() || right.isEmpty()) {
            return 0.5d;
        }
        return brandSimilarityNormalized(left, right);
    }

    private double brandSimilarityNormalized(String left, String right) {
        if (left.equals(right)) {
            return 1d;
        }
        if (left.contains(right) || right.contains(left)) {
            return 0.92d;
        }
        Set<String> leftTokens = splitTokenSet(left);
        Set<String> rightTokens = splitTokenSet(right);
        double tokenScore = jaccardSimilarity(leftTokens, rightTokens);
        if (tokenScore >= 0.5d) {
            return 0.85d;
        }
        return 0d;
    }

    private double imageSimilarity(
            String leftUrl,
            String rightUrl,
            Map<String, ImageSignature> imageSignatureCache
    ) {
        String leftRaw = safe(leftUrl).trim();
        String rightRaw = safe(rightUrl).trim();
        if (leftRaw.isEmpty() || rightRaw.isEmpty()) {
            return 0d;
        }
        ImageSignature leftSignature = resolveImageSignature(leftRaw, imageSignatureCache);
        ImageSignature rightSignature = resolveImageSignature(rightRaw, imageSignatureCache);
        if (leftSignature != null && rightSignature != null) {
            double fullAHash = hashSimilarity(leftSignature.fullAHash(), rightSignature.fullAHash());
            double fullDHash = hashSimilarity(leftSignature.fullDHash(), rightSignature.fullDHash());
            double centerAHash = hashSimilarity(leftSignature.centerAHash(), rightSignature.centerAHash());
            double centerDHash = hashSimilarity(leftSignature.centerDHash(), rightSignature.centerDHash());
            double fullScore = (fullAHash * 0.55d) + (fullDHash * 0.45d);
            double centerScore = (centerAHash * 0.55d) + (centerDHash * 0.45d);
            return Math.max(fullScore, centerScore);
        }

        // Fallback path: URL fingerprint if image bytes are unreachable.
        if (leftRaw.equals(rightRaw)) {
            return 1d;
        }
        String left = imageFingerprint(leftRaw);
        String right = imageFingerprint(rightRaw);
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        if (left.equals(right)) {
            return 1d;
        }
        if (left.contains(right) || right.contains(left)) {
            return 0.9d;
        }
        Set<String> leftTokens = splitTokenSet(left);
        Set<String> rightTokens = splitTokenSet(right);
        double tokenScore = jaccardSimilarity(leftTokens, rightTokens);
        if (tokenScore > 0d) {
            return Math.min(0.85d, 0.45d + tokenScore * 0.4d);
        }
        return left.length() >= 10 && right.length() >= 10 && left.substring(0, 10).equals(right.substring(0, 10))
                ? 0.6d
                : 0d;
    }

    private ImageSignature resolveImageSignature(String url, Map<String, ImageSignature> requestCache) {
        String normalizedUrl = normalizeImageUrl(url);
        if (normalizedUrl.isBlank()) {
            return null;
        }
        if (requestCache.containsKey(normalizedUrl)) {
            return requestCache.get(normalizedUrl);
        }
        CachedImageSignature processCached = PROCESS_IMAGE_CACHE.get(normalizedUrl);
        if (processCached != null) {
            requestCache.put(normalizedUrl, processCached.signature());
            return processCached.signature();
        }
        CachedImageSignature persisted = loadPersistedSignature(normalizedUrl);
        if (persisted != null) {
            putProcessCache(normalizedUrl, persisted);
            requestCache.put(normalizedUrl, persisted.signature());
            return persisted.signature();
        }
        ImageSignature signature = null;
        try {
            URI uri = URI.create(normalizedUrl);
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                putUnavailableCaches(normalizedUrl, requestCache);
                return null;
            }
            String host = safe(uri.getHost()).toLowerCase(Locale.ROOT);
            if (host.endsWith(".test") || host.equals("localhost")) {
                putUnavailableCaches(normalizedUrl, requestCache);
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(IMAGE_REQUEST_TIMEOUT)
                    .header("User-Agent", "smart-pantry-image-matcher/1.0")
                    .build();
            HttpResponse<byte[]> response = IMAGE_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                signature = buildImageSignature(image);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException ex) {
            // Image retrieval is best-effort; matching continues without image signal.
        }
        if (signature == null) {
            putUnavailableCaches(normalizedUrl, requestCache);
            return null;
        }
        CachedImageSignature cached = new CachedImageSignature(signature, false);
        putProcessCache(normalizedUrl, cached);
        requestCache.put(normalizedUrl, signature);
        persistSignature(normalizedUrl, cached);
        return signature;
    }

    private CachedImageSignature loadPersistedSignature(String normalizedUrl) {
        if (imageSignatureCacheRepository == null) {
            return null;
        }
        try {
            return imageSignatureCacheRepository.findByNormalizedUrl(normalizedUrl)
                    .map(this::toCachedSignature)
                    .orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void persistSignature(String normalizedUrl, CachedImageSignature cached) {
        if (imageSignatureCacheRepository == null) {
            return;
        }
        try {
            ImageSignatureCache entity = imageSignatureCacheRepository.findByNormalizedUrl(normalizedUrl)
                    .orElseGet(ImageSignatureCache::new);
            entity.setNormalizedUrl(normalizedUrl);
            entity.setUnavailable(cached.unavailable());
            entity.setUpdatedAt(LocalDateTime.now());
            if (cached.signature() == null) {
                entity.setFullAHash(null);
                entity.setFullDHash(null);
                entity.setCenterAHash(null);
                entity.setCenterDHash(null);
            } else {
                entity.setFullAHash(cached.signature().fullAHash());
                entity.setFullDHash(cached.signature().fullDHash());
                entity.setCenterAHash(cached.signature().centerAHash());
                entity.setCenterDHash(cached.signature().centerDHash());
            }
            imageSignatureCacheRepository.save(entity);
        } catch (RuntimeException ignored) {
            // DB cache is opportunistic; matching should continue even when persistence fails.
        }
    }

    private CachedImageSignature toCachedSignature(ImageSignatureCache entity) {
        if (entity.isUnavailable()) {
            return new CachedImageSignature(null, true);
        }
        if (entity.getFullAHash() == null ||
                entity.getFullDHash() == null ||
                entity.getCenterAHash() == null ||
                entity.getCenterDHash() == null) {
            return new CachedImageSignature(null, true);
        }
        return new CachedImageSignature(
                new ImageSignature(
                        entity.getFullAHash(),
                        entity.getFullDHash(),
                        entity.getCenterAHash(),
                        entity.getCenterDHash()
                ),
                false
        );
    }

    private void putUnavailableCaches(String normalizedUrl, Map<String, ImageSignature> requestCache) {
        CachedImageSignature unavailable = new CachedImageSignature(null, true);
        putProcessCache(normalizedUrl, unavailable);
        requestCache.put(normalizedUrl, null);
        persistSignature(normalizedUrl, unavailable);
    }

    private void putProcessCache(String normalizedUrl, CachedImageSignature cached) {
        if (PROCESS_IMAGE_CACHE.size() >= PROCESS_IMAGE_CACHE_MAX_SIZE) {
            PROCESS_IMAGE_CACHE.clear();
        }
        PROCESS_IMAGE_CACHE.put(normalizedUrl, cached);
    }

    private ImageSignature buildImageSignature(BufferedImage image) {
        if (image == null || image.getWidth() <= 1 || image.getHeight() <= 1) {
            return null;
        }
        BufferedImage normalized = toGrayscale(image);
        BufferedImage centerCrop = cropCenter(normalized);
        return new ImageSignature(
                averageHash(normalized),
                differenceHash(normalized),
                averageHash(centerCrop),
                differenceHash(centerCrop)
        );
    }

    private BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = output.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return output;
    }

    private BufferedImage cropCenter(BufferedImage source) {
        double ratio = 0.75d;
        int width = source.getWidth();
        int height = source.getHeight();
        int cropWidth = Math.max(2, (int) Math.round(width * ratio));
        int cropHeight = Math.max(2, (int) Math.round(height * ratio));
        int x = Math.max(0, (width - cropWidth) / 2);
        int y = Math.max(0, (height - cropHeight) / 2);
        return source.getSubimage(x, y, cropWidth, cropHeight);
    }

    private long averageHash(BufferedImage source) {
        int width = 8;
        int height = 8;
        BufferedImage scaled = resize(source, width, height);
        int[] values = new int[width * height];
        long sum = 0L;
        int index = 0;
        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width; x += 1) {
                int value = scaled.getRaster().getSample(x, y, 0);
                values[index] = value;
                sum += value;
                index += 1;
            }
        }
        double avg = (double) sum / (double) values.length;
        long hash = 0L;
        for (int i = 0; i < values.length; i += 1) {
            if (values[i] >= avg) {
                hash |= (1L << i);
            }
        }
        return hash;
    }

    private long differenceHash(BufferedImage source) {
        int width = 9;
        int height = 8;
        BufferedImage scaled = resize(source, width, height);
        long hash = 0L;
        int bitIndex = 0;
        for (int y = 0; y < height; y += 1) {
            for (int x = 0; x < width - 1; x += 1) {
                int left = scaled.getRaster().getSample(x, y, 0);
                int right = scaled.getRaster().getSample(x + 1, y, 0);
                if (left >= right) {
                    hash |= (1L << bitIndex);
                }
                bitIndex += 1;
            }
        }
        return hash;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(source, 0, 0, width, height, null);
        g2d.dispose();
        return resized;
    }

    private double hashSimilarity(long left, long right) {
        long diff = left ^ right;
        int distance = Long.bitCount(diff);
        return 1d - (distance / 64d);
    }

    private Set<String> splitTokenSet(String value) {
        String[] split = value.split("[^a-z0-9]+");
        Set<String> result = new LinkedHashSet<>();
        for (String token : split) {
            if (token.length() > 2) {
                result.add(token);
            }
        }
        return result;
    }

    private String imageFingerprint(String url) {
        if (url.isEmpty()) {
            return "";
        }
        String withoutQuery = url.split("\\?")[0];
        String[] parts = withoutQuery.split("/");
        String fileName = parts.length == 0 ? "" : parts[parts.length - 1];
        return fileName.replaceAll("\\.[a-z0-9]+$", "").toLowerCase(TR);
    }

    private Set<String> tokenSet(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : semanticTokens(value)) {
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Set<String> coreTokenSet(String value) {
        Set<String> source = tokenSet(value);
        Set<String> filtered = new LinkedHashSet<>();
        for (String token : source) {
            if (!NAME_STOP_WORDS.contains(token) && token.length() > 2) {
                filtered.add(token);
            }
        }
        return filtered;
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection += 1;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union == 0 ? 0d : (double) intersection / (double) union;
    }

    private String normalizeMatchText(String value) {
        return normalizePlainText(value).replaceAll("\\s+", " ").trim();
    }

    private String normalizePlainText(String value) {
        String lower = safe(value).toLowerCase(TR);
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    private List<String> splitWords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\s+"));
    }

    private List<String> semanticTokens(String value) {
        String normalized = normalizePlainText(value)
                .transform(this::stripMeasurementTokens)
                .transform(this::stripPackTokens)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String rawToken : tokenizeWithNlp(normalized)) {
            String token = normalizeMatchText(rawToken);
            if (!token.isBlank()) {
                token = NLP_LEMMA_MAP.getOrDefault(token, token);
                token = stemToken(token);
                if (!NLP_NOISE_TOKENS.contains(token) && token.length() > 1) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private String stripMeasurementTokens(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            int digitIndex = findNextDigitIndex(value, index);
            if (digitIndex < 0) {
                cleaned.append(value, index, value.length());
                break;
            }
            cleaned.append(value, index, digitIndex);
            MeasurementSpan measurementSpan = findMeasurementSpan(value, digitIndex);
            if (measurementSpan != null) {
                cleaned.append(' ');
                index = measurementSpan.end();
                continue;
            }
            ParsedToken first = readNumericToken(value, digitIndex);
            if (first == null) {
                cleaned.append(value.charAt(digitIndex));
                index = digitIndex + 1;
                continue;
            }
            cleaned.append(value, digitIndex, first.end());
            index = first.end();
        }
        return cleaned.toString();
    }

    private Integer extractPackCount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int index = findNextDigitIndex(value, 0);
        while (index >= 0) {
            PackSuffixMatch match = readPackSuffixMatch(value, index);
            if (match != null) {
                return match.packCount();
            }
            index = findNextDigitIndex(value, index + 1);
        }
        return null;
    }

    private String stripPackTokens(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            int digitIndex = findNextDigitIndex(value, index);
            if (digitIndex < 0) {
                cleaned.append(value, index, value.length());
                break;
            }
            cleaned.append(value, index, digitIndex);
            SemanticPackSpan semanticPackSpan = readSemanticPackSpan(value, digitIndex);
            if (semanticPackSpan != null) {
                cleaned.append(' ');
                index = semanticPackSpan.end();
                continue;
            }
            ParsedToken number = readNumericToken(value, digitIndex);
            if (number == null) {
                cleaned.append(value.charAt(digitIndex));
                index = digitIndex + 1;
                continue;
            }
            cleaned.append(value, digitIndex, number.end());
            index = number.end();
        }
        return cleaned.toString();
    }

    private QuantityInfo parsePackQuantity(String value, ParsedToken first) {
        int current = skipMeasurementWhitespace(value, first.end());
        if (current >= value.length() || (value.charAt(current) != 'x' && value.charAt(current) != 'X')) {
            return null;
        }
        ParsedToken second = readNumericToken(value, skipMeasurementWhitespace(value, current + 1));
        if (second == null) {
            return null;
        }
        ParsedWord unitWord = readWordToken(value, skipMeasurementWhitespace(value, second.end()));
        if (unitWord == null || !isMeasurementUnit(unitWord.value())) {
            return null;
        }
        Double amount = parseAmount(second.value(), unitWord.value());
        String unit = parseUnit(unitWord.value());
        Integer packCount = parseIntOrNull(first.value());
        return new QuantityInfo(amount, unit, packCount);
    }

    private QuantityInfo parseSingleQuantity(String value, ParsedToken first) {
        ParsedWord unitWord = readWordToken(value, skipMeasurementWhitespace(value, first.end()));
        if (unitWord == null || !isMeasurementUnit(unitWord.value())) {
            return null;
        }
        Double amount = parseAmount(first.value(), unitWord.value());
        String unit = parseUnit(unitWord.value());
        return new QuantityInfo(amount, unit, extractPackCount(value));
    }

    private MeasurementSpan findMeasurementSpan(String value, int startIndex) {
        ParsedToken first = readNumericToken(value, startIndex);
        if (first == null) {
            return null;
        }
        QuantityInfo packQuantity = parsePackQuantity(value, first);
        if (packQuantity != null) {
            int current = skipMeasurementWhitespace(value, first.end());
            ParsedToken second = readNumericToken(value, skipMeasurementWhitespace(value, current + 1));
            if (second == null) {
                return null;
            }
            ParsedWord unitWord = readWordToken(value, skipMeasurementWhitespace(value, second.end()));
            return unitWord == null ? null : new MeasurementSpan(unitWord.end());
        }
        ParsedWord unitWord = readWordToken(value, skipMeasurementWhitespace(value, first.end()));
        if (unitWord != null && isMeasurementUnit(unitWord.value())) {
            return new MeasurementSpan(unitWord.end());
        }
        return null;
    }

    private PackSuffixMatch readPackSuffixMatch(String value, int startIndex) {
        ParsedToken number = readWholeNumberToken(value, startIndex);
        if (number == null) {
            return null;
        }
        int suffixStart = skipPackDelimiters(value, number.end());
        int suffixEnd = readLetterSequenceEnd(value, suffixStart);
        if (suffixEnd == suffixStart) {
            return null;
        }
        String suffix = value.substring(suffixStart, suffixEnd);
        if (!isPackSuffix(suffix) || !isWordBoundary(value, suffixEnd)) {
            return null;
        }
        return new PackSuffixMatch(Integer.parseInt(number.value()), suffixEnd);
    }

    private SemanticPackSpan readSemanticPackSpan(String value, int startIndex) {
        ParsedToken number = readWholeNumberToken(value, startIndex);
        if (number == null) {
            return null;
        }
        int suffixStart = skipPackWhitespace(value, number.end());
        int suffixEnd = readLetterSequenceEnd(value, suffixStart);
        if (suffixEnd <= suffixStart) {
            return null;
        }
        String suffix = value.substring(suffixStart, suffixEnd);
        if (isSemanticPackSuffix(suffix) && isWordBoundary(value, suffixEnd)) {
            return new SemanticPackSpan(suffixEnd);
        }
        return null;
    }

    private int skipPackDelimiters(String value, int index) {
        int current = skipPackWhitespace(value, index);
        if (current < value.length()) {
            char currentChar = value.charAt(current);
            if (currentChar == '\'' || currentChar == '’') {
                current++;
            }
        }
        return current;
    }

    private int skipPackWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private boolean isPackSuffix(String suffix) {
        return switch (suffix) {
            case "li", "lı", "lu", "lü", "pack", PACKAGE_TOKEN -> true;
            default -> false;
        };
    }

    private int findNextDigitIndex(String value, int startIndex) {
        int current = Math.max(0, startIndex);
        while (current < value.length() && !Character.isDigit(value.charAt(current))) {
            current++;
        }
        return current < value.length() ? current : -1;
    }

    private int readLetterSequenceEnd(String value, int startIndex) {
        int current = startIndex;
        while (current < value.length() && Character.isLetter(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private ParsedToken readWholeNumberToken(String value, int start) {
        if (start >= value.length() || !Character.isDigit(value.charAt(start))) {
            return null;
        }
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return new ParsedToken(value.substring(start, end), end);
    }

    private ParsedToken readNumericToken(String value, int start) {
        if (start >= value.length() || !Character.isDigit(value.charAt(start))) {
            return null;
        }
        int end = start;
        boolean seenSeparator = false;
        while (end < value.length()) {
            char current = value.charAt(end);
            if (Character.isDigit(current)) {
                end++;
                continue;
            }
            if (!seenSeparator && (current == '.' || current == ',')) {
                seenSeparator = true;
                end++;
                continue;
            }
            break;
        }
        return new ParsedToken(value.substring(start, end), end);
    }

    private ParsedWord readWordToken(String value, int start) {
        int current = skipMeasurementWhitespace(value, start);
        if (current >= value.length() || !Character.isLetter(value.charAt(current))) {
            return null;
        }
        int end = current;
        while (end < value.length() && Character.isLetter(value.charAt(end))) {
            end++;
        }
        return new ParsedWord(value.substring(current, end), end);
    }

    private int skipMeasurementWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }

    private boolean isMeasurementUnit(String unit) {
        return switch (unit) {
            case "kg", "gr", "g", "ml", "lt", "l" -> true;
            default -> false;
        };
    }

    private Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ParsedToken(String value, int end) {
    }

    private record ParsedWord(String value, int end) {
    }

    private record MeasurementSpan(int end) {
    }

    private record PackSuffixMatch(int packCount, int end) {
    }

    private record SemanticPackSpan(int end) {
    }

    private boolean isSemanticPackSuffix(String suffix) {
        return switch (suffix) {
            case "li", "lı", "lu", "lü", "pack", PACKAGE_TOKEN, "adet" -> true;
            default -> false;
        };
    }

    private boolean isWordBoundary(String value, int index) {
        return index >= value.length() || !Character.isLetterOrDigit(value.charAt(index));
    }

    private List<String> tokenizeWithNlp(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return splitWords(normalizeMatchText(value));
    }

    private String stemToken(String token) {
        if (token.isBlank() || token.length() <= 4) {
            return token;
        }
        String cached = NLP_TOKEN_CACHE.get(token);
        if (cached != null) {
            return cached;
        }
        String resolved = token;
        try {
            TurkishStemmer stemmer = new TurkishStemmer();
            stemmer.setCurrent(token);
            if (stemmer.stem()) {
                String stemmed = normalizeMatchText(stemmer.getCurrent());
                if (!stemmed.isBlank()) {
                    resolved = NLP_LEMMA_MAP.getOrDefault(stemmed, stemmed);
                }
            }
        } catch (RuntimeException ignored) {
            // NLP stemming is best-effort; keep original token when stemmer fails.
        }

        if (NLP_TOKEN_CACHE.size() > NLP_TOKEN_CACHE_MAX_SIZE) {
            NLP_TOKEN_CACHE.clear();
        }
        NLP_TOKEN_CACHE.put(token, resolved);
        return resolved;
    }

    private String normalizeExternalId(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeImageUrl(String value) {
        return safe(value).trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double parseDoubleOrNaN(String value) {
        try {
            return Double.parseDouble(value.replace(",", "."));
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private record QuantityInfo(
            Double amount,
            String unit,
            Integer packCount
    ) {
        boolean hasData() {
            return amount != null || unit != null || packCount != null;
        }
    }

    private record Compatibility(
            boolean compatible,
            double score
    ) {
    }

    private record ImageSignature(
            long fullAHash,
            long fullDHash,
            long centerAHash,
            long centerDHash
    ) {
    }

    private record CachedImageSignature(
            ImageSignature signature,
            boolean unavailable
    ) {
    }

    private record MatchProfile(
            boolean lactoseFree,
            boolean organic,
            boolean protein,
            boolean goatMilk,
            String flavor,
            Double fatPercent,
            String fatClass,
            boolean uht,
            boolean pasteurized,
            boolean daily,
            boolean bottle
    ) {
    }
}
