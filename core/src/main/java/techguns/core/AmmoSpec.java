package techguns.core;

import java.util.List;

/** Each component is consumed together; empty and loose returns keep their source index. */
public record AmmoSpec(List<Component> components, int bundlesPerMagazine, boolean individual) {
    public record Component(String item, String emptyItem, String looseItem) {
        public Component {
            if (item == null || item.isEmpty() || emptyItem == null || looseItem == null)
                throw new IllegalArgumentException("Invalid ammo component");
        }
    }
    public AmmoSpec {
        if (components == null || components.isEmpty() || bundlesPerMagazine < 0)
            throw new IllegalArgumentException("Invalid ammo definition");
        components = List.copyOf(components);
        if (components.stream().map(Component::item).distinct().count() != components.size())
            throw new IllegalArgumentException("Duplicate ammo input");
        if (individual && components.stream().anyMatch(c -> !c.emptyItem().isEmpty()))
            throw new IllegalArgumentException("Individual ammo cannot be a magazine");
    }
    public AmmoSpec(String item, String emptyItem, String looseItem, int bundlesPerMagazine, boolean individual) {
        this(List.of(new Component(item, emptyItem, looseItem)), bundlesPerMagazine, individual);
    }
    public String item() { return components.getFirst().item(); }
    public String emptyItem() { return components.getFirst().emptyItem(); }
    public String looseItem() { return components.getFirst().looseItem(); }
    public boolean magazine() { return components.stream().anyMatch(c -> !c.emptyItem().isEmpty()); }
}
