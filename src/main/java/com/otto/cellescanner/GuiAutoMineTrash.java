package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Checkbox configuration GUI for choosing which blocks/items AutoMine bot drops as junk.
 */
public class GuiAutoMineTrash extends GuiScreen {

    private static final int ID_SAVE = 0;
    private static final int BTN_W = 100;
    private static final int BTN_H = 20;

    private static final String[] ITEMS = {"Cobblestone", "Sandstone", "Lapis Blok", "Lapis Lazuli"};

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.buttonList.add(new StyledButton(ID_SAVE, cx - BTN_W / 2, cy + 50, BTN_W, BTN_H, "Gem & Luk"));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            int startY = cy - 35;

            for (int i = 0; i < ITEMS.length; i++) {
                int yPos = startY + i * 18;
                // Checkbox bounding box (width/height: ~150x12)
                if (mouseX >= cx - 75 && mouseX <= cx + 75 && mouseY >= yPos - 2 && mouseY <= yPos + 12) {
                    String item = ITEMS[i];
                    if (CelleScannerMod.config.trashItems.contains(item)) {
                        CelleScannerMod.config.trashItems.remove(item);
                    } else {
                        CelleScannerMod.config.trashItems.add(item);
                    }
                    CelleScannerMod.config.save();
                    this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
                    break;
                }
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_SAVE) {
            this.mc.displayGuiScreen(new GuiAutoMine());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Title
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + "SKRALDE FILTER", cx, cy - 74, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Vælg hvad robotten skal smide ud:", cx, cy - 60, 0xCCCCCC);

        int startY = cy - 35;
        for (int i = 0; i < ITEMS.length; i++) {
            String item = ITEMS[i];
            int yPos = startY + i * 18;

            boolean checked = CelleScannerMod.config.trashItems.contains(item);
            boolean hovered = mouseX >= cx - 75 && mouseX <= cx + 75 && mouseY >= yPos - 2 && mouseY <= yPos + 12;

            // Draw Checkbox box
            int boxColor = checked ? 0xCC4BE08C : (hovered ? 0x664BE08C : 0x44202026);
            Style.roundedRect(cx - 70, yPos, cx - 60, yPos + 10, boxColor);
            Style.roundedRect(cx - 69, yPos + 1, cx - 61, yPos + 9, 0xFF141418);

            if (checked) {
                // Fill checked marker (mint-green box)
                Style.roundedRect(cx - 67, yPos + 3, cx - 63, yPos + 7, 0xFF4BE08C);
            }

            // Draw Text label next to checkbox
            drawString(this.fontRendererObj, EnumChatFormatting.WHITE + item, cx - 50, yPos + 1, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
