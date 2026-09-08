package techguns.modern.machine;

import net.minecraft.world.inventory.ContainerData;

/** Vanilla menu properties carry signed shorts; split each logical integer into two words. */
public final class SplitIntContainerData implements ContainerData {
    private final ContainerData values;
    public SplitIntContainerData(ContainerData values) { this.values = values; }
    @Override public int getCount() { return values.getCount() * 2; }
    @Override public int get(int index) { return (values.get(index / 2) >>> ((index & 1) * 16)) & 0xffff; }
    @Override public void set(int index, int value) {
        int shift = (index & 1) * 16;
        int previous = values.get(index / 2);
        values.set(index / 2, (previous & ~(0xffff << shift)) | ((value & 0xffff) << shift));
    }
}
