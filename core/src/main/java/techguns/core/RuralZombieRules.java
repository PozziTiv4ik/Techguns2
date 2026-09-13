package techguns.core;

import java.util.List;

/** Original ZombieFarmer/ZombieMiner rolls and GenericNPCUndead sunlight probability. */
public final class RuralZombieRules {
    public enum Kind {
        FARMER("zombiefarmer",18,3,List.of("minecraft:wooden_hoe","minecraft:iron_hoe","minecraft:stone_hoe","techguns:handcannon")),
        MINER("zombieminer",20,4,List.of("minecraft:stone_pickaxe","minecraft:iron_pickaxe","techguns:handcannon"));
        private final String id;
        private final int health, attack;
        private final List<String> weapons;
        Kind(String id,int health,int attack,List<String> weapons) { this.id=id; this.health=health; this.attack=attack; this.weapons=weapons; }
        public String id() { return id; }
        public int health() { return health; }
        public int attack() { return attack; }
        public int weaponCount() { return weapons.size(); }
        public String weapon(int roll) {
            if(roll<0 || roll>=weapons.size()) throw new IllegalArgumentException("Roll outside source weapon table");
            return weapons.get(roll);
        }
    }
    public static boolean armor(Kind kind,ArmorSlot slot,double roll) {
        if(!Double.isFinite(roll) || roll<0 || roll>=1) throw new IllegalArgumentException("Expected unit random draw");
        if(slot==ArmorSlot.HEAD) return kind==Kind.MINER;
        return kind==Kind.FARMER && slot==ArmorSlot.CHEST || roll<=.5;
    }
    public static boolean sunIgnites(float brightness,float roll) {
        return UndeadRules.sunIgnites(brightness,roll);
    }
    private RuralZombieRules() {}
}
