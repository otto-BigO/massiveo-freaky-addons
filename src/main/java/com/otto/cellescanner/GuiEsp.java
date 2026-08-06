package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Control screen for the ESP addon - Apple Motion Physics.
 */
public class GuiEsp extends GuiScreen {

    private static final int ID_ADD = 0;
    private static final int ID_CLEAR = 1;
    private static final int ID_ESP = 2;
    private static final int ID_AUTO = 3;
    private static final int ID_BACK = 4;
    private static final int ID_ALL = 5;
    private static final int ID_MODE = 6;
    private static final int REMOVE_BASE = 100;
    private static final int MAX_REMOVE_ROWS = 4;

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int REMOVE_H = 18;
    private static final int ROW_GAP = 6;

    private GuiTextField nameField;
    private GuiButton espButton;
    private GuiButton autoButton;
    private GuiButton allButton;
    private GuiButton modeButton;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    private final List<String> shownNames = new ArrayList<String>();
    private int listHintY;
    private int scrollOffset = 0;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.shownNames.clear();

        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_W / 2;
        int y = this.height / 2 - 118;

        String carry = nameField != null && nameField.getText() != null ? nameField.getText() : "";
        nameField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W, FIELD_H);
        nameField.setMaxStringLength(32);
        nameField.setText(carry);
        nameField.setFocused(true);
        y += FIELD_H + ROW_GAP + 4;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_ADD, fieldX, y, halfW, BTN_H, "Tilføj"));
        this.buttonList.add(new StyledButton(ID_CLEAR, fieldX + halfW + 4, y, halfW, BTN_H, "Ryd alle"));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(espButton = new StyledButton(ID_ESP, fieldX, y, halfW, BTN_H, espLabel()));
        this.buttonList.add(autoButton = new StyledButton(ID_AUTO, fieldX + halfW + 4, y, halfW, BTN_H, autoLabel()));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(modeButton = new StyledButton(ID_MODE, fieldX, y, halfW, BTN_H, modeLabel()));
        this.buttonList.add(allButton = new StyledButton(ID_ALL, fieldX + halfW + 4, y, halfW, BTN_H, allLabel()));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(new StyledButton(ID_BACK, fieldX, y, FIELD_W, BTN_H, "< Tilbage"));
        y += BTN_H + ROW_GAP + 4;

        listHintY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 4;

        List<String> members = MassiveOsFreakyAddons.config.bandeMembers;
        int maxOffset = Math.max(0, members.size() - MAX_REMOVE_ROWS);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        int shown = Math.min(members.size() - scrollOffset, MAX_REMOVE_ROWS);
        for (int i = 0; i < shown; i++) {
            String name = members.get(scrollOffset + i);
            shownNames.add(name);
            this.buttonList.add(new StyledButton(REMOVE_BASE + i, fieldX, y, FIELD_W, REMOVE_H, "Fjern  " + name));
            y += REMOVE_H + 2;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int maxOffset = Math.max(0, MassiveOsFreakyAddons.config.bandeMembers.size() - MAX_REMOVE_ROWS);
        if (wheel < 0 && scrollOffset < maxOffset) {
            scrollOffset++;
            this.initGui();
        } else if (wheel > 0 && scrollOffset > 0) {
            scrollOffset--;
            this.initGui();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String espLabel() {
        return "ESP: " + (MassiveOsFreakyAddons.config.espAddonEnabled ? "Til" : "Fra");
    }

    private String autoLabel() {
        return "Auto-hold: " + (MassiveOsFreakyAddons.config.espAutoTeam ? "Til" : "Fra");
    }

    private String allLabel() {
        return "ESP på alle: " + (MassiveOsFreakyAddons.config.espAllPlayers ? "Til" : "Fra");
    }

    private String modeLabel() {
        String m = MassiveOsFreakyAddons.config.espMode != null ? MassiveOsFreakyAddons.config.espMode : "2D";
        return "Tilstand: " + m;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= REMOVE_BASE) {
            int index = button.id - REMOVE_BASE;
            if (index >= 0 && index < shownNames.size()) {
                String name = shownNames.get(index);
                CelleActions.removeBandeMember(name);
                statusLine = "\"" + name + "\" fjernet.";
                statusColor = 0xAAAAAA;
                this.initGui();
            }
            return;
        }

        switch (button.id) {
            case ID_ADD:
                addCurrent();
                break;
            case ID_CLEAR:
                CelleActions.clearBandeMembers();
                statusLine = "Bande-listen er ryddet.";
                statusColor = 0xAAAAAA;
                this.initGui();
                break;
            case ID_ESP:
                CelleActions.toggleEspAddon();
                espButton.displayString = espLabel();
                break;
            case ID_AUTO:
                CelleActions.toggleEspAutoTeam();
                autoButton.displayString = autoLabel();
                break;
            case ID_ALL:
                CelleActions.toggleEspAllPlayers();
                allButton.displayString = allLabel();
                break;
            case ID_MODE:
                String curr = MassiveOsFreakyAddons.config.espMode != null ? MassiveOsFreakyAddons.config.espMode : "2D";
                if (curr.equalsIgnoreCase("2D")) {
                    MassiveOsFreakyAddons.config.espMode = "Corners";
                } else if (curr.equalsIgnoreCase("Corners")) {
                    MassiveOsFreakyAddons.config.espMode = "3D";
                } else if (curr.equalsIgnoreCase("3D")) {
                    MassiveOsFreakyAddons.config.espMode = "Outline";
                } else {
                    MassiveOsFreakyAddons.config.espMode = "2D";
                }
                MassiveOsFreakyAddons.config.save();
                modeButton.displayString = modeLabel();
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    private void addCurrent() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            statusLine = "Indtast et spillernavn først.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.addBandeMember(name);
        nameField.setText("");
        statusLine = "\"" + name + "\" tilføjet.";
        statusColor = 0x55FF55;
        this.initGui();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            addCurrent();
            return;
        }
        nameField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 118 - 22;
        drawCenteredString(this.fontRendererObj, net.minecraft.util.EnumChatFormatting.BOLD + "ESP", this.width / 2, titleY, Style.getAccentColor());
        // The old line named the colours, and named them wrongly: bande is blue
        // by default, green is the vagt colour. It says who gets a box instead,
        // which is the part that does not change when the palette does.
        drawCenteredString(this.fontRendererObj,
                "Bande, venner og vagter har hver sin farve. \"ESP på alle\" tager alle andre med.",
                this.width / 2, titleY + 12, 0xAAAAAA);

        nameField.drawTextBox();

        List<String> members = MassiveOsFreakyAddons.config.bandeMembers;
        if (members.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "(ingen bande-medlemmer sat)", this.width / 2, listHintY, 0xAAAAAA);
        } else {
            drawCenteredString(this.fontRendererObj, members.size() + " medlem(mer) - klik Fjern for at slette:", this.width / 2, listHintY, 0xAAAAAA);
            if (members.size() > MAX_REMOVE_ROWS) {
                int first = scrollOffset + 1;
                int last = Math.min(members.size(), scrollOffset + MAX_REMOVE_ROWS);
                drawCenteredString(this.fontRendererObj, "Viser " + first + "-" + last + " af " + members.size() + " - scroll for flere",
                        this.width / 2, this.height / 2 + 118, 0x888888);
            }
        }

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 132, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
