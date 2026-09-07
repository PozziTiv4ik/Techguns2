package techguns.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AimingTest {
    @Test void originalOpticsDriveFovAccuracyAndMuzzlePosition() {
        AimSpec m4 = Weapons.definition("m4").aim();
        assertEquals(52.5f, m4.applyFov(70));
        assertEquals(.75f, m4.accuracyMultiplier());
        assertTrue(m4.toggle());
        assertFalse(m4.centered());
        AimSpec as50 = Weapons.definition("as50").aim();
        assertEquals(24.5f, as50.applyFov(70));
        assertEquals(.125f, as50.accuracyMultiplier());
        assertTrue(as50.centered());
        assertFalse(Weapons.definition("pistol").aim().supported());
    }
    @Test void hudClampsStaleNetworkValuesAndTracksProgress() {
        WeaponDefinition lmg = Weapons.definition("lmg");
        assertEquals(new WeaponHud(100,100,100,0), WeaponHud.of(lmg,1000,1000));
        assertEquals(new WeaponHud(10,100,50,.5f), WeaponHud.of(lmg,10,50));
        assertFalse(WeaponHud.of(lmg,-10,-1).reloading());
    }
    @Test void invalidOpticalParametersFailEarly() {
        assertThrows(IllegalArgumentException.class, () -> new AimSpec(0,true,1,false));
        assertThrows(IllegalArgumentException.class, () -> new AimSpec(.75f,true,Float.NaN,false));
    }
}
