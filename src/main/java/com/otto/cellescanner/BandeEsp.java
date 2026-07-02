package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bande ESP addon: draws players in your bande as a "chams" - their real model,
 * visible through walls and tinted green - instead of a box, so you see their
 * skin/shape and can pick them out anywhere. Membership comes from the manual
 * name list (CelleConfig.bandeMembers), optionally plus anyone sharing your
 * bande tag when bandeAutoTeam is on.
 *
 * It works by tweaking GL state around each bande player's normal render
 * (RenderLivingEvent Pre/Post): depth off (through walls) + lighting off so the
 * green tint actually shows on the skin.
 */
public class BandeEsp {

    // Set in Pre, cleared in Post - Pre/Post are paired per entity on the single
    // render thread, so this correctly scopes the state change to one player.
    private boolean active = false;

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre<EntityLivingBase> event) {
        active = false;
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.bandeEspEnabled || !(event.entity instanceof EntityPlayer)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Don't apply during GUI entity rendering (e.g. the Player Info model) -
        // RenderLivingEvent fires there too, and chams state would ruin it.
        if (mc.currentScreen != null || mc.thePlayer == null) {
            return;
        }
        EntityPlayer p = (EntityPlayer) event.entity;
        if (p == mc.thePlayer || !isBande(mc, p)) {
            return;
        }
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.color(0.45f, 1.0f, 0.55f, 1.0f);
        active = true;
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (!active) {
            return;
        }
        active = false;
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
    }

    private boolean isBande(Minecraft mc, EntityPlayer p) {
        if (CelleScannerMod.config.isBandeMember(p.getName())) {
            return true;
        }
        if (CelleScannerMod.config.bandeAutoTeam) {
            String mine = bandeTag(mc.thePlayer);
            String theirs = bandeTag(p);
            if (mine != null && mine.equals(theirs)) {
                return true;
            }
        }
        return false;
    }

    private static String bandeTag(EntityPlayer player) {
        try {
            Team t = player.getTeam();
            if (t == null) {
                return null;
            }
            String tag = EnumChatFormatting.getTextWithoutFormattingCodes(t.formatString("")).trim();
            return tag.isEmpty() ? null : tag;
        } catch (Throwable e) {
            return null;
        }
    }
}
