package org.cloudburstmc.server.item;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtMapBuilder;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.ToString;
import org.cloudburstmc.api.block.BlockTypes;
import org.cloudburstmc.api.enchantment.EnchantmentInstance;
import org.cloudburstmc.api.enchantment.EnchantmentType;
import org.cloudburstmc.api.item.ItemStack;
import org.cloudburstmc.api.item.ItemStackBuilder;
import org.cloudburstmc.api.item.ItemType;
import org.cloudburstmc.api.item.behavior.ItemBehavior;
import org.cloudburstmc.api.util.Identifier;
import org.cloudburstmc.server.registry.CloudItemRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.ParametersAreNullableByDefault;
import javax.annotation.concurrent.Immutable;
import java.util.*;

@ToString
@Immutable
@ParametersAreNonnullByDefault
public class CloudItemStack implements ItemStack {

    protected static final Object NONE_VALUE = new Object();

    protected volatile Identifier id;
    protected final ItemType type;
    protected final int amount;
    protected final String itemName;
    protected final List<String> itemLore;
    protected final ImmutableMap<EnchantmentType, EnchantmentInstance> enchantments;
    protected final Set<Identifier> canDestroy;
    protected final Set<Identifier> canPlaceOn;

    protected volatile Map<Class<?>, Object> data;
    protected volatile NbtMap nbt;
    protected volatile NbtMap dataTag;
    protected volatile ItemData networkData;
    protected volatile int stackNetId = -1;

    public CloudItemStack(Identifier id, ItemType type) {
        this(id, type, 1, null, null, null, null, null, null, null, null, null, -1);
    }

    public CloudItemStack(Identifier id, ItemType type, int amount) {
        this(id, type, amount, null, null, null, null, null, null, null, null, null, -1);
    }

    @ParametersAreNullableByDefault
    public CloudItemStack(
            Identifier id,
            ItemType type,
            int amount,
            String itemName,
            List<String> itemLore,
            Map<EnchantmentType, EnchantmentInstance> enchantments,
            Collection<Identifier> canDestroy,
            Collection<Identifier> canPlaceOn,
            Map<Class<?>, Object> data,
            NbtMap nbt,
            NbtMap dataTag,
            ItemData networkData,
            int netId
    ) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.itemName = itemName;
        this.itemLore = itemLore == null ? ImmutableList.of() : ImmutableList.copyOf(itemLore);
        this.enchantments = enchantments == null ? ImmutableMap.of() : ImmutableMap.copyOf(enchantments);
        this.canDestroy = canDestroy == null ? ImmutableSet.of() : ImmutableSet.copyOf(canDestroy);
        this.canPlaceOn = canPlaceOn == null ? ImmutableSet.of() : ImmutableSet.copyOf(canPlaceOn);
        this.data = data == null ? new Reference2ReferenceOpenHashMap<>() : new Reference2ReferenceOpenHashMap<>(data);
        this.nbt = nbt;
        this.dataTag = dataTag;
        this.networkData = networkData;

        if (netId == -1 && this.networkData != null) {
            this.stackNetId = this.networkData.getNetId();
        } else {
            this.stackNetId = netId;
        }
    }

    public Identifier getId() {
        if (id == null) {
            synchronized (type) {
                if (id == null) {
                    id = Identifier.fromString(getNbt().getString("Name"));
                }
            }
        }

        return id;
    }

    @Override
    public ItemType getType() {
        return type;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public boolean isNull() {
        return this.getType() == BlockTypes.AIR;
    }

    public Map<Class<?>, Object> getMetadataMap() {
        return this.data;
    }

    @Override
    public <T> boolean hasMetadata(Class<T> metadataClass) {
        return this.data.containsKey(metadataClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getMetadata(Class<T> metadataClass, T defaultValue) {
        T value = (T) this.data.getOrDefault(metadataClass, NONE_VALUE);

        if (value == NONE_VALUE) {
            synchronized (this.canPlaceOn) {
                value = (T) this.data.getOrDefault(metadataClass, NONE_VALUE);

                if (value == NONE_VALUE) {
                    var serializer = CloudItemRegistry.get().getSerializer(metadataClass);
                    if (serializer != null) {
                        value = (T) serializer.deserialize(this.id, getNbt(true), getDataTag());
                    }

                    Map<Class<?>, Object> copy = new Reference2ReferenceOpenHashMap<>(this.data);
                    copy.put(metadataClass, value);

                    this.data = copy;
                }
            }
        }

        return value == null ? defaultValue : value;
    }

    public ImmutableMap<Class<?>, Object> getData() {
        return ImmutableMap.copyOf(data);
    }

    @Override
    public String getName() {
        return itemName;
    }

    @Override
    public List<String> getLore() {
        return itemLore;
    }

    @Override
    public Map<EnchantmentType, EnchantmentInstance> getEnchantments() {
        return enchantments;
    }

    @Override
    public Collection<Identifier> getCanDestroy() {
        return this.canDestroy;
    }

    @Override
    public Collection<Identifier> getCanPlaceOn() {
        return this.canPlaceOn;
    }

    @Override
    public ItemStackBuilder toBuilder() {
        return new CloudItemStackBuilder(this);
    }

    public boolean isBlock() {
        return false;
    }

    public NbtMap getNbt() {
        return getNbt(true);
    }

    public NbtMap getNbt(boolean serialize) {
        if (nbt == null) {
            synchronized (itemLore) {
                if (!serialize) {
                    return nbt;
                }

                if (nbt == null) {
                    NbtMapBuilder builder = NbtMap.builder();
                    CloudItemRegistry.get().getSerializer(this.type).serialize(this, builder);
                    this.nbt = builder.build();
                }
            }
        }

        return this.nbt;
    }

    public NbtMap getDataTag() {
        if (dataTag == null) {
            getNbt();
            if (nbt.containsKey("tag")) {
                dataTag = nbt.getCompound("tag");
            } else {
                dataTag = NbtMap.EMPTY;
            }
        }

        return dataTag;
    }

    public ItemData getNetworkData() {
        if (networkData == null) {
            synchronized (this) {
                if (networkData == null) {
                    this.networkData = ItemUtils.toNetwork(this);
                }
            }
        }
        return networkData;
    }

    public int getStackNetworkId() {
        return this.stackNetId;
    }

    @Override
    public boolean hasTag() {
        if (this.nbt != null) {
            var tag = this.nbt.getCompound("tag");
            if (tag != null && !tag.isEmpty()) {
                return true;
            }
        }

        return !(this.data.isEmpty() || this.itemLore.isEmpty() || this.itemName == null || this.enchantments.isEmpty() || this.canPlaceOn.isEmpty() || this.canDestroy.isEmpty());
    }

    @Override
    public ItemStack withData(Object data) {
        Preconditions.checkNotNull(data, "data");
        return withData(data.getClass(), data);
    }

    @Override
    public ItemStack withData(Class<?> metadataClass, Object data) {
        return this.toBuilder().itemData(metadataClass, data).build();
    }

    @Override
    public ItemBehavior getBehavior() {
        return CloudItemRegistry.get().getBehavior(this.type);
    }

    @Override
    public boolean isMergeable(@Nonnull ItemStack other) {
        return equals(other);
    }

    @Override
    public boolean equals(@Nullable ItemStack item) {
        return equals(item, false, hasTag());
    }

    @Override
    public boolean equals(@Nullable ItemStack other, boolean checkAmount, boolean checkData) {
        if (this == other) return true;
        if (other == null) return false;
        CloudItemStack that = (CloudItemStack) other;

        return this.type == that.type && (!checkAmount || this.amount == that.amount) &&
                (!checkData || (Objects.equals(this.getDataTag(), that.getDataTag())
                        && (this.getNbt().getShort("Damage") == 0x7FFF  // Crafting wildcards
                        || that.getNbt().getShort("Damage") == 0x7FFF
                        || this.getNbt().getShort("Damage") == that.getNbt().getShort("Damage"))));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItemStack)) return false;
        return equals((ItemStack) o, true, true);
    }

    public int hashCode() {
        return Objects.hash(type, amount, itemName, itemLore, enchantments);
    }
}
