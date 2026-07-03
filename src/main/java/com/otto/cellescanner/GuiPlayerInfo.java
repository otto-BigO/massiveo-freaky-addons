package com.otto.cellescanner;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The player info menu (shift + right-click a player, or search by name): a
 * rotating 3D model on the left, and the player's bande, celle (from /ce info)
 * and armor with enchants on the right. Armor/skin/bande are captured on open;
 * the live entity is kept only to draw the model.
 */
public class GuiPlayerInfo extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int CARD_W = 330;
    private static final int CARD_H = 208;
    private static final int MODEL_W = 96;
    private static final int ROW_H = 20;
    private static final int INFO_W = CARD_W - MODEL_W - 32;

    private static final String[] SLOT_NAMES = {"Støvler", "Bukser", "Brystplade", "Hjelm"};

    private final boolean offline;
    private final String playerName;
    private final String rawName;
    private final String bande;
    private final ItemStack[] armor = new ItemStack[4];
    private final boolean slim;
    private ResourceLocation skin;         // for the flat head/body fallback
    private boolean modelBroken = false;
    // A stable fake entity carrying the captured skin (+ armor when online) - the
    // 3D model always renders this, never the live entity, so it persists even
    // after the player walks away or unloads.
    private EntityPlayer modelEntity;

    /** Online / loaded player: full data, model built from the captured skin + armor. */
    public GuiPlayerInfo(EntityPlayer target) {
        this.offline = false;
        this.rawName = target.getName();
        String name;
        try {
            name = target.getDisplayName() != null ? target.getDisplayName().getFormattedText() : target.getName();
        } catch (Throwable t) {
            name = target.getName();
        }
        this.playerName = name;
        this.bande = bandeOf(target);
        for (int i = 0; i < 4; i++) {
            ItemStack s = target.getCurrentArmor(i);
            armor[i] = s == null ? null : s.copy();
        }
        ResourceLocation sk = null;
        boolean sl = false;
        try {
            if (target instanceof AbstractClientPlayer) {
                AbstractClientPlayer acp = (AbstractClientPlayer) target;
                sk = acp.getLocationSkin();
                sl = "slim".equals(acp.getSkinType());
            }
        } catch (Throwable ignored) {
        }
        this.skin = sk;
        this.slim = sl;
        this.modelEntity = buildModel(sk, sl, armor);
    }

    /** Offline player (not loaded): skin fetched from Mojang, celle via /ce info, no live armor. */
    public GuiPlayerInfo(String username) {
        this.offline = true;
        this.rawName = username;
        this.playerName = username;
        this.bande = null;
        this.slim = false;
    }

    private static String bandeOf(EntityPlayer target) {
        try {
            Team t = target.getTeam();
            if (t == null) {
                return null;
            }
            String combo = EnumChatFormatting.getTextWithoutFormattingCodes(t.formatString("")).trim();
            return combo.isEmpty() ? null : combo;
        } catch (Throwable t) {
            return null;
        }
    }

    private int cardL() {
        return this.width / 2 - CARD_W / 2;
    }

    private int cardT() {
        return this.height / 2 - CARD_H / 2;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new StyledButton(ID_BACK, cardL(), cardT() + CARD_H + 6, CARD_W, 20, "Luk"));
        PlayerInfo.lookup(rawName);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Minecraft mc = Minecraft.getMinecraft();
        int cardL = cardL();
        int cardT = cardT();

        Style.panel(cardL, cardT, cardL + CARD_W, cardT + CARD_H);
        drawCenteredString(this.fontRendererObj, playerName, this.width / 2, cardT + 8, 0xFFFFFF);
        drawRect(cardL + 8, cardT + 20, cardL + CARD_W - 8, cardT + 21, Style.ACCENT);

        // Left: 3D model in its own panel.
        int mx0 = cardL + 8;
        int my0 = cardT + 26;
        int mh = CARD_H - 34;
        Style.panel(mx0, my0, mx0 + MODEL_W, my0 + mh);
        drawModel(mc, mx0, my0, MODEL_W, mh, mouseX, mouseY);

        // Right: info column.
        int x = mx0 + MODEL_W + 12;
        int y = cardT + 28;

        if (bande != null) {
            drawString(this.fontRendererObj, EnumChatFormatting.RED + "Bande: " + EnumChatFormatting.WHITE + bande, x, y, 0xFFFFFF);
        } else {
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + (offline ? "Bande: ukendt (offline)" : "Bande: ukendt"), x, y, 0xAAAAAA);
        }
        y += 15;

        y = drawCelle(x, y);
        y += 6;

        drawString(this.fontRendererObj, EnumChatFormatting.GREEN + "Rustning", x, y, 0x55FF55);
        y += 12;
        if (offline) {
            drawString(this.fontRendererObj, EnumChatFormatting.DARK_GRAY + "Ikke tilgængelig (offline)", x + 4, y, 0x777777);
        } else {
            drawArmor(mc, x, y);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawModel(Minecraft mc, int mx0, int my0, int mw, int mh, int mouseX, int mouseY) {
        // Offline: build the model lazily once the skin has been fetched.
        if (modelEntity == null && offline && !modelBroken) {
            SkinFetcher.Entry sk = SkinFetcher.get(rawName);
            if (sk.location == null) {
                String msg = "fejl".equals(sk.status) ? "Ingen skin fundet" : "Henter skin...";
                drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + msg, mx0 + mw / 2, my0 + mh / 2 - 4, 0xAAAAAA);
                return;
            }
            this.skin = sk.location;
            modelEntity = buildModel(sk.location, sk.slim, null);
        }

        if (modelEntity != null && !modelBroken
                && renderEntity3D(mc, modelEntity, mx0, my0, mw, mh, mouseX, mouseY)) {
            return;
        }

        // Fallbacks if the model couldn't build/render.
        if (offline && skin != null) {
            SkinFetcher.Entry sk = SkinFetcher.get(rawName);
            int s = Math.max(2, (mh - 20) / 32);
            drawSkinBody(mc, skin, mx0 + mw / 2, my0 + (mh - 32 * s) / 2, s, sk.slim, sk.legacy);
        } else {
            drawHead(mc, mx0 + mw / 2 - 16, my0 + 16, 32);
        }
    }

    /** Renders a living entity as the rotating 3D model in the panel. Returns false (and sets modelBroken) if it throws. */
    private boolean renderEntity3D(Minecraft mc, EntityLivingBase ent, int mx0, int my0, int mw, int mh, int mouseX, int mouseY) {
        try {
            ScaledResolution sr = new ScaledResolution(mc);
            int sf = sr.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(mx0 * sf, mc.displayHeight - (my0 + mh) * sf, mw * sf, mh * sf);
            boolean prevHide = mc.gameSettings.hideGUI;
            // hideGUI makes Minecraft.isGuiEnabled() false, which the entity name
            // render checks - so this hides the floating name + health hearts.
            mc.gameSettings.hideGUI = true;
            try {
                int cx = mx0 + mw / 2;
                int feet = my0 + mh - 8;
                int scale = (int) (mh / 2.5);
                // Full-bright lightmap so the model isn't dark in dim areas.
                GlStateManager.color(1f, 1f, 1f, 1f);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                GuiInventory.drawEntityOnScreen(cx, feet, scale, cx - mouseX, (my0 + mh / 3) - mouseY, ent);
            } finally {
                mc.gameSettings.hideGUI = prevHide;
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            return true;
        } catch (Throwable t) {
            modelBroken = true;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.color(1f, 1f, 1f, 1f);
            return false;
        }
    }

    /** Builds the stable fake entity for the 3D model: the given skin, plus armor when supplied (online). */
    private EntityPlayer buildModel(ResourceLocation skinRL, boolean slimModel, ItemStack[] armorStacks) {
        if (skinRL == null) {
            return null;
        }
        try {
            World world = Minecraft.getMinecraft().theWorld;
            if (world == null) {
                return null;
            }
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("MassiveoInfo:" + rawName).getBytes()), rawName);
            FakeSkinPlayer f = new FakeSkinPlayer(world, profile, skinRL, slimModel);
            if (armorStacks != null) {
                // Equipment slots 1..4 = boots, leggings, chestplate, helmet.
                for (int i = 0; i < 4; i++) {
                    if (armorStacks[i] != null) {
                        f.setCurrentItemOrArmor(i + 1, armorStacks[i]);
                    }
                }
            }
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** A minimal offline player entity that reports the fetched skin, so RenderPlayer draws it. */
    private static final class FakeSkinPlayer extends EntityOtherPlayerMP {
        private final ResourceLocation skinLoc;
        private final boolean slim;

        FakeSkinPlayer(World world, GameProfile profile, ResourceLocation skinLoc, boolean slim) {
            super(world, profile);
            this.skinLoc = skinLoc;
            this.slim = slim;
        }

        @Override
        public ResourceLocation getLocationSkin() {
            return skinLoc;
        }

        @Override
        public boolean hasSkin() {
            return skinLoc != null;
        }

        @Override
        public String getSkinType() {
            return slim ? "slim" : "default";
        }
    }

    private int drawCelle(int x, int y) {
        drawString(this.fontRendererObj, EnumChatFormatting.AQUA + "Celle", x, y, 0x55FFFF);
        y += 11;
        int count = PlayerInfo.getCelleCount();
        String countStr = count > 0 ? String.valueOf(count) : (PlayerInfo.isLoading() ? "henter..." : "0");
        drawString(this.fontRendererObj, EnumChatFormatting.GRAY + "Celler i alt: " + EnumChatFormatting.WHITE + countStr, x + 4, y, 0xFFFFFF);
        y += 10;
        PlayerInfo.Celle c = PlayerInfo.getCelle();
        if (c == null) {
            String msg = PlayerInfo.isLoading() ? "Henter celle info..." : "Ingen celle fundet";
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + msg, x + 4, y, 0xAAAAAA);
            return y + 11;
        }
        String head = c.id != null ? c.id : "?";
        if (c.gang != null && !c.gang.isEmpty()) {
            head += "  " + EnumChatFormatting.GRAY + "(" + c.gang + ")";
        }
        drawString(this.fontRendererObj, EnumChatFormatting.WHITE + head, x + 4, y, 0xFFFFFF);
        y += 10;
        if (c.owner != null) {
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + "Ejer: " + EnumChatFormatting.WHITE + c.owner, x + 4, y, 0xFFFFFF);
            y += 10;
        }
        if (c.tid != null) {
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + "Tid: " + EnumChatFormatting.WHITE + c.tid, x + 4, y, 0xFFFFFF);
            y += 10;
        }
        if (!c.members.isEmpty()) {
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + "Medlemmer: " + EnumChatFormatting.WHITE
                    + trimToWidth(join(c.members), INFO_W - 8), x + 4, y, 0xFFFFFF);
            y += 10;
        }
        return y;
    }

    private void drawArmor(Minecraft mc, int x, int y) {
        RenderItem ri = mc.getRenderItem();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int idx = 0; idx < 4; idx++) {
            ItemStack s = armor[3 - idx];
            if (s != null) {
                ri.renderItemAndEffectIntoGUI(s, x, y + idx * ROW_H);
                ri.renderItemOverlayIntoGUI(this.fontRendererObj, s, x, y + idx * ROW_H, null);
            }
        }
        RenderHelper.disableStandardItemLighting();

        GlStateManager.disableLighting();
        for (int idx = 0; idx < 4; idx++) {
            int slot = 3 - idx;
            ItemStack s = armor[slot];
            int ry = y + idx * ROW_H;
            if (s == null) {
                drawString(this.fontRendererObj, SLOT_NAMES[slot] + ": " + EnumChatFormatting.DARK_GRAY + "ingen", x + 22, ry + 4, 0x777777);
                continue;
            }
            try {
                List<String> tip = s.getTooltip(mc.thePlayer, false);
                String name = tip.isEmpty() ? s.getDisplayName() : tip.get(0);
                drawString(this.fontRendererObj, trimToWidth(name, INFO_W - 24), x + 22, ry, 0xFFFFFF);
                StringBuilder ench = new StringBuilder();
                for (int k = 1; k < tip.size(); k++) {
                    String line = tip.get(k) == null ? "" : EnumChatFormatting.getTextWithoutFormattingCodes(tip.get(k)).trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (ench.length() > 0) {
                        ench.append(", ");
                    }
                    ench.append(line);
                }
                String enchLine = ench.length() > 0 ? ench.toString() : "ingen fortryllelser";
                drawString(this.fontRendererObj, EnumChatFormatting.GRAY + trimToWidth(enchLine, INFO_W - 24), x + 22, ry + 10, 0xAAAAAA);
            } catch (Throwable t) {
                drawString(this.fontRendererObj, s.getDisplayName(), x + 22, ry, 0xFFFFFF);
            }
        }
    }

    private void drawHead(Minecraft mc, int x, int y, int size) {
        if (skin == null) {
            return;
        }
        try {
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(skin);
            Gui.drawScaledCustomSizeModalRect(x, y, 8, 8, 8, 8, size, size, 64, 64);
            Gui.drawScaledCustomSizeModalRect(x, y, 40, 8, 8, 8, size, size, 64, 64);
        } catch (Throwable ignored) {
        }
    }

    /** Draws a flat, front-facing render of a skin (head/body/arms/legs + overlays), scaled by s. */
    private void drawSkinBody(Minecraft mc, ResourceLocation loc, int centerX, int topY, int s, boolean slim, boolean legacy) {
        try {
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.getTextureManager().bindTexture(loc);
            float th = legacy ? 32f : 64f;
            int armW = slim ? 3 : 4;
            int x0 = centerX - (2 * armW + 8) * s / 2;
            int headX = x0 + armW * s;
            int bodyY = topY + 8 * s;
            int legsY = bodyY + 12 * s;
            int leftArmX = x0 + (armW + 8) * s;

            // Base layer.
            part(headX, topY, 8, 8, 8, 8, s, th);
            part(x0, bodyY, 44, 20, armW, 12, s, th);                                   // right arm
            part(headX, bodyY, 20, 20, 8, 12, s, th);                                   // body
            part(leftArmX, bodyY, legacy ? 44 : 36, legacy ? 20 : 52, armW, 12, s, th); // left arm (mirror on legacy)
            part(headX, legsY, 4, 20, 4, 12, s, th);                                    // right leg
            part(x0 + (armW + 4) * s, legsY, legacy ? 4 : 20, legacy ? 20 : 52, 4, 12, s, th); // left leg

            // Overlay (hat + jacket + sleeves + pants) - hat always, rest 64x64 only.
            part(headX, topY, 40, 8, 8, 8, s, th);                                      // hat
            if (!legacy) {
                part(x0, bodyY, 44, 36, armW, 12, s, th);                               // right sleeve
                part(headX, bodyY, 20, 36, 8, 12, s, th);                               // jacket
                part(leftArmX, bodyY, 52, 52, armW, 12, s, th);                         // left sleeve
                part(headX, legsY, 4, 36, 4, 12, s, th);                                // right pant
                part(x0 + (armW + 4) * s, legsY, 4, 52, 4, 12, s, th);                  // left pant
            }
        } catch (Throwable ignored) {
        }
    }

    private void part(int x, int y, int u, int v, int uw, int vh, int s, float tileH) {
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, uw, vh, uw * s, vh * s, 64.0F, tileH);
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private String trimToWidth(String text, int maxWidth) {
        if (this.fontRendererObj.getStringWidth(text) <= maxWidth) {
            return text;
        }
        return this.fontRendererObj.trimStringToWidth(text, maxWidth - this.fontRendererObj.getStringWidth("...")) + "...";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
