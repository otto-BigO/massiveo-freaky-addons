package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.util.List;

/**
 * The player info menu opened by shift + right-clicking a player (see
 * PlayerInfo). Shows their face, name and each equipped armor piece with its
 * enchants. Armor stacks and the skin are captured when the menu opens, so it
 * stays correct even if the player walks off or unloads while it's open.
 */
public class GuiPlayerInfo extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int PANEL_W = 240;
    private static final int HEAD = 32;
    private static final int ROW_H = 22;

    // 0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet (as getCurrentArmor).
    private static final String[] SLOT_NAMES = {"Støvler", "Bukser", "Brystplade", "Hjelm"};

    private final String playerName;
    private final ItemStack[] armor = new ItemStack[4];
    private ResourceLocation skin;

    public GuiPlayerInfo(EntityPlayer target) {
        String name;
        try {
            name = target.getDisplayName() != null ? target.getDisplayName().getFormattedText() : target.getName();
        } catch (Throwable t) {
            name = target.getName();
        }
        this.playerName = name;
        for (int i = 0; i < 4; i++) {
            ItemStack s = target.getCurrentArmor(i);
            armor[i] = s == null ? null : s.copy();
        }
        try {
            if (target instanceof AbstractClientPlayer) {
                skin = ((AbstractClientPlayer) target).getLocationSkin();
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        this.buttonList.add(new StyledButton(ID_BACK, left, this.height - 30, PANEL_W, 20, "Luk"));
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
        Style.card(this.width, this.height);

        Minecraft mc = Minecraft.getMinecraft();
        int left = this.width / 2 - PANEL_W / 2;
        int top = 28;

        // Head + name.
        if (skin != null) {
            try {
                GlStateManager.color(1f, 1f, 1f, 1f);
                mc.getTextureManager().bindTexture(skin);
                Gui.drawScaledCustomSizeModalRect(left, top, 8, 8, 8, 8, HEAD, HEAD, 64, 64);   // face
                Gui.drawScaledCustomSizeModalRect(left, top, 40, 8, 8, 8, HEAD, HEAD, 64, 64);  // hat overlay
            } catch (Throwable ignored) {
            }
        }
        drawString(this.fontRendererObj, playerName, left + HEAD + 8, top + 6, 0xFFFFFF);
        drawString(this.fontRendererObj, EnumChatFormatting.GRAY + "Spiller Info", left + HEAD + 8, top + 18, 0xAAAAAA);

        int labelY = top + HEAD + 8;
        drawString(this.fontRendererObj, EnumChatFormatting.GREEN + "Rustning", left, labelY, 0x55FF55);
        int rowsTop = labelY + 12;

        // Pass 1: item icons (needs GUI item lighting), helmet down to boots.
        RenderItem ri = mc.getRenderItem();
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int idx = 0; idx < 4; idx++) {
            ItemStack s = armor[3 - idx];
            if (s != null) {
                int ry = rowsTop + idx * ROW_H;
                ri.renderItemAndEffectIntoGUI(s, left, ry);
                ri.renderItemOverlayIntoGUI(this.fontRendererObj, s, left, ry, null);
            }
        }
        RenderHelper.disableStandardItemLighting();

        // Pass 2: name + enchants text next to each icon.
        GlStateManager.disableLighting();
        for (int idx = 0; idx < 4; idx++) {
            int slot = 3 - idx;
            ItemStack s = armor[slot];
            int ry = rowsTop + idx * ROW_H;
            if (s == null) {
                drawString(this.fontRendererObj, SLOT_NAMES[slot] + ": " + EnumChatFormatting.DARK_GRAY + "ingen", left + 22, ry + 4, 0x777777);
                continue;
            }
            try {
                List<String> tip = s.getTooltip(mc.thePlayer, false);
                String name = tip.isEmpty() ? s.getDisplayName() : tip.get(0);
                drawString(this.fontRendererObj, name, left + 22, ry, 0xFFFFFF);

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
                drawString(this.fontRendererObj, EnumChatFormatting.GRAY + trimToWidth(enchLine, PANEL_W - 22), left + 22, ry + 10, 0xAAAAAA);
            } catch (Throwable t) {
                drawString(this.fontRendererObj, s.getDisplayName(), left + 22, ry, 0xFFFFFF);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Cuts a string with an ellipsis so it fits the given pixel width. */
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
