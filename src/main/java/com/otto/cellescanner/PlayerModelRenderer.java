package com.otto.cellescanner;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Reusable helper that renders a player's 3D model at an arbitrary screen position.
 * Extracted from GuiPlayerInfo so FlipCaseGui can reuse it without inheriting the full
 * info-screen machinery. All GL state is restored on exit; clip/scissor is applied so
 * the model can't bleed outside the requested cell box.
 */
public final class PlayerModelRenderer {

    static {
        try {
            net.minecraft.entity.EntityList.classToStringMapping.put(FakeSkinPlayer.class, "Player");
        } catch (Throwable ignored) {}
    }

    private PlayerModelRenderer() {
    }

    /**
     * Creates a fake entity for the given player name + skin entry.
     * Returns null if the skin is not yet ready or the world is unloaded.
     * Safe to call every frame - just returns null until the skin is fetched.
     */
    public static EntityOtherPlayerMP buildEntity(String username, SkinFetcher.Entry skin) {
        try {
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null) {
                return null;
            }
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("FlipCase:" + username).getBytes()),
                    username);

            // 1. Try to find player in the world entities (most reliable, guarantees skin is loaded)
            for (net.minecraft.entity.player.EntityPlayer p : world.playerEntities) {
                if (p.getName().equalsIgnoreCase(username)) {
                    if (p instanceof net.minecraft.client.entity.AbstractClientPlayer) {
                        net.minecraft.client.entity.AbstractClientPlayer acp = (net.minecraft.client.entity.AbstractClientPlayer) p;
                        ResourceLocation loc = acp.getLocationSkin();
                        if (loc != null) {
                            boolean slim = "slim".equals(acp.getSkinType());
                            return new FakeSkinPlayer(world, profile, loc, slim);
                        }
                    }
                }
            }

            // 2. Try to find player in the client tablist/connection list
            net.minecraft.client.network.NetHandlerPlayClient connection = Minecraft.getMinecraft().getNetHandler();
            if (connection != null) {
                for (net.minecraft.client.network.NetworkPlayerInfo info : connection.getPlayerInfoMap()) {
                    if (info.getGameProfile().getName().equalsIgnoreCase(username)) {
                        ResourceLocation loc = info.getLocationSkin();
                        if (loc != null) {
                            boolean slim = "slim".equals(info.getSkinType());
                            return new FakeSkinPlayer(world, profile, loc, slim);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (skin == null || skin.location == null) {
            return null;
        }
        try {
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null) {
                return null;
            }
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("FlipCase:" + username).getBytes()),
                    username);
            return new FakeSkinPlayer(world, profile, skin.location, skin.slim);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Draws the given entity's 3D model centred inside the box [x, y, w, h].
     * mouseX/mouseY drive the head-turn; pass (x+w/2, y+h/3) for a neutral pose.
     * Returns false if rendering failed (broken GL state recovered).
     *
     * @param fixedYaw if non-null, the entity yaw is temporarily set to this value
     *                 (degrees, 0 = facing south) instead of tracking the mouse.
     */
    public static boolean draw(Minecraft mc,
                               EntityOtherPlayerMP entity,
                               int x, int y, int w, int h,
                               int mouseX, int mouseY,
                               Float fixedYaw) {
        if (entity == null || mc.theWorld == null) {
            return false;
        }
        try {
            boolean prevHide = mc.gameSettings.hideGUI;
            mc.gameSettings.hideGUI = true;
            float prevYaw   = entity.rotationYaw;
            float prevYawH  = entity.renderYawOffset;
            try {
                if (fixedYaw != null) {
                    entity.rotationYaw    = fixedYaw;
                    entity.renderYawOffset = fixedYaw;
                }
                int cx   = x + w / 2;
                int feet = y + h - 8;
                int scale = (int) (h / 2.5);

                GlStateManager.color(1f, 1f, 1f, 1f);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
                GuiInventory.drawEntityOnScreen(cx, feet, scale,
                        cx - mouseX, (y + h / 3) - mouseY, entity);
            } finally {
                entity.rotationYaw     = prevYaw;
                entity.renderYawOffset = prevYawH;
                mc.gameSettings.hideGUI = prevHide;
            }
            return true;
        } catch (Throwable t) {
            GlStateManager.color(1f, 1f, 1f, 1f);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    static final class FakeSkinPlayer extends EntityOtherPlayerMP {
        private final ResourceLocation skinLoc;
        private final boolean slim;

        FakeSkinPlayer(World world, GameProfile profile, ResourceLocation skinLoc, boolean slim) {
            super(world, profile);
            this.skinLoc = skinLoc;
            this.slim    = slim;
            try {
                this.getDataWatcher().updateObject(10, Byte.valueOf((byte) 127));
            } catch (Throwable ignored) {}
        }

        @Override public ResourceLocation getLocationSkin() { return skinLoc; }
        @Override public boolean hasSkin()                  { return skinLoc != null; }
        @Override public String getSkinType()               { return slim ? "slim" : "default"; }
    }
}
