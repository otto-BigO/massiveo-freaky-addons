package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Visual selection menu for Armor Skins featuring side-by-side 3D player previews
 * of the MesterHolm and Hypixel+ skin packs.
 */
public class GuiArmorSkins extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int ID_MATERIAL = 2;
    private static final int ID_LEVEL = 3;

    private static final int BTN_W = 150;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;
    private GuiButton materialButton;
    private GuiButton levelButton;
    private EntityOtherPlayerMP previewEntity = null;

    private String currentMaterial = "diamond";
    private int currentLevel = 3;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Top selectors
        this.buttonList.add(materialButton = new StyledButton(ID_MATERIAL, cx - 115, cy - 98, 105, 18, materialLabel()));
        this.buttonList.add(levelButton = new StyledButton(ID_LEVEL, cx + 10, cy - 98, 105, 18, levelLabel()));

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, cx - BTN_W / 2, cy + 96, BTN_W, BTN_H, toggleLabel()));
        this.buttonList.add(new StyledButton(ID_BACK, cx - BTN_W / 2, cy + 120, BTN_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Rustnings-skins: " + (CelleScannerMod.config.armorSkinsEnabled ? "Til" : "Fra");
    }

    private String materialLabel() {
        return "Materiale: " + ("diamond".equals(currentMaterial) ? "Diamant" : "Jern");
    }

    private String levelLabel() {
        return "Niveau: P" + currentLevel;
    }

    private void setupPreviewEntity() {
        if (previewEntity == null && this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null) {
            previewEntity = PlayerModelRenderer.buildEntity(this.mc.thePlayer.getName(), SkinFetcher.get(this.mc.thePlayer.getName()));
            if (previewEntity != null) {
                updatePreviewEquipment();
            }
        }
    }

    private void updatePreviewEquipment() {
        if (previewEntity == null) return;

        ItemStack helmet = new ItemStack("iron".equals(currentMaterial) ? Items.iron_helmet : Items.diamond_helmet);
        ItemStack chest = new ItemStack("iron".equals(currentMaterial) ? Items.iron_chestplate : Items.diamond_chestplate);
        ItemStack legs = new ItemStack("iron".equals(currentMaterial) ? Items.iron_leggings : Items.diamond_leggings);
        ItemStack boots = new ItemStack("iron".equals(currentMaterial) ? Items.iron_boots : Items.diamond_boots);

        helmet.addEnchantment(Enchantment.protection, currentLevel);
        chest.addEnchantment(Enchantment.protection, currentLevel);
        legs.addEnchantment(Enchantment.protection, currentLevel);
        boots.addEnchantment(Enchantment.protection, currentLevel);

        previewEntity.setCurrentItemOrArmor(4, helmet);
        previewEntity.setCurrentItemOrArmor(3, chest);
        previewEntity.setCurrentItemOrArmor(2, legs);
        previewEntity.setCurrentItemOrArmor(1, boots);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            int cx = this.width / 2;
            int cy = this.height / 2;

            // Card boundaries (y: cy - 75 to cy + 85, w: 105)
            // Left Card: MesterHolm
            if (mouseX >= cx - 115 && mouseX <= cx - 10 && mouseY >= cy - 75 && mouseY <= cy + 85) {
                CelleScannerMod.config.armorSkinPack = "mesterholm";
                CelleScannerMod.config.save();
                this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
            }
            // Right Card: Hypixel+
            else if (mouseX >= cx + 10 && mouseX <= cx + 115 && mouseY >= cy - 75 && mouseY <= cy + 85) {
                CelleScannerMod.config.armorSkinPack = "hypixel";
                CelleScannerMod.config.save();
                this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleArmorSkins();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_MATERIAL:
                currentMaterial = "diamond".equals(currentMaterial) ? "iron" : "diamond";
                materialButton.displayString = materialLabel();
                updatePreviewEquipment();
                break;
            case ID_LEVEL:
                currentLevel = currentLevel % 4 + 1;
                levelButton.displayString = levelLabel();
                updatePreviewEquipment();
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        setupPreviewEntity();

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Title
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + "RUSTNINGS PREVIEWS", cx, cy - 105, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Vælg din foretrukne tekstur-pakke nedenfor.", cx, cy - 92, 0xCCCCCC);

        // Draw selection cards
        String activePack = CelleScannerMod.config.armorSkinPack;

        // MesterHolm Card (Left)
        boolean hoveredA = mouseX >= cx - 115 && mouseX <= cx - 10 && mouseY >= cy - 75 && mouseY <= cy + 85;
        boolean selectedA = "mesterholm".equals(activePack);
        int borderA = selectedA ? 0xCC4BE08C : (hoveredA ? 0x664BE08C : 0x44202026);
        int cardBgA = selectedA ? 0xE61A1A22 : 0xE6141418;

        Style.roundedRect(cx - 116, cy - 76, cx - 9, cy + 86, borderA);
        Style.roundedRect(cx - 115, cy - 75, cx - 10, cy + 85, cardBgA);

        if (previewEntity != null) {
            CustomArmorLayer.previewPackOverride = "mesterholm";
            PlayerModelRenderer.draw(this.mc, previewEntity, cx - 115 + 2, cy - 75 + 10, 101, 120, mouseX, mouseY, null);
            CustomArmorLayer.previewPackOverride = null;
        }
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.WHITE + "MesterHolm", cx - 62, cy + 60, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.DARK_GRAY + "Standard", cx - 62, cy + 72, 0x888888);

        // Hypixel+ Card (Right)
        boolean hoveredB = mouseX >= cx + 10 && mouseX <= cx + 115 && mouseY >= cy - 75 && mouseY <= cy + 85;
        boolean selectedB = "hypixel".equals(activePack);
        int borderB = selectedB ? 0xCC4BE08C : (hoveredB ? 0x664BE08C : 0x44202026);
        int cardBgB = selectedB ? 0xE61A1A22 : 0xE6141418;

        Style.roundedRect(cx + 9, cy - 76, cx + 116, cy + 86, borderB);
        Style.roundedRect(cx + 10, cy - 75, cx + 115, cy + 85, cardBgB);

        if (previewEntity != null) {
            CustomArmorLayer.previewPackOverride = "hypixel";
            PlayerModelRenderer.draw(this.mc, previewEntity, cx + 10 + 2, cy - 75 + 10, 101, 120, mouseX, mouseY, null);
            CustomArmorLayer.previewPackOverride = null;
        }
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.WHITE + "Hypixel+", cx + 62, cy + 60, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.DARK_GRAY + "Farveret", cx + 62, cy + 72, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
