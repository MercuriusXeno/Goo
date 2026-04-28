package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Handles parallel copy operations during base_values.json loading.
 * A parallel copy assigns values from one pseudo-tag's members to another's,
 * with optional scaling via multiplier/divisor.
 */
final class GooParallelCopy {

    /**
     * Regex group index for the multiplier in parallel copy pattern.
     */
    private static final int PARALLEL_COPY_MULTIPLIER_GROUP = 2;
    /**
     * Regex group index for the divisor in parallel copy pattern.
     */
    private static final int PARALLEL_COPY_DIVISOR_GROUP = 3;

    /**
     * Pattern for parallel copy with optional scale: "#source * N / M" or just "#source".
     */
    private static final java.util.regex.Pattern PARALLEL_COPY_PATTERN = java.util.regex.Pattern.compile(
            "#(\\w+)(?:\\s*\\*\\s*(\\d+)\\s*/\\s*(\\d+))?\\s*");

    private static final String LOG_PSEUDO_TAG_EMPTY = "Pseudo-tag #{} resolved to no members, skipping";
    private static final String LOG_PARALLEL_SRC_EMPTY = "Parallel copy source #{} resolved to no members";
    private static final String LOG_PARALLEL_MISMATCH = "Parallel copy size mismatch: #{} has {} items, #{} has {} items";
    private static final String LOG_PARALLEL_NO_VALUE = "Parallel copy: source {} has no value, skipping target {}";
    private static final String LOG_PARALLEL_NEGATIVE = "Parallel copy produced negative for {}: {}";
    private static final String LOG_PARALLEL_SCALE_FAIL = "Parallel copy scale failed for {} (from {}): {}";

    private GooParallelCopy() {
    }

    /**
     * Expands a #name key against pseudo-tags, assigning the value to each member.
     *
     * @param name  the pseudo-tag name (without # prefix)
     * @param value the JSON value to assign to each member
     * @param state mutable parsing state
     */
    static void expandPseudoTag(String name, JsonElement value, GooValueLoader.ParseState state) {
        Set<Identifier> targetMembers = GooGroupParser.resolvePseudoTag(name, state.pseudoTags);
        if (targetMembers == null || targetMembers.isEmpty()) {
            Goo.LOGGER.warn(LOG_PSEUDO_TAG_EMPTY, name);
            return;
        }
        if (tryParallelCopy(name, value, targetMembers, state)) {
            return;
        }
        for (Identifier member : targetMembers) {
            GooValueLoader.assignItemValue(member, value, state);
        }
    }

    /**
     * Checks if the value is a parallel copy expression ("#source * N / M") and applies it.
     *
     * @param name          the target pseudo-tag name
     * @param value         the JSON value to check
     * @param targetMembers resolved target item IDs
     * @param state         mutable parsing state
     * @return true if a parallel copy was applied
     */
    private static boolean tryParallelCopy(String name, JsonElement value,
                                           Set<Identifier> targetMembers, GooValueLoader.ParseState state) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return false;
        }
        Matcher m = PARALLEL_COPY_PATTERN.matcher(value.getAsString().trim());
        if (!m.matches()) {
            return false;
        }
        String sourceName = m.group(1);
        int multiplier = parseGroupOrDefault(m, PARALLEL_COPY_MULTIPLIER_GROUP);
        int divisor = parseGroupOrDefault(m, PARALLEL_COPY_DIVISOR_GROUP);
        parallelCopyBaseValues(name, sourceName, targetMembers, multiplier, divisor, state);
        return true;
    }

    /**
     * Parses a regex group as an integer, defaulting to 1 if the group did not match.
     *
     * @param m     the matcher with a successful match
     * @param group the capture group index to parse
     * @return the parsed integer, or 1 if the group is absent
     */
    private static int parseGroupOrDefault(Matcher m, int group) {
        return m.group(group) != null ? Integer.parseInt(m.group(group)) : 1;
    }

    /**
     * Parallel copy from source tag to target tag, with optional scale.
     *
     * @param targetName    the target pseudo-tag name
     * @param sourceName    the source pseudo-tag name to copy from
     * @param targetMembers resolved target item IDs
     * @param multiplier    numerator for post-copy scaling
     * @param divisor       denominator for post-copy scaling
     * @param state         mutable parsing state
     */
    private static void parallelCopyBaseValues(String targetName, String sourceName,
                                               Set<Identifier> targetMembers,
                                               int multiplier, int divisor, GooValueLoader.ParseState state) {
        List<Identifier> sources = resolveSourceMembers(sourceName, state);
        if (sources.isEmpty()) {
            return;
        }
        List<Identifier> targets = new ArrayList<>(targetMembers);
        if (!validateParallelSize(targetName, targets, sourceName, sources)) {
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            copyScaledValue(sources.get(i), targets.get(i), multiplier, divisor, state.baseValues);
        }
    }

    /**
     * Resolves a source pseudo-tag, returning empty list with an error log if empty.
     *
     * @param sourceName the pseudo-tag name to resolve
     * @param state      the current parse state with pseudo-tag definitions
     * @return the resolved member list, or empty if the tag is empty
     */
    private static List<Identifier> resolveSourceMembers(String sourceName, GooValueLoader.ParseState state) {
        Set<Identifier> sourceMembers = GooGroupParser.resolvePseudoTag(sourceName, state.pseudoTags);
        if (sourceMembers == null || sourceMembers.isEmpty()) {
            Goo.LOGGER.error(LOG_PARALLEL_SRC_EMPTY, sourceName);
            return List.of();
        }
        return new ArrayList<>(sourceMembers);
    }

    /**
     * Validates that parallel copy source and target have equal size.
     *
     * @param targetName target tag name for logging
     * @param targets    resolved target IDs
     * @param sourceName source tag name for logging
     * @param sources    resolved source IDs
     * @return true if sizes match
     */
    private static boolean validateParallelSize(String targetName, List<Identifier> targets,
                                                String sourceName, List<Identifier> sources) {
        if (targets.size() == sources.size()) {
            return true;
        }
        if (Goo.LOGGER.isErrorEnabled()) {
            Goo.LOGGER.error(LOG_PARALLEL_MISMATCH,
                    targetName, targets.size(), sourceName, sources.size());
        }
        return false;
    }

    /**
     * Copies a single source item's value to a target, applying multiplier/divisor scaling.
     *
     * @param source     the source item ID
     * @param target     the target item ID
     * @param multiplier numerator for scaling
     * @param divisor    denominator for scaling
     * @param baseValues the base values map
     */
    private static void copyScaledValue(Identifier source, Identifier target,
                                        int multiplier, int divisor,
                                        Map<Identifier, GooValue> baseValues) {
        GooValue sourceVal = baseValues.get(source);
        if (sourceVal == null || sourceVal.isEmpty()) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_PARALLEL_NO_VALUE, source, target);
            }
            return;
        }
        applyScaling(sourceVal, target, source, multiplier, divisor, baseValues);
    }

    /**
     * Scales a source value and stores the result, logging errors for negatives or overflow.
     *
     * @param sourceVal  the source goo value to scale
     * @param target     the target item ID
     * @param source     the source item ID (for error logging)
     * @param multiplier numerator for scaling
     * @param divisor    denominator for scaling
     * @param baseValues the base values map
     */
    private static void applyScaling(GooValue sourceVal, Identifier target, Identifier source,
                                     int multiplier, int divisor,
                                     Map<Identifier, GooValue> baseValues) {
        try {
            GooValue scaled = sourceVal.multiply(multiplier).divideExact(divisor);
            if (scaled.hasNegative()) {
                if (Goo.LOGGER.isErrorEnabled()) {
                    Goo.LOGGER.error(LOG_PARALLEL_NEGATIVE, target, scaled);
                }
                return;
            }
            baseValues.put(target, scaled);
        } catch (ArithmeticException e) {
            if (Goo.LOGGER.isErrorEnabled()) {
                Goo.LOGGER.error(LOG_PARALLEL_SCALE_FAIL, target, source, e.getMessage());
            }
        }
    }
}
