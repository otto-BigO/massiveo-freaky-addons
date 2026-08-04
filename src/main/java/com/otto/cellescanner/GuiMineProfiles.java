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
 * Mine profiles for Auto Mine. Each profile is a named mine with its own area
 * and its own shop, deposit and iron positions, so a second mine is a second
 * profile rather than re-teaching the bot every time.
 */
public class GuiMineProfiles extends GuiScreen {

    private static final int ID_NAME_SAVE = 0;
    private static final int ID_NEW = 1;
    private static final int ID_DELETE = 2;
    private static final int ID_SET_AREA = 3;
    private static final int ID_SET_SHOP = 4;
    private static final int ID_SET_DEPOSIT = 5;
    private static final int ID_SET_IRON = 6;
    private static final int ID_BACK = 7;
    private static final int ID_SCROLL_UP = 8;
    private static final int ID_SCROLL_DOWN = 9;
    private static final int ROW_BASE = 100;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;
    private static final int ROW_H = 16;
    private static final int GAP = 3;
    private static final int VISIBLE_ROWS = 4;

    private GuiTextField nameField;
    private final List<Integer> shownIndexes = new ArrayList<Integer>();
    private int scroll = 0;
    private int listTopY;

    private String status = "";
    private int statusColor = 0xAAAAAA;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    private static CelleConfig cfg() {
        return MassiveOsFreakyAddons.config;
    }

    private static List<MineProfile> profiles() {
        CelleConfig c = cfg();
        if (c == null) {
            return new ArrayList<MineProfile>();
        }
        c.migrateMineProfiles();
        return c.mineProfiles;
    }

    private static MineProfile active() {
        CelleConfig c = cfg();
        return c == null ? null : c.activeMineProfile();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        shownIndexes.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int y = this.height / 2 - 112;

        // Name of the selected profile, editable in place.
        int smallW = 56;
        MineProfile a = active();
        nameField = new GuiTextField(0, this.fontRendererObj, left, y, PANEL_W - smallW - GAP, BTN_H);
        nameField.setMaxStringLength(24);
        nameField.setText(a == null ? "" : a.displayName());
        nameField.setFocused(true);
        this.buttonList.add(new StyledButton(ID_NAME_SAVE, left + PANEL_W - smallW, y - 1, smallW, BTN_H + 2, "Omdøb"));
        y += BTN_H + GAP + 5;

        // The list of mines. Clicking one selects it.
        int max = Math.max(0, profiles().size() - VISIBLE_ROWS);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;

        listTopY = y;
        List<MineProfile> all = profiles();
        int rowW = all.size() > VISIBLE_ROWS ? PANEL_W - 14 : PANEL_W;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scroll + i;
            if (index >= all.size()) break;
            shownIndexes.add(index);
            this.buttonList.add(new StyledButton(ROW_BASE + i, left, y, rowW, ROW_H, rowLabel(index)));
            y += ROW_H + 2;
        }
        if (all.size() > VISIBLE_ROWS) {
            this.buttonList.add(new StyledButton(ID_SCROLL_UP, left + PANEL_W - 12, listTopY, 12, ROW_H, "▲"));
            this.buttonList.add(new StyledButton(ID_SCROLL_DOWN, left + PANEL_W - 12, listTopY + ROW_H + 2, 12, ROW_H, "▼"));
        }

        y = this.height / 2 - 28;
        int halfW = (PANEL_W - GAP) / 2;
        this.buttonList.add(new StyledButton(ID_NEW, left, y, halfW, BTN_H, "Ny mine"));
        this.buttonList.add(new StyledButton(ID_DELETE, left + halfW + GAP, y, halfW, BTN_H, "Slet"));
        y += BTN_H + GAP + 6;

        // The four positions this profile needs.
        this.buttonList.add(new StyledButton(ID_SET_AREA, left, y, PANEL_W, BTN_H, areaLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_SET_SHOP, left, y, PANEL_W, BTN_H, shopLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_SET_DEPOSIT, left, y, PANEL_W, BTN_H, depositLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_SET_IRON, left, y, PANEL_W, BTN_H, ironLabel()));
        y += BTN_H + GAP + 6;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String rowLabel(int index) {
        MineProfile p = profiles().get(index);
        boolean sel = cfg() != null && cfg().mineProfileIndex == index;
        String mark = sel ? (Style.getAccentFormatting() + "> ") : (EnumChatFormatting.DARK_GRAY + "  ");
        return mark + EnumChatFormatting.RESET + p.displayName()
                + EnumChatFormatting.DARK_GRAY + "   " + p.configuredCount() + "/4";
    }

    /** A position button shows what is set, so the screen doubles as the summary. */
    private String posLabel(String title, boolean set, int x, int y, int z) {
        if (!set) {
            return title + "  " + EnumChatFormatting.DARK_GRAY + "(ikke sat)";
        }
        return title + "  " + Style.getAccentFormatting() + x + " " + y + " " + z;
    }

    private String areaLabel() {
        MineProfile p = active();
        if (p == null || !p.areaSet) {
            return "Mine-område  " + EnumChatFormatting.DARK_GRAY + "(standard)";
        }
        int[] b = p.bounds();
        int vol = (b[1] - b[0] + 1) * (b[3] - b[2] + 1) * (b[5] - b[4] + 1);
        return "Mine-område  " + Style.getAccentFormatting() + vol + " blokke";
    }

    private String shopLabel() {
        MineProfile p = active();
        return p == null ? "Pikaxe-skilt"
                : posLabel("Pikaxe-skilt", p.shopSet, p.shopX, p.shopY, p.shopZ);
    }

    private String depositLabel() {
        MineProfile p = active();
        return p == null ? "Skraldespand"
                : posLabel("Skraldespand", p.depositSet, p.depositX, p.depositY, p.depositZ);
    }

    private String ironLabel() {
        MineProfile p = active();
        return p == null ? "Jern-aflevering"
                : posLabel("Jern-aflevering", p.ironSet, p.ironX, p.ironY, p.ironZ);
    }

    private void saveName() {
        MineProfile a = active();
        if (a == null) return;
        String n = nameField.getText() == null ? "" : nameField.getText().trim();
        if (n.isEmpty()) {
            status = "Navnet kan ikke være tomt.";
            statusColor = 0xFF5555;
            return;
        }
        a.name = n;
        cfg().save();
        status = "Omdøbt til \"" + n + "\".";
        statusColor = 0x55FF55;
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = cfg();
        if (c == null) return;

        if (button.id >= ROW_BASE) {
            int slot = button.id - ROW_BASE;
            if (slot >= 0 && slot < shownIndexes.size()) {
                c.mineProfileIndex = shownIndexes.get(slot);
                c.save();
                // The box the bot mines comes from the profile, so switching
                // profile has to throw away the plan built against the old one.
                AutoMine.invalidatePlan();
                status = "Valgte \"" + active().displayName() + "\".";
                statusColor = 0x55FF55;
                initGui();
            }
            return;
        }

        switch (button.id) {
            case ID_NAME_SAVE:
                saveName();
                return;
            case ID_NEW: {
                MineProfile p = new MineProfile("Mine " + (profiles().size() + 1));
                c.mineProfiles.add(p);
                c.mineProfileIndex = c.mineProfiles.size() - 1;
                c.save();
                AutoMine.invalidatePlan();
                scroll = Math.max(0, c.mineProfiles.size() - VISIBLE_ROWS);
                status = "Ny mine oprettet. Sæt område og skilte.";
                statusColor = 0x55FF55;
                initGui();
                return;
            }
            case ID_DELETE: {
                if (profiles().size() <= 1) {
                    // Something has to be selected, so the last one cannot go.
                    status = "Der skal være mindst én mine.";
                    statusColor = 0xFFAA00;
                    return;
                }
                String gone = active().displayName();
                c.mineProfiles.remove(c.mineProfileIndex);
                if (c.mineProfileIndex >= c.mineProfiles.size()) {
                    c.mineProfileIndex = c.mineProfiles.size() - 1;
                }
                c.save();
                AutoMine.invalidatePlan();
                status = "\"" + gone + "\" slettet.";
                statusColor = 0xAAAAAA;
                initGui();
                return;
            }
            case ID_SET_AREA:
                AutoMine.beginSetArea();
                return;
            case ID_SET_SHOP:
                AutoMine.beginPick(AutoMine.PICK_SHOP, "skiltet hvor du køber pikaxe");
                return;
            case ID_SET_DEPOSIT:
                AutoMine.beginPick(AutoMine.PICK_DEPOSIT, "skraldespanden");
                return;
            case ID_SET_IRON:
                AutoMine.beginPick(AutoMine.PICK_IRON, "stedet hvor jern afleveres");
                return;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiAutoMineSettings());
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
            saveName();
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
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || profiles().size() <= VISIBLE_ROWS) {
            return;
        }
        int max = Math.max(0, profiles().size() - VISIBLE_ROWS);
        int next = Math.max(0, Math.min(max, wheel > 0 ? scroll - 1 : scroll + 1));
        if (next != scroll) {
            scroll = next;
            initGui();
        }
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

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Miner",
                cx, cy - 138, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Hver mine har sit eget område og sine egne skilte",
                cx, cy - 126, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        nameField.drawTextBox();

        if (!status.isEmpty()) {
            drawCenteredString(this.fontRendererObj, status, cx, cy + 122, statusColor);
        }

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
