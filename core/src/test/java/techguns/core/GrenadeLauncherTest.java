package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrenadeLauncherTest {
    @Test void partialReloadConsumesOnlyMissingIndividualRounds() {
        var gun=Weapons.definition("grenadelauncher");
        assertFalse(gun.automatic());assertEquals(ProjectileKind.GRENADE_40MM,gun.projectile());
        var full=Magazine.plan(gun,2,10,false);assertEquals(6,full.rounds());assertEquals(4,full.consumedItems());
        var partial=Magazine.plan(gun,2,1,false);assertEquals(3,partial.rounds());assertEquals(1,partial.consumedItems());
        var creative=Magazine.plan(gun,2,0,true);assertEquals(6,creative.rounds());assertEquals(0,creative.consumedItems());
    }
    @Test void drumUsesFiveTickRecoilAndReturnsToEquivalentSixtyDegreePose() {
        assertEquals(0,LauncherAnimation.drumDegrees(-1,10,0));assertEquals(0,LauncherAnimation.drumDegrees(10,9,0));
        assertEquals(0,LauncherAnimation.drumDegrees(10,10,0));assertEquals(30,LauncherAnimation.drumDegrees(10,12,.5f));
        assertEquals(54,LauncherAnimation.drumDegrees(10,14,.5f));assertEquals(0,LauncherAnimation.drumDegrees(10,15,0));
    }
}
