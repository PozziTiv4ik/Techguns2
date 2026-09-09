package techguns.core;

import java.util.Random;

/** The original chamber checks a reaction every 60 ticks, strictly before its deadline. */
public final class ReactionCycle {
    public static final int INTERVAL = 60;
    private static final long RANDOM_MASK = (1L << 48) - 1;
    public record Rules(int cycles, int requiredCompletion, int intensity, int margin, int liquidLevel,
                        double instability, int energyPerCheck) {
        public Rules {
            if (cycles < 2 || cycles > 1200 || requiredCompletion < 1 || requiredCompletion >= cycles
                    || intensity < 0 || intensity > 10 || margin < 0 || margin > 10
                    || liquidLevel < 1 || liquidLevel > 10 || !Double.isFinite(instability)
                    || instability < 0 || instability > 1 || energyPerCheck < 1 || energyPerCheck > 1000000)
                throw new IllegalArgumentException("Invalid reaction parameters");
        }
        public int deadline() { return cycles * INTERVAL; }
    }
    public record State(int elapsed, int completion, int nextCheck, int requiredIntensity, long randomState) {
        public State {
            if (elapsed < 0 || completion < 0 || nextCheck < 1 || nextCheck > INTERVAL
                    || requiredIntensity < 0 || requiredIntensity > 10) throw new IllegalArgumentException("Invalid reaction state");
        }
        public static State start(Rules rules, long seed) { return new State(0, 0, INTERVAL, rules.intensity, (seed ^ 0x5DEECE66DL) & RANDOM_MASK); }
        public boolean checkDue(Rules rules) { return nextCheck == 1 && elapsed + 1 < rules.deadline(); }
    }
    public enum Outcome { RUNNING, SUCCESS, FAILURE }
    public record Step(State state, boolean checked, boolean good, Outcome outcome) {}

    public static Step advance(Rules rules, State old, int intensity, int liquidLevel, boolean focusMatches, boolean powered) {
        if (old.completion >= rules.requiredCompletion) return new Step(old, false, false, Outcome.SUCCESS);
        if (old.elapsed >= rules.deadline()) return new Step(old, false, false, Outcome.FAILURE);
        int elapsed = old.elapsed + 1, completion = old.completion, required = old.requiredIntensity;
        boolean checked = old.checkDue(rules), good = false;
        PersistedRandom random = new PersistedRandom(old.randomState);
        if (checked) {
            good = powered && focusMatches && required == intensity && rules.liquidLevel == liquidLevel;
            if (good) completion++;
            if (!focusMatches) { elapsed = rules.deadline(); completion = 0; }
            else if (rules.instability > 0 && random.nextFloat() < rules.instability) {
                int change = 1 + (rules.margin > 1 ? random.nextInt(rules.margin) : 0);
                required = Math.clamp(required + (random.nextBoolean() ? -change : change), 0, 10);
            }
        }
        State next = new State(elapsed, completion, checked || old.nextCheck == 1 ? INTERVAL : old.nextCheck - 1, required, random.state);
        return new Step(next, checked, good, completion >= rules.requiredCompletion ? Outcome.SUCCESS
                : elapsed >= rules.deadline() ? Outcome.FAILURE : Outcome.RUNNING);
    }
    // java.util.Random's original distribution, with explicit state so a reload cannot reroll the beam.
    private static final class PersistedRandom extends Random {
        private long state;
        PersistedRandom(long state) { super(0); this.state = state & RANDOM_MASK; }
        @Override protected int next(int bits) { state = (state * 0x5DEECE66DL + 0xBL) & RANDOM_MASK; return (int) (state >>> (48 - bits)); }
    }
    private ReactionCycle() {}
}
