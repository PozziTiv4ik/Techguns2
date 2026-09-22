package techguns.modern.npc.spawner;

/** Shared ownership contract for Monster and Spider based NPCs. */
public interface SpawnerLinked {
    SpawnerLifecycle spawnerLifecycle();
    default SpawnerLink spawnerLink() { return spawnerLifecycle().link(); }
    default boolean hasSpawnerOrigin() { return spawnerLink()!=null; }
    default void bindSpawner(SpawnerLink link) { spawnerLifecycle().bind(link); }
}
