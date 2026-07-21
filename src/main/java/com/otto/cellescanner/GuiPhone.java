package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Venne Telefon (Friend Phone) GUI: Re-centered Bomb Rush Cyberfunk (BRC) Style.
 * Features:
 * - Snappy cyber slide-in transitions when opening apps.
 * - Dynamic hover micro-animations and glowing neon outlines.
 * - Re-centered display with elevated z-level (zLevel = 500) over scoreboard.
 */
public class GuiPhone extends GuiScreen {

    public enum AppMode {
        HOME, SETTINGS, FRIENDS, MESSAGES
    }

    private static AppMode currentApp = AppMode.HOME;
    private static String selectedFriendForMsg = "";

    // Compact Phone Dimensions
    private static final int PHONE_W = 140;
    private static final int PHONE_H = 220;

    // Animations
    private long openTime;
    private long appOpenTime;
    private static final float ANIM_DURATION_MS = 220.0f;
    private static final float APP_ANIM_MS = 180.0f;

    // Sub-view elements
    private GuiTextField inputField;
    private GuiTextField msgInputField;

    // Relative button references for animation sync
    private GuiButton btnAppSystem;
    private GuiButton btnAppCrew;
    private GuiButton btnAppChat;
    private GuiButton btnBack;

    private GuiButton btnAddFriend;
    private GuiButton btnToggleEsp;
    private GuiButton btnSendMsg;
    private final List<GuiButton> friendMsgBtns = new ArrayList<GuiButton>();

    // Chat History Buffer for iMessage View
    private static final List<ChatMessage> chatHistory = new ArrayList<ChatMessage>();

    public static class ChatMessage {
        public final String sender;
        public final String text;
        public final String time;
        public final boolean isMe;

        public ChatMessage(String sender, String text, boolean isMe) {
            this.sender = sender;
            this.text = text;
            this.isMe = isMe;
            this.time = new SimpleDateFormat("HH:mm").format(new Date());
        }
    }

    public static void addReceivedMessage(String sender, String text) {
        chatHistory.add(new ChatMessage(sender, text, false));
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        long now = System.currentTimeMillis();
        openTime = now;
        appOpenTime = now;

        int phoneX = (width - PHONE_W) / 2;
        int phoneY = (height - PHONE_H) / 2;

        buttonList.clear();
        friendMsgBtns.clear();

        if (currentApp == AppMode.HOME) {
            // Real GuiButton app tiles
            btnAppSystem = new GuiButton(101, phoneX + 10, phoneY + 60, 36, 36, "SYS");
            btnAppCrew = new GuiButton(102, phoneX + 52, phoneY + 60, 36, 36, "CREW");
            btnAppChat = new GuiButton(103, phoneX + 94, phoneY + 60, 36, 36, "CHAT");

            buttonList.add(btnAppSystem);
            buttonList.add(btnAppCrew);
            buttonList.add(btnAppChat);
        } else {
            // Universal Back Button
            btnBack = new GuiButton(99, phoneX + 6, phoneY + 20, 36, 16, "< Back");
            buttonList.add(btnBack);

            if (currentApp == AppMode.SETTINGS) {
                inputField = new GuiTextField(1, fontRendererObj, phoneX + 8, phoneY + 48, 85, 16);
                inputField.setMaxStringLength(16);
                inputField.setFocused(true);

                btnAddFriend = new GuiButton(10, phoneX + 97, phoneY + 47, 35, 18, "Tilfoej");
                btnToggleEsp = new GuiButton(11, phoneX + 8, phoneY + PHONE_H - 38, 124, 18,
                        "Blaa ESP: " + (CelleScannerMod.config.friendEspEnabled ? "TIL" : "FRA"));
                buttonList.add(btnAddFriend);
                buttonList.add(btnToggleEsp);
            } else if (currentApp == AppMode.FRIENDS) {
                List<String> friends = new ArrayList<String>(CelleScannerMod.config.friendsList);
                int startY = phoneY + 44;
                for (int i = 0; i < friends.size(); i++) {
                    int rowY = startY + i * 28;
                    if (rowY + 24 > phoneY + PHONE_H - 12) break;
                    GuiButton btnMsg = new GuiButton(200 + i, phoneX + 98, rowY + 4, 34, 16, "MSG");
                    friendMsgBtns.add(btnMsg);
                    buttonList.add(btnMsg);
                }
            } else if (currentApp == AppMode.MESSAGES) {
                msgInputField = new GuiTextField(2, fontRendererObj, phoneX + 8, phoneY + PHONE_H - 38, 85, 16);
                msgInputField.setMaxStringLength(100);
                msgInputField.setFocused(true);

                btnSendMsg = new GuiButton(20, phoneX + 97, phoneY + PHONE_H - 39, 35, 18, "Send");
                buttonList.add(btnSendMsg);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE || (CelleScannerMod.phoneKey != null && keyCode == CelleScannerMod.phoneKey.getKeyCode())) {
            if (currentApp != AppMode.HOME && keyCode == Keyboard.KEY_ESCAPE) {
                switchApp(AppMode.HOME);
                return;
            } else {
                mc.displayGuiScreen(null);
                return;
            }
        }

        if (currentApp == AppMode.SETTINGS && inputField != null && inputField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                addFriendFromInput();
                return;
            }
            inputField.textboxKeyTyped(typedChar, keyCode);
            return;
        }

        if (currentApp == AppMode.MESSAGES && msgInputField != null && msgInputField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN) {
                sendWhisperFromInput();
                return;
            }
            msgInputField.textboxKeyTyped(typedChar, keyCode);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private void switchApp(AppMode mode) {
        currentApp = mode;
        appOpenTime = System.currentTimeMillis();
        initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 99) { // Back button
            switchApp(AppMode.HOME);
        } else if (button.id == 101) { // Open Settings App
            switchApp(AppMode.SETTINGS);
        } else if (button.id == 102) { // Open Crew App
            switchApp(AppMode.FRIENDS);
        } else if (button.id == 103) { // Open Chat App
            if (selectedFriendForMsg.isEmpty() && !CelleScannerMod.config.friendsList.isEmpty()) {
                selectedFriendForMsg = CelleScannerMod.config.friendsList.iterator().next();
            }
            switchApp(AppMode.MESSAGES);
        } else if (button.id == 10) { // Add Friend in Settings
            addFriendFromInput();
        } else if (button.id == 11) { // Toggle Blue ESP
            CelleScannerMod.config.friendEspEnabled = !CelleScannerMod.config.friendEspEnabled;
            CelleScannerMod.config.save();
            button.displayString = "Blaa ESP: " + (CelleScannerMod.config.friendEspEnabled ? "TIL" : "FRA");
        } else if (button.id == 20) { // Send iMessage Whisper
            sendWhisperFromInput();
        } else if (button.id >= 200 && button.id < 300) { // Quick Msg Friend button
            int idx = button.id - 200;
            List<String> friends = new ArrayList<String>(CelleScannerMod.config.friendsList);
            if (idx >= 0 && idx < friends.size()) {
                selectedFriendForMsg = friends.get(idx);
                switchApp(AppMode.MESSAGES);
            }
        }
    }

    private void addFriendFromInput() {
        if (inputField != null) {
            String name = inputField.getText().trim();
            if (!name.isEmpty()) {
                CelleScannerMod.config.friendsList.add(name);
                CelleScannerMod.config.save();
                inputField.setText("");
                initGui();
            }
        }
    }

    private void sendWhisperFromInput() {
        if (msgInputField != null && !selectedFriendForMsg.isEmpty()) {
            String text = msgInputField.getText().trim();
            if (!text.isEmpty()) {
                mc.thePlayer.sendChatMessage("/whisper " + selectedFriendForMsg + " " + text);
                chatHistory.add(new ChatMessage("Mig", text, true));
                msgInputField.setText("");
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int phoneX = (width - PHONE_W) / 2;
        int targetPhoneY = (height - PHONE_H) / 2;

        float elapsed = System.currentTimeMillis() - openTime;
        float progress = MathHelper.clamp_float(elapsed / ANIM_DURATION_MS, 0.0f, 1.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
        int animYOffset = (int) ((1.0f - ease) * (PHONE_H + 20));
        int curPhoneY = targetPhoneY + animYOffset;

        if (mouseButton == 0) {
            // Settings View: Delete Friend Buttons
            if (currentApp == AppMode.SETTINGS) {
                List<String> friends = new ArrayList<String>(CelleScannerMod.config.friendsList);
                int startY = curPhoneY + 76;
                for (int i = 0; i < friends.size(); i++) {
                    int rowY = startY + i * 18;
                    if (mouseX >= phoneX + 110 && mouseX <= phoneX + 130 && mouseY >= rowY && mouseY <= rowY + 14) {
                        CelleScannerMod.config.friendsList.remove(friends.get(i));
                        CelleScannerMod.config.save();
                        initGui();
                        return;
                    }
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int phoneX = (width - PHONE_W) / 2;
        int targetPhoneY = (height - PHONE_H) / 2;

        long now = System.currentTimeMillis();

        // Phone Slide-Up Opening Animation
        float elapsed = now - openTime;
        float progress = MathHelper.clamp_float(elapsed / ANIM_DURATION_MS, 0.0f, 1.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - progress, 3);
        int animYOffset = (int) ((1.0f - ease) * (PHONE_H + 20));
        int curPhoneY = targetPhoneY + animYOffset;

        // App Opening Slide Animation (Snappy Horizontal Wipe)
        float appElapsed = now - appOpenTime;
        float appProgress = MathHelper.clamp_float(appElapsed / APP_ANIM_MS, 0.0f, 1.0f);
        float appEase = 1.0f - (float) Math.pow(1.0f - appProgress, 3);
        int appSlideX = (int) ((1.0f - appEase) * 35.0f);

        // --- Synchronize GuiButton Y-positions & Hover Micro-Animations ---
        if (currentApp == AppMode.HOME) {
            int baseY = curPhoneY + 60;
            if (btnAppSystem != null) {
                btnAppSystem.yPosition = (mouseX >= btnAppSystem.xPosition && mouseX <= btnAppSystem.xPosition + 36 && mouseY >= baseY && mouseY <= baseY + 36) ? baseY - 2 : baseY;
            }
            if (btnAppCrew != null) {
                btnAppCrew.yPosition = (mouseX >= btnAppCrew.xPosition && mouseX <= btnAppCrew.xPosition + 36 && mouseY >= baseY && mouseY <= baseY + 36) ? baseY - 2 : baseY;
            }
            if (btnAppChat != null) {
                btnAppChat.yPosition = (mouseX >= btnAppChat.xPosition && mouseX <= btnAppChat.xPosition + 36 && mouseY >= baseY && mouseY <= baseY + 36) ? baseY - 2 : baseY;
            }
        } else {
            if (btnBack != null) btnBack.yPosition = curPhoneY + 20 + appSlideX / 4;

            if (currentApp == AppMode.SETTINGS) {
                if (btnAddFriend != null) btnAddFriend.yPosition = curPhoneY + 47;
                if (btnToggleEsp != null) btnToggleEsp.yPosition = curPhoneY + PHONE_H - 38;
            } else if (currentApp == AppMode.FRIENDS) {
                int startY = curPhoneY + 44;
                for (int i = 0; i < friendMsgBtns.size(); i++) {
                    friendMsgBtns.get(i).yPosition = startY + i * 28 + 4;
                }
            } else if (currentApp == AppMode.MESSAGES) {
                if (btnSendMsg != null) btnSendMsg.yPosition = curPhoneY + PHONE_H - 39;
            }
        }

        // --- Elevate Z-Index so Phone renders ON TOP of Minecraft Scoreboard & HUD ---
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 500.0f);
        zLevel = 500.0f;

        // --- Cyber Phone Outer Frame ---
        drawRect(phoneX - 5, curPhoneY - 5, phoneX + PHONE_W + 5, curPhoneY + PHONE_H + 5, 0xFF0A0D14);
        drawRect(phoneX - 5, curPhoneY + 24, phoneX - 2, curPhoneY + PHONE_H - 24, 0xFF00E676); // Emerald Bumper
        drawRect(phoneX + PHONE_W + 2, curPhoneY + 24, phoneX + PHONE_W + 5, curPhoneY + PHONE_H - 24, 0xFF00E676); // Emerald Bumper

        // Azure Inner Glow Accent
        drawRect(phoneX - 2, curPhoneY - 2, phoneX + PHONE_W + 2, curPhoneY + PHONE_H + 2, 0xFF00E5FF);
        // OLED Screen Background
        drawRect(phoneX, curPhoneY, phoneX + PHONE_W, curPhoneY + PHONE_H, 0xFF060911);

        // --- Status Bar (Top Header) ---
        drawRect(phoneX, curPhoneY, phoneX + PHONE_W, curPhoneY + 16, 0xFF121824);
        String timeStr = new SimpleDateFormat("HH:mm").format(new Date());
        fontRendererObj.drawStringWithShadow(timeStr, phoneX + 4, curPhoneY + 4, 0xFF00E676);
        fontRendererObj.drawStringWithShadow("BRC-5G", phoneX + PHONE_W - 52, curPhoneY + 4, 0xFF00E5FF);

        // Render Current App Content Backgrounds with App Open Slide Transition
        int renderX = phoneX + appSlideX;
        switch (currentApp) {
            case HOME:
                drawHomeScreen(phoneX, curPhoneY, mouseX, mouseY);
                break;
            case SETTINGS:
                drawSettingsScreen(renderX, curPhoneY, mouseX, mouseY);
                break;
            case FRIENDS:
                drawFriendsScreen(renderX, curPhoneY, mouseX, mouseY);
                break;
            case MESSAGES:
                drawMessagesScreen(renderX, curPhoneY, mouseX, mouseY);
                break;
        }

        // --- Cyber Home Bar ---
        drawRect(phoneX + PHONE_W / 2 - 22, curPhoneY + PHONE_H - 6, phoneX + PHONE_W / 2 + 22, curPhoneY + PHONE_H - 3, 0xFF00E676);

        // Draw active input fields
        if (currentApp == AppMode.SETTINGS && inputField != null) {
            inputField.xPosition = renderX + 8;
            inputField.yPosition = curPhoneY + 48;
            inputField.drawTextBox();
        } else if (currentApp == AppMode.MESSAGES && msgInputField != null) {
            msgInputField.xPosition = renderX + 8;
            msgInputField.yPosition = curPhoneY + PHONE_H - 38;
            msgInputField.drawTextBox();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Restore Z-layer
        zLevel = 0.0f;
        GlStateManager.popMatrix();
    }

    private void drawHomeScreen(int px, int py, int mouseX, int mouseY) {
        // Deep Midnight Navy to Purple Wallpaper
        drawGradientRect(px + 2, py + 18, px + PHONE_W - 2, py + PHONE_H - 8, 0xFF0A1128, 0xFF1C0A35);

        // BRC Streetwear Header Title Banner with Cyber Yellow Accent
        drawRect(px + 6, py + 24, px + PHONE_W - 6, py + 42, 0xEE0A0D14);
        drawRect(px + 6, py + 41, px + PHONE_W - 6, py + 43, 0xFFFFE600); // Yellow BRC Accent Line
        fontRendererObj.drawStringWithShadow("BRC // VENNER", px + 18, py + 30, 0xFF00E5FF);

        // Hover Neon Outlines for App Buttons
        if (btnAppSystem != null && mouseX >= btnAppSystem.xPosition && mouseX <= btnAppSystem.xPosition + 36 && mouseY >= py + 60 && mouseY <= py + 96) {
            drawRect(btnAppSystem.xPosition - 2, btnAppSystem.yPosition - 2, btnAppSystem.xPosition + 38, btnAppSystem.yPosition + 38, 0xFF00E676);
        }
        if (btnAppCrew != null && mouseX >= btnAppCrew.xPosition && mouseX <= btnAppCrew.xPosition + 36 && mouseY >= py + 60 && mouseY <= py + 96) {
            drawRect(btnAppCrew.xPosition - 2, btnAppCrew.yPosition - 2, btnAppCrew.xPosition + 38, btnAppCrew.yPosition + 38, 0xFF00E5FF);
        }
        if (btnAppChat != null && mouseX >= btnAppChat.xPosition && mouseX <= btnAppChat.xPosition + 36 && mouseY >= py + 60 && mouseY <= py + 96) {
            drawRect(btnAppChat.xPosition - 2, btnAppChat.yPosition - 2, btnAppChat.xPosition + 38, btnAppChat.yPosition + 38, 0xFFD500F9);
        }
    }

    private void drawSettingsScreen(int px, int py, int mouseX, int mouseY) {
        drawRect(px, py + 16, px + PHONE_W, py + 38, 0xFF121824);
        fontRendererObj.drawStringWithShadow("SYSTEM", px + 54, py + 22, 0xFF00E676);

        fontRendererObj.drawString("Tilfoej ven:", px + 8, py + 38, 0xFFAAAAAA);
        fontRendererObj.drawString("Dine Venner:", px + 8, py + 66, 0xFFFFFFFF);

        List<String> friends = new ArrayList<String>(CelleScannerMod.config.friendsList);
        int startY = py + 76;
        for (int i = 0; i < friends.size(); i++) {
            int rowY = startY + i * 18;
            if (rowY + 16 > py + PHONE_H - 42) break;

            drawRect(px + 8, rowY, px + PHONE_W - 8, rowY + 14, 0xFF121824);
            fontRendererObj.drawString(friends.get(i), px + 12, rowY + 3, 0xFF00E5FF);

            // Delete Button
            drawRect(px + 110, rowY + 1, px + 130, rowY + 13, 0xFFD500F9);
            fontRendererObj.drawString("DEL", px + 113, rowY + 3, 0xFFFFFFFF);
        }
    }

    private void drawFriendsScreen(int px, int py, int mouseX, int mouseY) {
        drawRect(px, py + 16, px + PHONE_W, py + 38, 0xFF121824);
        fontRendererObj.drawStringWithShadow("CREW ONLINE", px + 48, py + 22, 0xFF00E5FF);

        List<String> friends = new ArrayList<String>(CelleScannerMod.config.friendsList);
        if (friends.isEmpty()) {
            fontRendererObj.drawString("Ingen venner endnu.", px + 10, py + 48, 0xFFAAAAAA);
            fontRendererObj.drawString("Go til SYSTEM.", px + 10, py + 62, 0xFF00E676);
            return;
        }

        int startY = py + 44;
        for (int i = 0; i < friends.size(); i++) {
            int rowY = startY + i * 28;
            if (rowY + 24 > py + PHONE_H - 12) break;

            String name = friends.get(i);
            EntityPlayer player = mc.theWorld != null ? mc.theWorld.getPlayerEntityByName(name) : null;
            boolean online = player != null;

            drawRect(px + 6, rowY, px + PHONE_W - 6, rowY + 24, 0xFF121824);

            int dotColor = online ? 0xFF00E676 : 0xFF8E8E93;
            drawRect(px + 10, rowY + 9, px + 14, rowY + 13, dotColor);

            fontRendererObj.drawString(name, px + 18, rowY + 3, 0xFFFFFFFF);

            if (online) {
                double dist = mc.thePlayer.getDistanceToEntity(player);
                fontRendererObj.drawString(String.format("%.1fm", dist), px + 18, rowY + 13, 0xFF00E676);
            } else {
                fontRendererObj.drawString("Offline", px + 18, rowY + 13, 0xFF8E8E93);
            }
        }
    }

    private void drawMessagesScreen(int px, int py, int mouseX, int mouseY) {
        drawRect(px, py + 16, px + PHONE_W, py + 38, 0xFF121824);
        String targetName = selectedFriendForMsg.isEmpty() ? "Vaelg" : selectedFriendForMsg;
        fontRendererObj.drawStringWithShadow("Til: " + targetName, px + 48, py + 22, 0xFFD500F9);

        int chatBoxY = py + 40;
        drawRect(px + 6, chatBoxY, px + PHONE_W - 6, py + PHONE_H - 42, 0xFF060911);

        int startY = py + PHONE_H - 58;
        int count = 0;
        for (int i = chatHistory.size() - 1; i >= 0; i--) {
            ChatMessage msg = chatHistory.get(i);
            int msgY = startY - count * 18;
            if (msgY < chatBoxY + 3) break;

            if (msg.isMe) {
                drawRect(px + 40, msgY, px + PHONE_W - 10, msgY + 14, 0xFFD500F9);
                fontRendererObj.drawString(msg.text, px + 44, msgY + 3, 0xFFFFFFFF);
            } else {
                drawRect(px + 10, msgY, px + PHONE_W - 40, msgY + 14, 0xFF121824);
                fontRendererObj.drawString(msg.text, px + 14, msgY + 3, 0xFFFFFFFF);
            }
            count++;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
