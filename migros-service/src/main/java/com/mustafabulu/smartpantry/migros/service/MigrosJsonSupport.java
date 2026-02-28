package com.mustafabulu.smartpantry.migros.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mustafabulu.smartpantry.common.core.util.MigrosEffectivePriceCampaignParser;
import com.mustafabulu.smartpantry.common.core.util.MarketplacePriceNormalizer;
import com.mustafabulu.smartpantry.migros.constant.MigrosConstants;

import java.math.BigDecimal;
import java.util.function.Function;

final class MigrosJsonSupport {
    private MigrosJsonSupport() {
    }

    static JsonNode resolveDiscountTagsNode(JsonNode crmDiscountTagsNode, String tagKey) {
        if (isMissingOrNull(crmDiscountTagsNode)) {
            return MissingNode.getInstance();
        }
        ArrayNode tags = JsonNodeFactory.instance.arrayNode();
        collectDiscountTagNodes(crmDiscountTagsNode, tags, tagKey);
        return tags.isEmpty() ? MissingNode.getInstance() : tags;
    }

    static <T> T parseFromTextNodes(
            JsonNode nodes,
            String fieldName,
            Function<String, T> parser
    ) {
        if (!nodes.isArray()) {
            return null;
        }
        for (JsonNode node : nodes) {
            String text = node.path(fieldName).asText("");
            T parsed = parser.apply(text);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    static BigDecimal normalizeMigrosPrice(BigDecimal value) {
        return MarketplacePriceNormalizer.normalizePotentialCents(value);
    }

    static MigrosEffectivePriceCampaignParser.EffectivePriceCampaign resolveEffectiveCampaign(JsonNode entry) {
        MigrosEffectivePriceCampaignParser.EffectivePriceCampaign fromTags =
                parseFromTextNodes(
                        resolveDiscountTagsNode(
                                entry.path(MigrosConstants.CRM_DISCOUNT_TAGS_KEY),
                                MigrosConstants.TAG_KEY
                        ),
                        MigrosConstants.TAG_KEY,
                        MigrosEffectivePriceCampaignParser::parse
                );
        if (fromTags != null) {
            return fromTags;
        }
        return parseFromTextNodes(entry.path("lists"), "name", MigrosEffectivePriceCampaignParser::parse);
    }

    private static void collectDiscountTagNodes(
            JsonNode node,
            ArrayNode output,
            String tagKey
    ) {
        if (isMissingOrNull(node)) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectDiscountTagNodes(child, output, tagKey);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has(tagKey)) {
            output.add(node);
        }
        if (node.has(MigrosConstants.CRM_DISCOUNT_TAGS_KEY)) {
            collectDiscountTagNodes(node.path(MigrosConstants.CRM_DISCOUNT_TAGS_KEY), output, tagKey);
        }
    }

    private static boolean isMissingOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }
}
