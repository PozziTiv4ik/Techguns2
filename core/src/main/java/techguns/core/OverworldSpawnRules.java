package techguns.core;

/** TGSpawnManager: danger buckets, registration order within each bucket, inclusive weight boundary. */
public final class OverworldSpawnRules {
    public enum Choice {
        ZOMBIE_FARMER(0), ZOMBIE_MINER(0), ZOMBIE_SOLDIER(1), SKELETON_SOLDIER(1), PSYCHO_STEVE(1), BANDIT(2), NONE(0);
        private final int danger;
        Choice(int danger) { this.danger = danger; }
    }
    public record Weights(int farmer, int miner, int soldier, int skeleton, int psycho, int bandit) {
        public Weights {
            for (int weight : new int[]{farmer, miner, soldier, skeleton, psycho, bandit})
                if (weight < 0 || weight > 10000) throw new IllegalArgumentException("Invalid spawn weight");
        }
        public int get(Choice choice) {
            return switch (choice) {
                case ZOMBIE_FARMER -> farmer;
                case ZOMBIE_MINER -> miner;
                case ZOMBIE_SOLDIER -> soldier;
                case SKELETON_SOLDIER -> skeleton;
                case PSYCHO_STEVE -> psycho;
                case BANDIT -> bandit;
                case NONE -> 0;
            };
        }
        public int total(int danger) {
            int total = 0;
            for (Choice choice : Choice.values()) if (choice.danger <= danger) total += get(choice);
            return total;
        }
        public Choice choose(int danger, int roll) {
            int total = total(danger);
            if (total == 0) return Choice.NONE;
            if (roll < 0 || roll >= total) throw new IllegalArgumentException("Roll outside spawn table");
            int cumulative = 0;
            for (Choice choice : Choice.values()) {
                if (choice.danger > danger || get(choice) == 0) continue;
                cumulative += get(choice);
                if (cumulative >= roll) return choice;
            }
            throw new IllegalStateException("Unreachable spawn ticket");
        }
    }
    public static int distanceDanger(double x, double z, double spawnX, double spawnZ, int level0, int level1, int level2) {
        double distance = Math.hypot(x - spawnX, z - spawnZ);
        return distance < level0 ? 0 : distance < level1 ? 1 : distance < level2 ? 2 : 3;
    }
    private OverworldSpawnRules() {}
}
