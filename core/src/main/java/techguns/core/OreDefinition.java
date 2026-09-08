package techguns.core;

/** Original absolute world heights and mining metadata; maxY is exclusive. */
public record OreDefinition(String id, String config, boolean enabledByDefault, int legacyMetadata,
                            float hardness, int miningLevel, int light, int minSize, int maxSize,
                            int attempts, int minY, int maxY) {}
