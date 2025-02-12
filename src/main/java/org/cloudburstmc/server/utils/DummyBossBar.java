package org.cloudburstmc.server.utils;

import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.packet.*;
import org.cloudburstmc.api.entity.Attribute;
import org.cloudburstmc.api.entity.EntityTypes;
import org.cloudburstmc.server.network.NetworkUtils;
import org.cloudburstmc.server.player.CloudPlayer;

import java.util.concurrent.ThreadLocalRandom;

import static com.nukkitx.protocol.bedrock.data.entity.EntityData.*;

/**
 * DummyBossBar
 * ===============
 * author: boybook
 * Nukkit Project
 * ===============
 */
public class DummyBossBar {

    private final CloudPlayer player;
    private final long bossBarId;

    private String text;
    private float length;
    private BossBarColor color;

    private DummyBossBar(Builder builder) {
        this.player = builder.player;
        this.bossBarId = builder.bossBarId;
        this.text = builder.text;
        this.length = builder.length;
        this.color = builder.color;
    }

    public static class Builder {

        private final CloudPlayer player;
        private final long bossBarId;

        private String text = "";
        private float length = 100;
        private BossBarColor color = null;

        public Builder(CloudPlayer player) {
            this.player = player;
            this.bossBarId = 1095216660480L + ThreadLocalRandom.current().nextLong(0, 0x7fffffffL);
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder length(float length) {
            if (length >= 0 && length <= 100) this.length = length;
            return this;
        }

        public Builder color(BossBarColor color) {
            this.color = color;
            return this;
        }

        public DummyBossBar build() {
            return new DummyBossBar(this);
        }
    }

    public CloudPlayer getPlayer() {
        return player;
    }

    public long getBossBarId() {
        return bossBarId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        if (!this.text.equals(text)) {
            this.text = text;
            this.updateBossEntityNameTag();
            this.sendSetBossBarTitle();
        }
    }

    public float getLength() {
        return length;
    }

    public void setLength(float length) {
        if (this.length != length) {
            this.length = length;
            this.sendAttributes();
            this.sendSetBossBarLength();
        }
    }

    public void setColor(BossBarColor color) {
        if (this.color == null || !this.color.equals(color)) {
            this.color = color;
            this.sendSetBossBarTexture();
        }
    }

    public BossBarColor getColor() {
        return this.color;
    }

    private void createBossEntity() {
        AddEntityPacket pkAdd = new AddEntityPacket();
        pkAdd.setIdentifier(EntityTypes.CREEPER.getIdentifier().toString());
        pkAdd.setUniqueEntityId(bossBarId);
        pkAdd.setRuntimeEntityId(bossBarId);
        pkAdd.setPosition(Vector3f.from(player.getX(), -10, player.getZ()));
        pkAdd.setMotion(Vector3f.ZERO);
        pkAdd.getMetadata()
                // Default Metadata tags
                .putShort(AIR_SUPPLY, 400)
                .putShort(MAX_AIR_SUPPLY, 400)
                .putLong(LEASH_HOLDER_EID, -1)
                .putString(NAMETAG, text) // Set the entity name
                .putFloat(SCALE, 0); // And make it invisible

        this.player.sendPacket(pkAdd);
    }

    private void sendAttributes() {
        UpdateAttributesPacket pkAttributes = new UpdateAttributesPacket();
        pkAttributes.setRuntimeEntityId(bossBarId);
        Attribute attr = Attribute.getAttribute(Attribute.MAX_HEALTH);
        attr.setMaxValue(100); // Max value - We need to change the max value first, or else the "setValue" will return a IllegalArgumentException
        attr.setValue(length); // Entity health
        pkAttributes.getAttributes().add(NetworkUtils.attributeToNetwork(attr));
        this.player.sendPacket(pkAttributes);
    }

    private void sendShowBossBar() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.setBossUniqueEntityId(bossBarId);
        pkBoss.setPlayerUniqueEntityId(player.getUniqueId());
        pkBoss.setAction(BossEventPacket.Action.CREATE);
        pkBoss.setTitle(text);
        pkBoss.setHealthPercentage(this.length / 100);
        this.player.sendPacket(pkBoss);
    }

    private void sendHideBossBar() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.setBossUniqueEntityId(bossBarId);
        pkBoss.setPlayerUniqueEntityId(player.getUniqueId());
        pkBoss.setAction(BossEventPacket.Action.REMOVE);
        player.sendPacket(pkBoss);
    }

    private void sendSetBossBarTexture() {
        BossEventPacket pk = new BossEventPacket();
        pk.setBossUniqueEntityId(this.bossBarId);
        pk.setPlayerUniqueEntityId(this.player.getUniqueId());
        pk.setAction(BossEventPacket.Action.UPDATE_STYLE);
        pk.setColor(color.ordinal());
        player.sendPacket(pk);
    }

    private void sendSetBossBarTitle() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.setBossUniqueEntityId(this.bossBarId);
        pkBoss.setPlayerUniqueEntityId(this.player.getUniqueId());
        pkBoss.setAction(BossEventPacket.Action.UPDATE_NAME);
        pkBoss.setTitle(text);
        pkBoss.setHealthPercentage(this.length / 100);
        player.sendPacket(pkBoss);
    }

    private void sendSetBossBarLength() {
        BossEventPacket pkBoss = new BossEventPacket();
        pkBoss.setBossUniqueEntityId(this.bossBarId);
        pkBoss.setPlayerUniqueEntityId(this.player.getUniqueId());
        pkBoss.setAction(BossEventPacket.Action.UPDATE_PERCENTAGE);
        pkBoss.setHealthPercentage(this.length / 100);
        player.sendPacket(pkBoss);
    }

    /**
     * Don't let the entity go too far from the player, or the BossBar will disappear.
     * Update boss entity's position when teleport and each 5s.
     */
    public void updateBossEntityPosition() {
        MoveEntityAbsolutePacket pk = new MoveEntityAbsolutePacket();
        pk.setRuntimeEntityId(this.bossBarId);
        pk.setPosition(Vector3f.from(this.player.getX(), -10, this.player.getZ()));
        pk.setRotation(Vector3f.ZERO);
        player.sendPacket(pk);
    }

    private void updateBossEntityNameTag() {
        SetEntityDataPacket pk = new SetEntityDataPacket();
        pk.setRuntimeEntityId(this.bossBarId);
        pk.getMetadata().putString(NAMETAG, this.text);
        player.sendPacket(pk);
    }

    private void removeBossEntity() {
        RemoveEntityPacket pkRemove = new RemoveEntityPacket();
        pkRemove.setUniqueEntityId(bossBarId);
        player.sendPacket(pkRemove);
    }

    public void create() {
        createBossEntity();
        sendAttributes();
        sendShowBossBar();
        sendSetBossBarLength();
        if (color != null) this.sendSetBossBarTexture();
    }

    /**
     * Once the player has teleported, resend Show BossBar
     */
    public void reshow() {
        updateBossEntityPosition();
        sendShowBossBar();
        sendSetBossBarLength();
    }

    public void destroy() {
        sendHideBossBar();
        removeBossEntity();
    }

    public enum BossBarColor {

        PINK,
        BLUE,
        RED,
        GREEN,
        YELLOW,
        PURPLE,
        WHITE
    }
}
