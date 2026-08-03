package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Celle Buyer's pick list. Celler on this list are the only ones it will
 * claim, and each one gets a rainbow box in the world so you can see what you
 * picked without coming back in here.
 */
public class GuiCelleBuyerList extends GuiScreen {

    private static final int ID_ADD = 0;
    private static final int ID_PASTE = 1;
    private static final int ID_USE_LIST = 2;
    private static final int ID_CLEAR = 3;
    private static final int ID_BACK = 4;
    private static final int ID_SCROLL_UP = 5;
    private static final int ID_SCROLL_DOWN = 6;
    private static final int ROW_BASE = 100;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;
    private static final int ROW_H = 16;
    private static final int GAP = 3;
    /** Five rows is what fits between the toggle and the buttons at the bottom. */
    private static final int VISIBLE_ROWS = 5;

    private GuiTextField idField;
    private GuiButton useListBtn;
    private final List<String> shown = new ArrayList<String>();
    private int scroll = 0;

    private String status = "";
    private int statusColor = 0xAAAAAA;
    private int listTopY;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    private static CelleConfig cfg() {
        return MassiveOsFreakyAddons.config;
    }

    private static List<String> picks() {
        CelleConfig c = cfg();
        if (c == null || c.celleBuyerWhitelist == null) {
            return new ArrayList<String>();
        }
        return c.celleBuyerWhitelist;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        shown.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int y = this.height / 2 - 104;

        int smallW = 52;
        String carry = idField != null && idField.getText() != null ? idField.getText() : "";
        idField = new GuiTextField(0, this.fontRendererObj, left, y,
                PANEL_W - (smallW * 2) - (GAP * 2), BTN_H);
        idField.setMaxStringLength(32);
        idField.setText(carry);
        idField.setFocused(true);
        this.buttonList.add(new StyledButton(ID_PASTE,
                left + PANEL_W - (smallW * 2) - GAP, y - 1, smallW, BTN_H + 2, "Indsæt"));
        this.buttonList.add(new StyledButton(ID_ADD,
                left + PANEL_W - smallW, y - 1, smallW, BTN_H + 2, "Tilføj"));
        y += BTN_H + GAP + 5;

        this.buttonList.add(useListBtn = new StyledButton(ID_USE_LIST, left, y, PANEL_W, BTN_H, useListLabel()));
        y += BTN_H + GAP + 5;

        // Clamp before laying rows out, so removing the last row does not leave
        // the view scrolled past the end of a now-shorter list.
        int max = Math.max(0, picks().size() - VISIBLE_ROWS);
        if (scroll > max) {
            scroll = max;
        }
        if (scroll < 0) {
            scroll = 0;
        }

        listTopY = y;
        List<String> all = picks();
        int rowW = all.size() > VISIBLE_ROWS ? PANEL_W - 14 : PANEL_W;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= all.size()) {
                break;
            }
            String id = all.get(index);
            shown.add(id);
            this.buttonList.add(new StyledButton(ROW_BASE + i, left, y, rowW, ROW_H, rowLabel(id)));
            y += ROW_H + 2;
        }
        if (all.size() > VISIBLE_ROWS) {
            this.buttonList.add(new StyledButton(ID_SCROLL_UP,
                    left + PANEL_W - 12, listTopY, 12, ROW_H, "▲"));
            this.buttonList.add(new StyledButton(ID_SCROLL_DOWN,
                    left + PANEL_W - 12, listTopY + ROW_H + 2, 12, ROW_H, "▼"));
        }

        y = this.height / 2 + 60;
        this.buttonList.add(new StyledButton(ID_CLEAR, left, y, PANEL_W, BTN_H, "Ryd listen"));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));

        refreshLabels();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String useListLabel() {
        boolean on = cfg() != null && cfg().celleBuyerUseWhitelist;
        return "Kun valgte celler  " + (on
                ? (Style.getAccentFormatting() + "[ TIL ]")
                : (EnumChatFormatting.DARK_GRAY + "[ FRA ]"));
    }

    /** A row shows whether the celle has ever been scanned, since an unseen one gets no box. */
    private String rowLabel(String id) {
        boolean known = CellePositions.get(id) != null;
        return (known ? "" : EnumChatFormatting.DARK_GRAY.toString()) + id
                + (known ? "" : "  (ikke set endnu)")
                + EnumChatFormatting.DARK_GRAY + "   klik = fjern";
    }

    private void refreshLabels() {
        if (useListBtn != null) {
            useListBtn.displayString = useListLabel();
        }
    }

    private void add() {
        String raw = idField.getText() == null ? "" : idField.getText().trim();
        if (raw.isEmpty()) {
            status = "Indtast et celle-id først.";
            statusColor = 0xFF5555;
            return;
        }
        String id = CelleBuyer.normalizeId(raw);
        if (!CelleBuyer.addToWhitelist(cfg(), raw)) {
            status = id + " er allerede på listen.";
            statusColor = 0xFFAA00;
            return;
        }
        idField.setText("");
        if (CellePositions.get(id) == null) {
            status = id + " tilføjet, men ikke scannet endnu, så der er ingen boks endnu.";
            statusColor = 0xFFAA00;
        } else {
            status = id + " tilføjet. Se regnbue-boksen.";
            statusColor = 0x55FF55;
        }
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = cfg();
        if (c == null) {
            return;
        }
        if (button.id >= ROW_BASE) {
            int index = button.id - ROW_BASE;
            if (index >= 0 && index < shown.size()) {
                String id = shown.get(index);
                CelleBuyer.removeFromWhitelist(c, id);
                status = id + " fjernet.";
                statusColor = 0xAAAAAA;
                initGui();
            }
            return;
        }
        switch (button.id) {
            case ID_ADD:
                add();
                return;
            case ID_PASTE:
                String clip = getClipboardString();
                if (clip != null) {
                    idField.setText(clip.trim());
                }
                return;
            case ID_USE_LIST:
                c.celleBuyerUseWhitelist = !c.celleBuyerUseWhitelist;
                c.save();
                refreshLabels();
                return;
            case ID_CLEAR:
                if (c.celleBuyerWhitelist != null) {
                    c.celleBuyerWhitelist.clear();
                }
                c.save();
                scroll = 0;
                status = "Listen er ryddet.";
                statusColor = 0xAAAAAA;
                initGui();
                return;
            case ID_SCROLL_UP:
                scroll = Math.max(0, scroll - 1);
                initGui();
                return;
            case ID_SCROLL_DOWN:
                scroll = Math.min(Math.max(0, picks().size() - VISIBLE_ROWS), scroll + 1);
                initGui();
                return;
            case ID_BACK:
                CelleActions.openCelleBuyer();
                return;
            default:
                return;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            add();
            return;
        }
        idField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        idField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || picks().size() <= VISIBLE_ROWS) {
            return;
        }
        int max = Math.max(0, picks().size() - VISIBLE_ROWS);
        int next = wheel > 0 ? scroll - 1 : scroll + 1;
        next = Math.max(0, Math.min(max, next));
        if (next != scroll) {
            scroll = next;
            initGui();
        }
    }

    @Override
    public void updateScreen() {
        idField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        int halfW = Style.cardHalfWidth(this.width);
        int halfH = Style.cardHalfHeight(this.height);
        int ccx = this.width / 2;
        int ccy = this.height / 2;
        AmbientParticleEngine.INSTANCE.renderBehind(this.width, this.height,
                ccx - halfW, ccy - halfH, ccx + halfW, ccy + halfH, mouseX, mouseY);

        float breathe = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1400.0);
        Style.cardGlow(this.width, this.height, pVal * (0.45f + 0.55f * breathe));

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Valgte celler",
                cx, cy - 138, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Kun disse bliver købt",
                cx, cy - 126, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        idField.drawTextBox();

        int count = picks().size();
        if (count == 0) {
            drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.DARK_GRAY + "Ingen celler valgt endnu",
                    cx, listTopY + 4, 0x888888);
        }

        // The one combination that silently does nothing, called out rather than
        // left for the player to work out from an addon that never fires.
        if (count == 0 && cfg() != null && cfg().celleBuyerUseWhitelist && cfg().celleBuyerEnabled) {
            drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + "Tom liste + \"kun valgte\" = køber ingenting",
                    cx, cy + 44, 0xFF5555);
        } else if (count > 0) {
            drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.GRAY.toString() + count + " valgt" + (count > VISIBLE_ROWS
                            ? "  (scroll for resten)" : ""),
                    cx, cy + 44, 0xAAAAAA);
        }

        if (!status.isEmpty()) {
            drawCenteredString(this.fontRendererObj, status, cx, cy + 112, statusColor);
        }

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
