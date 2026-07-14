package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for searching and assigning an item icon to a chest.
 * Supports selecting placement direction (Front, Back, Left, Right, Top, Bottom).
 */
public class GuiChestOrganizer extends GuiScreen {

    private static final String[] PLACEMENTS = {"FRONT", "BACK", "LEFT", "RIGHT", "TOP", "BOTTOM"};
    private static final String[] PLACEMENT_LABELS = {"Foran", "Bagved", "Venstre side", "Højre side", "Top", "Bund"};

    private final BlockPos targetPos;
    private GuiTextField searchField;
    private final List<ItemStack> searchResults = new ArrayList<ItemStack>();
    private ItemStack selectedStack = null;
    private int selectedPlacementIdx = 0;

    private int guiWidth = 220;
    private int guiHeight = 210;
    private int startX;
    private int startY;

    private GuiButton saveButton;
    private GuiButton deleteButton;
    private GuiButton placementButton;

    public GuiChestOrganizer(BlockPos targetPos) {
        this.targetPos = targetPos;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.startX = (this.width - this.guiWidth) / 2;
        this.startY = (this.height - this.guiHeight) / 2;

        // Initialize Search Field
        this.searchField = new GuiTextField(0, this.fontRendererObj, startX + 15, startY + 35, 190, 20);
        this.searchField.setMaxStringLength(32);
        this.searchField.setFocused(true);
        this.searchField.setText("");

        // Add Buttons
        this.buttonList.clear();
        this.saveButton = new GuiButton(1, startX + 15, startY + 175, 60, 20, "Gem");
        this.deleteButton = new GuiButton(2, startX + 80, startY + 175, 60, 20, "Slet");
        GuiButton cancelButton = new GuiButton(3, startX + 145, startY + 175, 60, 20, "Afbryd");

        this.placementButton = new GuiButton(4, startX + 15, startY + 115, 190, 20, getPlacementButtonLabel());

        this.buttonList.add(saveButton);
        this.buttonList.add(deleteButton);
        this.buttonList.add(cancelButton);
        this.buttonList.add(placementButton);

        // Retrieve existing item and placement if configured
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc.thePlayer != null ? mc.thePlayer.dimension : 0;
        String currentIcon = ChestOrganizerPositions.getIcon(targetPos, dimension);
        if (currentIcon != null && !currentIcon.isEmpty()) {
            String itemName = currentIcon;
            String placementName = "FRONT";
            if (currentIcon.contains(";")) {
                String[] parts = currentIcon.split(";");
                itemName = parts[0];
                placementName = parts[1];
            }
            Item item = Item.getByNameOrId(itemName);
            if (item != null) {
                this.selectedStack = new ItemStack(item);
            }
            for (int i = 0; i < PLACEMENTS.length; i++) {
                if (PLACEMENTS[i].equalsIgnoreCase(placementName)) {
                    this.selectedPlacementIdx = i;
                    break;
                }
            }
        }

        updateSearch();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateSearch() {
        this.searchResults.clear();
        String query = this.searchField.getText().trim().toLowerCase();

        int limit = 18; // 2 rows of 9 items
        for (Object obj : Item.itemRegistry) {
            if (this.searchResults.size() >= limit) {
                break;
            }
            Item item = (Item) obj;
            if (item == null) continue;
            
            ItemStack stack = new ItemStack(item);
            String displayName = stack.getDisplayName().toLowerCase();
            ResourceLocation loc = Item.itemRegistry.getNameForObject(item);
            String regName = loc != null ? loc.toString().toLowerCase() : "";

            if (query.isEmpty() || displayName.contains(query) || regName.contains(query)) {
                this.searchResults.add(stack);
            }
        }

        // Enable/disable buttons appropriately
        this.saveButton.enabled = (selectedStack != null);
        
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc.thePlayer != null ? mc.thePlayer.dimension : 0;
        this.deleteButton.enabled = (ChestOrganizerPositions.getIcon(targetPos, dimension) != null);
    }

    private String getPlacementButtonLabel() {
        return "Placering: " + PLACEMENT_LABELS[selectedPlacementIdx];
    }

    @Override
    public void updateScreen() {
        this.searchField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            updateSearch();
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.searchField.mouseClicked(mouseX, mouseY, mouseButton);

        // Check if clicked inside the item grid
        int gridX = startX + 15;
        int gridY = startY + 65;

        for (int i = 0; i < this.searchResults.size(); i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = gridX + col * 21;
            int slotY = gridY + row * 21;

            if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18) {
                this.selectedStack = this.searchResults.get(i);
                this.saveButton.enabled = true;
                this.mc.thePlayer.playSound("random.click", 0.5F, 1.2F);
                break;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc.thePlayer != null ? mc.thePlayer.dimension : 0;

        if (button.id == 1 && selectedStack != null) {
            // Save Icon with Placement
            ResourceLocation loc = Item.itemRegistry.getNameForObject(selectedStack.getItem());
            if (loc != null) {
                String val = loc.toString() + ";" + PLACEMENTS[selectedPlacementIdx];
                ChestOrganizerPositions.setIcon(targetPos, dimension, val);
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN + "[Kiste-Organisering] Ikon gemt!"
                ));
            }
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            // Delete Icon
            ChestOrganizerPositions.removeIcon(targetPos, dimension);
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.RED + "[Kiste-Organisering] Ikon slettet!"
            ));
            this.mc.displayGuiScreen(null);
        } else if (button.id == 3) {
            // Cancel
            this.mc.displayGuiScreen(null);
        } else if (button.id == 4) {
            // Cycle Placement
            selectedPlacementIdx = (selectedPlacementIdx + 1) % PLACEMENTS.length;
            button.displayString = getPlacementButtonLabel();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Draw Card Background
        Style.roundedRect(startX - 1, startY - 1, startX + guiWidth + 1, startY + guiHeight + 1, 0x334BE08C); // glow border
        drawRect(startX, startY, startX + guiWidth, startY + guiHeight, 0xCC0C0C12); // background

        // Title
        drawCenteredString(this.fontRendererObj, "Kiste-Organisering", startX + guiWidth / 2, startY + 15, 0x55FF55);

        // Draw Search Input Box Label
        drawString(this.fontRendererObj, "Søg efter genstand:", startX + 15, startY + 25, 0x888888);

        // Draw Text Field
        this.searchField.drawTextBox();

        // Render Item Grid
        int gridX = startX + 15;
        int gridY = startY + 65;

        RenderHelper.enableGUIStandardItemLighting();
        ItemStack hoveredStack = null;

        for (int i = 0; i < this.searchResults.size(); i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = gridX + col * 21;
            int slotY = gridY + row * 21;

            // Draw a subtle border around selected slot
            boolean isHovered = mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= slotY && mouseY <= slotY + 18;
            boolean isSelected = selectedStack != null && selectedStack.getItem() == this.searchResults.get(i).getItem();

            if (isSelected) {
                drawRect(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xCC55FF55);
            } else if (isHovered) {
                drawRect(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0x554BE08C);
            }

            drawRect(slotX, slotY, slotX + 16, slotY + 16, 0x55000000);
            
            // Draw item stack
            this.itemRender.renderItemIntoGUI(this.searchResults.get(i), slotX, slotY);

            if (isHovered) {
                hoveredStack = this.searchResults.get(i);
            }
        }

        // Draw Preview of Currently Selected Item
        if (selectedStack != null) {
            drawString(this.fontRendererObj, "Valgt:", startX + 15, startY + 150, 0x888888);
            this.itemRender.renderItemIntoGUI(selectedStack, startX + 50, startY + 147);
            drawString(this.fontRendererObj, selectedStack.getDisplayName(), startX + 72, startY + 151, 0xFFFFFF);
        } else {
            drawString(this.fontRendererObj, "Intet ikon valgt", startX + 15, startY + 150, 0x888888);
        }

        RenderHelper.disableStandardItemLighting();

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw Tooltip if hovering over an item
        if (hoveredStack != null) {
            renderToolTip(hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
