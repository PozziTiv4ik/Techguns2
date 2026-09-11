package techguns.core;

/** The original Nether table order and its inclusive cumulative-weight comparison. */
public final class NetherSpawnRules {
    public enum Choice { NONE, ZOMBIE_PIGMAN_SOLDIER, CYBER_DEMON }
    public static Choice choose(int pigmanWeight, int cyberWeight, int roll) {
        if (pigmanWeight < 0 || pigmanWeight > 10000 || cyberWeight < 0 || cyberWeight > 10000)
            throw new IllegalArgumentException("Invalid spawn weights");
        int total = pigmanWeight + cyberWeight;
        if (total == 0) return Choice.NONE;
        if (roll < 0 || roll >= total) throw new IllegalArgumentException("Roll outside spawn table");
        // TGSpawnManager uses >= roll with nextInt(total), giving the first nonzero entry the boundary ticket.
        if (pigmanWeight > 0 && pigmanWeight >= roll) return Choice.ZOMBIE_PIGMAN_SOLDIER;
        return cyberWeight > 0 ? Choice.CYBER_DEMON : Choice.ZOMBIE_PIGMAN_SOLDIER;
    }
    private NetherSpawnRules() {}
}
