package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReactionCycleTest {
    private static final ReactionCycle.Rules TITANIUM=new ReactionCycle.Rules(2,1,5,0,3,0,25000);
    private static final ReactionCycle.Rules URANIUM=new ReactionCycle.Rules(5,4,7,0,3,0,250000);
    @Test void titaniumPaysAndCompletesAtSixtyTicksRatherThanEachGameTick() {
        var state=ReactionCycle.State.start(TITANIUM,12);
        for(int tick=1;tick<=60;tick++) {
            assertEquals(tick==60,state.checkDue(TITANIUM));
            var step=ReactionCycle.advance(TITANIUM,state,5,3,true,true); state=step.state();
            assertEquals(tick==60,step.checked()); assertEquals(tick==60 ? ReactionCycle.Outcome.SUCCESS : ReactionCycle.Outcome.RUNNING,step.outcome());
        }
        assertEquals(1,state.completion());
    }
    @Test void uraniumHasFourChecksAndNoFifthCheckAtItsDeadline() {
        var state=ReactionCycle.State.start(URANIUM,2); int checks=0;
        for(int tick=1;tick<=300;tick++) {
            var step=ReactionCycle.advance(URANIUM,state,tick==60 ? 6 : 7,3,true,true); state=step.state();
            if(step.checked()) checks++;
            assertEquals(tick==300 ? ReactionCycle.Outcome.FAILURE : ReactionCycle.Outcome.RUNNING,step.outcome());
        }
        assertEquals(4,checks); assertEquals(3,state.completion());
    }
    @Test void powerFailureUsesUpReactionTime() {
        var state=ReactionCycle.State.start(TITANIUM,0);
        ReactionCycle.Step step=null;
        for(int tick=0;tick<120;tick++) { step=ReactionCycle.advance(TITANIUM,state,5,3,true,false); state=step.state(); }
        assertEquals(ReactionCycle.Outcome.FAILURE,step.outcome()); assertEquals(0,state.completion());
    }
    @Test void removingFocusFailsAtTheNextCheckAndErasesPartialCompletion() {
        var state=ReactionCycle.State.start(URANIUM,6);
        for(int tick=1;tick<=120;tick++) {
            var step=ReactionCycle.advance(URANIUM,state,7,3,tick<120,true); state=step.state();
            if(tick==120) assertEquals(ReactionCycle.Outcome.FAILURE,step.outcome());
        }
        assertEquals(300,state.elapsed()); assertEquals(0,state.completion());
    }
    @Test void wrongLiquidSettingDoesNotCompleteAReaction() {
        var state=new ReactionCycle.State(59,0,1,5,1);
        var step=ReactionCycle.advance(TITANIUM,state,5,2,true,true);
        assertTrue(step.checked()); assertFalse(step.good()); assertEquals(0,step.state().completion());
    }
    @Test void savedRandomStateMatchesTheOriginalJavaRandomDistribution() {
        var rules=new ReactionCycle.Rules(100,99,8,2,4,1,500000);
        long seed=178134;
        var random=new java.util.Random(seed); var state=ReactionCycle.State.start(rules,seed); int expected=8;
        for(int check=0;check<75;check++) {
            random.nextFloat(); int change=1+random.nextInt(2);
            expected=Math.clamp(expected+(random.nextBoolean() ? -change : change),0,10);
            for(int tick=0;tick<60;tick++) state=ReactionCycle.advance(rules,state,0,4,true,true).state();
            state=new ReactionCycle.State(state.elapsed(),state.completion(),state.nextCheck(),state.requiredIntensity(),state.randomState());
            assertEquals(expected,state.requiredIntensity());
        }
    }
    @Test void invalidOrImpossibleReactionsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new ReactionCycle.Rules(5,5,7,0,3,0,250000));
        assertThrows(IllegalArgumentException.class,() -> new ReactionCycle.Rules(5,4,11,0,3,0,250000));
        assertThrows(IllegalArgumentException.class,() -> new ReactionCycle.Rules(5,4,7,0,3,Double.NaN,250000));
        assertThrows(IllegalArgumentException.class,() -> new ReactionCycle.Rules(5,4,7,0,3,0,1000001));
    }
}
