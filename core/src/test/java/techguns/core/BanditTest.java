package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BanditTest {
    @Test void allSixSourceDrawsIncludeTheReachableDefaultRifle() {
        String[] expected={"pistol","ak47","sawedoff","thompson","revolver","boltaction"};
        for(int i=0;i<6;i++) assertEquals(expected[i],BanditRules.weapon(i));
        assertThrows(IllegalArgumentException.class,() -> BanditRules.weapon(-1)); assertThrows(IllegalArgumentException.class,() -> BanditRules.weapon(6));
    }
    @Test void optionalMaskUsesTheInclusiveSourceBoundary() {
        assertTrue(BanditRules.helmet(0)); assertTrue(BanditRules.helmet(.5)); assertFalse(BanditRules.helmet(Math.nextUp(.5)));
        for(double invalid:new double[]{-1,1,Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class,() -> BanditRules.helmet(invalid));
    }
    @Test void lastEntryReceivesFortyNineTicketsOnlyAtDangerTwoOrAbove() {
        var weights=new OverworldSpawnRules.Weights(200,200,100,100,3,50);
        for(int danger=0;danger<=5;danger++) {
            int count=0; for(int roll=0;roll<weights.total(danger);roll++) if(weights.choose(danger,roll)==OverworldSpawnRules.Choice.BANDIT) count++;
            assertEquals(danger<2?0:49,count);
        }
    }
    @Test void boundaryTicketStillBelongsToPsychoSteve() {
        var weights=new OverworldSpawnRules.Weights(200,200,100,100,3,50);
        assertEquals(653,weights.total(2)); assertEquals(OverworldSpawnRules.Choice.PSYCHO_STEVE,weights.choose(2,603));
        assertEquals(OverworldSpawnRules.Choice.BANDIT,weights.choose(2,604)); assertEquals(OverworldSpawnRules.Choice.BANDIT,weights.choose(2,652));
    }
    @Test void customAndDisabledWeightsDoNotCreatePhantomTickets() {
        var only=new OverworldSpawnRules.Weights(0,0,0,0,0,50); assertEquals(0,only.total(1)); assertEquals(50,only.total(2));
        for(int roll=0;roll<50;roll++) assertEquals(OverworldSpawnRules.Choice.BANDIT,only.choose(2,roll));
        var disabled=new OverworldSpawnRules.Weights(200,200,100,100,3,0);
        for(int roll=0;roll<disabled.total(2);roll++) assertNotEquals(OverworldSpawnRules.Choice.BANDIT,disabled.choose(2,roll));
    }
}
