package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import java.util.UUID;

/**
 * Nametag ESP: draws player names through walls and keeps them readable at a
 * distance. Also carries the LabyMod badge and VoiceChat speaker status.
 *
 * A distant nametag drawn at its normal size collapses into an unreadable
 * smudge, so the tag grows with distance and never shrinks below normal. How
 * fast it grows is the scale divisor in the settings.
 *
 * Names are tinted by who the player is, which is the part that makes this
 * useful in a crowd: bande and friends one colour, vagter another, everyone
 * else a third, sharing the Bande ESP palette so the two addons agree.
 */
public class PlayerEsp {

    @SubscribeEvent
    public void onRenderNameplate(RenderLivingEvent.Specials.Pre event) {
        if (!MassiveOsFreakyAddons.config.playerEspEnabled) {
            return;
        }
        if (!(event.entity instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (player == mc.thePlayer) {
            return; // Don't render a tag above ourselves
        }
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (mc.thePlayer == null) {
            return;
        }

        double dist = player.getDistanceToEntity(mc.thePlayer);
        int maxDist = cfg.nameEspMaxDistance;
        if (maxDist > 0 && dist > maxDist) {
            // Past the limit, leave the vanilla tag alone rather than hiding the
            // player entirely: cancelling here would remove their name outright.
            return;
        }
        if (Boolean.TRUE.equals(cfg.nameEspOnlyKnown) && relationOf(mc, player) == REL_OTHER) {
            return;
        }

        // Cancel vanilla nameplate rendering
        event.setCanceled(true);

        renderCustomNametag(player, event.x, event.y, event.z);
    }

    static final int REL_BANDE = 0;
    static final int REL_VAGT = 1;
    static final int REL_OTHER = 2;

    /**
     * Who this player is to you. Shares the Bande ESP's idea of bande, friends
     * and vagter so the two addons never disagree about someone's colour.
     */
    static int relationOf(Minecraft mc, EntityPlayer p) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || p == null || p.getName() == null) {
            return REL_OTHER;
        }
        String name = p.getName();
        if (cfg.isBandeMember(name)) {
            return REL_BANDE;
        }
        if (cfg.friendsList != null) {
            for (String f : cfg.friendsList) {
                if (f != null && f.equalsIgnoreCase(name)) {
                    return REL_BANDE;
                }
            }
        }
        if (VagtRoster.contains(name)) {
            return REL_VAGT;
        }
        return REL_OTHER;
    }

    /** Tag colour for a player, or plain white when colouring is switched off. */
    private static int nameColor(Minecraft mc, EntityPlayer p) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !Boolean.TRUE.equals(cfg.nameEspColorByRelation)) {
            return -1;
        }
        switch (relationOf(mc, p)) {
            case REL_BANDE: return 0xFF000000 | cfg.espColorBande;
            case REL_VAGT:  return 0xFF000000 | cfg.espColorVagt;
            default:        return 0xFF000000 | cfg.espColorOther;
        }
    }

    private void renderCustomNametag(EntityPlayer player, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fontRenderer = mc.fontRendererObj;
        RenderManager renderManager = mc.getRenderManager();

        String name = player.getDisplayName().getFormattedText();
        UUID uuid = player.getUniqueID();

        float distance = player.getDistanceToEntity(mc.thePlayer);
        // Grows with distance and never shrinks below normal, so a far-off name
        // stays legible. Capped, because without a ceiling a name across the map
        // covers the screen.
        int divisor = Math.max(2, MassiveOsFreakyAddons.config.nameEspScaleDivisor);
        float scaleMultiplier = Math.max(1.0F, Math.min(distance / divisor, 6.0F));
        float f = 1.6F * scaleMultiplier;
        float f1 = 0.016666668F * f;

        GlStateManager.pushMatrix();
        // Translate to the player's head height + offset
        GlStateManager.translate((float) x, (float) y + player.height + 0.5F, (float) z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        // Rotate to always face the camera
        GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-f1, -f1, f1);

        // Through walls is the whole point of an ESP tag, but it is a switch:
        // with it off these behave like vanilla tags and stay hidden behind
        // geometry, while keeping the sizing and colouring.
        boolean through = Boolean.TRUE.equals(MassiveOsFreakyAddons.config.nameEspThroughWalls);
        if (through) {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        // Force full white/opaque color states to override vanilla sneaking translucency
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Detect LabyMod status and VoiceChat addon status
        boolean isLaby = isLabyModUser(uuid);
        Object voiceUser = getVoiceUser(uuid);
        boolean hasVoice = (voiceUser != null);

        int leftOffset = isLaby ? 11 : 2;
        int rightOffset = hasVoice ? 11 : 2;
        int width = fontRenderer.getStringWidth(name) / 2;

        // Draw translucent dark background box behind text and icons (0.5F opacity for better contrast)
        GlStateManager.disableTexture2D();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos((double) (-width - leftOffset), -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        worldrenderer.pos((double) (-width - leftOffset), 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        worldrenderer.pos((double) (width + rightOffset), 8.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        worldrenderer.pos((double) (width + rightOffset), -1.0D, 0.0D).color(0.0F, 0.0F, 0.0F, 0.5F).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();

        // Draw LabyMod blue wolf head logo on the left if applicable
        if (isLaby) {
            // Bind labymod.png which contains the wolf head sprite sheet
            mc.getTextureManager().bindTexture(new ResourceLocation("labymod", "themes/vanilla/textures/labymod.png"));
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            // Render only the big blue wolf head located on the right half (U from 0.5 to 1.0)
            drawTexturedQuad(-width - 10, 0, 8, 8, 0.5D, 1.0D, 0.0D, 1.0D);
        }

        // Draw VoiceChat status badge on the right if applicable
        if (hasVoice) {
            mc.getTextureManager().bindTexture(new ResourceLocation("voicechat", "textures/tag.png"));
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            // Determine V texture coordinates based on the player's voice status
            double vMin = 0.0D;
            double vMax = 0.125D; // Default: Grey Speaker (idle/listening)
            try {
                boolean isCommunicating = (boolean) voiceUser.getClass().getMethod("isCommunicating").invoke(voiceUser);
                boolean isMuted = (boolean) voiceUser.getClass().getMethod("isMuted").invoke(voiceUser);
                if (isCommunicating) {
                    vMin = 0.125D;
                    vMax = 0.250D; // Green Speaker (speaking)
                } else if (isMuted) {
                    vMin = 0.375D;
                    vMax = 0.500D; // Grey Speaker with red diagonal line (muted input)
                }
            } catch (Throwable t) {
                // Keep default idle coordinates if reflection fails
            }

            drawTexturedQuad(width + 2, 0, 8, 8, 0.0D, 1.0D, vMin, vMax);
        }

        // Draw name text with shadow for extra readability
        fontRenderer.drawStringWithShadow(name, -width, 0, nameColor(mc, player));

        // Always restore, whether or not it was turned off above.
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void drawTexturedQuad(double x, double y, double width, double height, double uMin, double uMax, double vMin, double vMax) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldrenderer.pos(x, y + height, 0.0D).tex(uMin, vMax).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0D).tex(uMax, vMax).endVertex();
        worldrenderer.pos(x + width, y, 0.0D).tex(uMax, vMin).endVertex();
        worldrenderer.pos(x, y, 0.0D).tex(uMin, vMin).endVertex();
        tessellator.draw();
    }

    private boolean isLabyModUser(UUID uuid) {
        try {
            Class<?> labyClass = Class.forName("net.labymod.api.Laby");
            Object labyAPI = labyClass.getMethod("labyAPI").invoke(null);
            Object gameUserService = labyAPI.getClass().getMethod("gameUserService").invoke(labyAPI);
            Object gameUser = gameUserService.getClass().getMethod("gameUser", UUID.class).invoke(gameUserService, uuid);
            if (gameUser != null) {
                return (boolean) gameUser.getClass().getMethod("isUsingLabyMod").invoke(gameUser);
            }
        } catch (Throwable t) {
            // LabyMod not running / API not available
        }
        return false;
    }

    private Object getVoiceUser(UUID uuid) {
        try {
            Class<?> addonClass = Class.forName("net.labymod.addons.voicechat.core.VoiceChatAddon");
            Object instance = addonClass.getField("INSTANCE").get(null);
            if (instance != null) {
                java.lang.reflect.Field field = addonClass.getDeclaredField("voiceUserRegistry");
                field.setAccessible(true);
                Object registry = field.get(instance);
                if (registry != null) {
                    return registry.getClass().getMethod("get", UUID.class).invoke(registry, uuid);
                }
            }
        } catch (Throwable t) {
            // Voicechat addon not loaded
        }
        return null;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!MassiveOsFreakyAddons.config.playerEspEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        Entity viewer = mc.thePlayer;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.0f);

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) {
                continue;
            }

            // Exclude bande members so they are not double-boxed (BandeEsp renders them green)
            if (MassiveOsFreakyAddons.config.bandeEspEnabled && MassiveOsFreakyAddons.config.isBandeMember(p.getName())) {
                continue;
            }

            // Draw orange bounding box around normal players
            drawBox(p, partialTicks, 1.0F, 0.6F, 0.0F);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawBox(EntityPlayer p, float partialTicks, float r, float g, float b) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;

        double w = p.width / 2.0 + 0.06;
        double minX = x - w;
        double maxX = x + w;
        double minY = y - 0.06;
        double maxY = y + p.height + 0.06;
        double minZ = z - w;
        double maxZ = z + w;

        GlStateManager.color(r, g, b, 0.8f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();
    }
}
