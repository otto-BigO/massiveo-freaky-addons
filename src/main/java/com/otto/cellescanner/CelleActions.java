package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Iterator;

/**
 * Shared action logic used by BOTH the typed chat commands (/celler ...)
 * and the button GUI (GuiCelleMenu), so the two are always in sync and
 * every action - whether clicked or typed - produces the same chat
 * feedback (which also makes it show up in latest.log for debugging).
 */
public final class CelleActions {

    private CelleActions() {
    }

    public static void toggleEnabled() {
        CelleScannerMod.config.enabled = !CelleScannerMod.config.enabled;
        CelleScannerMod.config.save();
        message("Celle Scanner er nu " + (CelleScannerMod.config.enabled ? "aktiveret" : "deaktiveret") + ".");
    }

    public static void toggleNotify() {
        CelleScannerMod.config.notify = !CelleScannerMod.config.notify;
        CelleScannerMod.config.save();
        message("Notifikationer er nu " + (CelleScannerMod.config.notify ? "til" : "fra") + ".");
    }

    public static void reloadConfig() {
        CelleScannerMod.config.load();
        GangRanges.load();
        message("Konfiguration genindlæst. minHours=" + CelleScannerMod.config.minHours
                + " maxHours=" + CelleScannerMod.config.maxHours
                + " notify=" + (CelleScannerMod.config.notify ? "til" : "fra")
                + ", gang-ranges=" + GangRanges.count());
    }

    public static void clearCache() {
        CelleScannerMod.scanner.clear();
        message("Cache ryddet.");
    }

    /**
     * Dumps the raw text of the celle sign you're looking at (or the nearest
     * sign if you're not looking at one), all four lines with their formatting
     * codes shown as '&'. Diagnostic: lets us see whether a sign carries any
     * gang/lokation info we aren't already parsing (it only has 4 lines:
     * status, owner, id, timer).
     */
    public static void dumpNearestSign() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            message("Ingen verden.");
            return;
        }
        net.minecraft.tileentity.TileEntitySign target = null;

        // Prefer the sign you're pointing at.
        if (mc.objectMouseOver != null && mc.objectMouseOver.getBlockPos() != null) {
            net.minecraft.tileentity.TileEntity te = mc.theWorld.getTileEntity(mc.objectMouseOver.getBlockPos());
            if (te instanceof net.minecraft.tileentity.TileEntitySign) {
                target = (net.minecraft.tileentity.TileEntitySign) te;
            }
        }
        // Otherwise the closest loaded sign.
        if (target == null) {
            double best = Double.MAX_VALUE;
            for (Object o : mc.theWorld.loadedTileEntityList) {
                if (!(o instanceof net.minecraft.tileentity.TileEntitySign)) {
                    continue;
                }
                net.minecraft.tileentity.TileEntitySign s = (net.minecraft.tileentity.TileEntitySign) o;
                double d = mc.thePlayer.getDistanceSq(s.getPos().getX() + 0.5, s.getPos().getY() + 0.5, s.getPos().getZ() + 0.5);
                if (d < best) {
                    best = d;
                    target = s;
                }
            }
        }
        if (target == null) {
            message("Ingen skilte i nærheden.");
            return;
        }

        message("Skilt @ " + target.getPos().getX() + "," + target.getPos().getY() + "," + target.getPos().getZ() + ":");
        for (int i = 0; i < target.signText.length; i++) {
            String raw = target.signText[i] == null ? "" : target.signText[i].getFormattedText();
            raw = raw.replace('§', '&');
            message("  [" + (i + 1) + "] \"" + raw + "\"");
        }
    }

    public static void debugDump() {
        int count = CelleScannerMod.scanner.getCache().size();
        message("Sporer " + count + " celler. minHours=" + CelleScannerMod.config.minHours
                + " maxHours=" + CelleScannerMod.config.maxHours
                + " notify=" + (CelleScannerMod.config.notify ? "til" : "fra"));
        for (Celle c : CelleScannerMod.scanner.getCache().values()) {
            String ownerPart = c.owner != null ? " ejer=" + c.owner : "";
            message(" - " + c.celleId + " [" + c.status + "] "
                    + CelleHud.formatDuration(c)
                    + " (" + String.format("%.2f", c.liveRemainingHours()) + "t live, sidst læst fra skilt: "
                    + CelleHud.formatDuration(c.remainingSeconds) + ")"
                    + " bekræftet=" + c.timerConfirmed
                    + " underrettet=" + c.notified
                    + " special=" + CelleScannerMod.config.isSpecial(c.celleId) + ownerPart);
        }
    }

    public static void adjustMinHours(int delta) {
        CelleScannerMod.config.minHours = Math.max(0, Math.min(CelleScannerMod.config.maxHours, CelleScannerMod.config.minHours + delta));
        CelleScannerMod.config.save();
        message("Min timer: " + CelleScannerMod.config.minHours);
    }

    public static void adjustMaxHours(int delta) {
        CelleScannerMod.config.maxHours = Math.max(CelleScannerMod.config.minHours, CelleScannerMod.config.maxHours + delta);
        CelleScannerMod.config.save();
        message("Max timer: " + CelleScannerMod.config.maxHours);
    }

    public static void setMinHours(int value) {
        CelleScannerMod.config.minHours = Math.max(0, Math.min(CelleScannerMod.config.maxHours, value));
        CelleScannerMod.config.save();
        message("Min timer: " + CelleScannerMod.config.minHours);
    }

    public static void setMaxHours(int value) {
        CelleScannerMod.config.maxHours = Math.max(CelleScannerMod.config.minHours, value);
        CelleScannerMod.config.save();
        message("Max timer: " + CelleScannerMod.config.maxHours);
    }

    public static void adjustMaxHudEntries(int delta) {
        CelleScannerMod.config.maxHudEntries = Math.max(1, Math.min(50, CelleScannerMod.config.maxHudEntries + delta));
        CelleScannerMod.config.save();
        message("Maks HUD-linjer: " + CelleScannerMod.config.maxHudEntries);
    }

    public static void toggleEsp() {
        CelleScannerMod.config.espEnabled = !CelleScannerMod.config.espEnabled;
        CelleScannerMod.config.save();
        message("ESP-outline er nu " + (CelleScannerMod.config.espEnabled ? "til" : "fra") + ".");
    }

    public static void toggleShowSeconds() {
        CelleScannerMod.config.showSeconds = !CelleScannerMod.config.showSeconds;
        CelleScannerMod.config.save();
        message("Vis sekunder: " + (CelleScannerMod.config.showSeconds ? "til" : "fra"));
    }

    public static void toggleShowOwner() {
        CelleScannerMod.config.showOwner = !CelleScannerMod.config.showOwner;
        CelleScannerMod.config.save();
        message("Vis ejernavn: " + (CelleScannerMod.config.showOwner ? "til" : "fra"));
    }

    public static void toggleShowStatusTag() {
        CelleScannerMod.config.showStatusTag = !CelleScannerMod.config.showStatusTag;
        CelleScannerMod.config.save();
        message("Vis status-mærke: " + (CelleScannerMod.config.showStatusTag ? "til" : "fra"));
    }

    public static void toggleShowDistance() {
        CelleScannerMod.config.showDistance = !CelleScannerMod.config.showDistance;
        CelleScannerMod.config.save();
        message("Vis afstand: " + (CelleScannerMod.config.showDistance ? "til" : "fra"));
    }

    public static void toggleEspLabels() {
        CelleScannerMod.config.espLabels = !CelleScannerMod.config.espLabels;
        CelleScannerMod.config.save();
        message("ESP celle-id label: " + (CelleScannerMod.config.espLabels ? "til" : "fra"));
    }

    public static void toggleBotReport() {
        CelleScannerMod.config.botReportEnabled = !CelleScannerMod.config.botReportEnabled;
        CelleScannerMod.config.save();
        message("Bot-rapportering er nu " + (CelleScannerMod.config.botReportEnabled ? "til" : "fra") + ".");
    }

    public static void setReportsWebhookUrl(String url) {
        CelleScannerMod.config.reportsWebhookUrl = url;
        CelleScannerMod.config.botReportEnabled = true;
        CelleScannerMod.config.save();
        message("Webhook-url gemt og aktiveret.");
    }

    public static void disableBotReport() {
        CelleScannerMod.config.botReportEnabled = false;
        CelleScannerMod.config.save();
        message("Bot-rapportering deaktiveret.");
    }

    public static void clearBotReport() {
        CelleScannerMod.config.botReportEnabled = false;
        CelleScannerMod.config.reportsWebhookUrl = "";
        CelleScannerMod.config.save();
        message("Webhook-forbindelse ryddet.");
    }

    public static void testBotConnection() {
        if (CelleScannerMod.config.reportsWebhookUrl == null || CelleScannerMod.config.reportsWebhookUrl.trim().isEmpty()) {
            message("Ingen webhook-url sat. Brug /celler bot <url> først.");
            return;
        }
        message("Sender test-rapport til webhook...");
        ReportWebhookClient.testConnection();
    }

    public static void adjustEspMaxDistance(double delta) {
        double v = CelleScannerMod.config.espMaxDistance + delta;
        CelleScannerMod.config.espMaxDistance = Math.max(8.0, v);
        CelleScannerMod.config.save();
        message("ESP maks-afstand: " + String.format("%.0fm", CelleScannerMod.config.espMaxDistance));
    }

    public static void setEspMaxDistance(double value) {
        CelleScannerMod.config.espMaxDistance = Math.max(8.0, value);
        CelleScannerMod.config.save();
        message("ESP maks-afstand: " + String.format("%.0fm", CelleScannerMod.config.espMaxDistance));
    }

    public static void addSpecialCelle(String id) {
        String trimmed = id == null ? "" : id.trim();
        if (trimmed.isEmpty()) {
            message("Ugyldigt celle-id.");
            return;
        }
        if (CelleScannerMod.config.isSpecial(trimmed)) {
            message("Celle " + trimmed + " er allerede på special-listen.");
            return;
        }
        CelleScannerMod.config.specialCelleIds.add(trimmed);
        CelleScannerMod.config.save();
        message("Celle " + trimmed + " tilføjet som special.");
    }

    public static void removeSpecialCelle(String id) {
        String trimmed = id == null ? "" : id.trim();
        boolean removed = false;
        Iterator<String> it = CelleScannerMod.config.specialCelleIds.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(trimmed)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            CelleScannerMod.config.save();
            message("Celle " + trimmed + " fjernet fra special-listen.");
        } else {
            message("Celle " + trimmed + " var ikke på special-listen.");
        }
    }

    public static void clearSpecialCelles() {
        CelleScannerMod.config.specialCelleIds.clear();
        CelleScannerMod.config.save();
        message("Special-listen er ryddet.");
    }

    public static void listSpecialCelles() {
        if (CelleScannerMod.config.specialCelleIds.isEmpty()) {
            message("Ingen special-celler sat. Brug /celler special add <id>.");
            return;
        }
        message("Special-celler (" + CelleScannerMod.config.specialCelleIds.size() + "):");
        for (String id : CelleScannerMod.config.specialCelleIds) {
            message(" - " + id);
        }
    }

    public static void openSpecialScreen() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleSpecial());
    }

    public static void openMover() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleHudMover());
    }

    public static void openHub() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiAddonsHub());
    }

    public static void openAntiAfk() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiAntiAfk());
    }

    public static void openBande() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiBande());
    }

    public static void openChestAlarm() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiChestAlarm());
    }

    public static void openArmorSkins() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiArmorSkins());
    }

    public static void openMineCeller() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiMineCeller());
    }

    public static void openGange() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiGange());
    }

    public static void openPlayerInfoMenu() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPlayerInfoMenu());
    }

    public static void togglePlayerInfo() {
        CelleScannerMod.config.playerInfoEnabled = !CelleScannerMod.config.playerInfoEnabled;
        CelleScannerMod.config.save();
        message("Spiller Info: " + (CelleScannerMod.config.playerInfoEnabled ? "til" : "fra"));
    }

    public static void toggleGangAutoQuery() {
        CelleScannerMod.config.gangAutoQuery = !CelleScannerMod.config.gangAutoQuery;
        CelleScannerMod.config.save();
        message("Gange auto-hentning: " + (CelleScannerMod.config.gangAutoQuery ? "til" : "fra"));
    }

    public static void openItemValues() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiItemValues());
    }

    public static void openPriceGuide() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPriceGuide());
    }

    public static void openUpdate() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiUpdate());
    }

    public static void toggleAutoUpdate() {
        CelleScannerMod.config.autoUpdateEnabled = !CelleScannerMod.config.autoUpdateEnabled;
        CelleScannerMod.config.save();
        message("Auto-opdatering er nu " + (CelleScannerMod.config.autoUpdateEnabled ? "til" : "fra") + ".");
    }

    public static void toggleUpdatePreRelease() {
        CelleScannerMod.config.autoUpdatePreRelease = !CelleScannerMod.config.autoUpdatePreRelease;
        CelleScannerMod.config.save();
        message("Opdater til pre-releases (test): " + (CelleScannerMod.config.autoUpdatePreRelease ? "til" : "fra"));
    }

    public static void checkForUpdateNow() {
        AutoUpdater.checkAsync();
        message("Tjekker for opdatering...");
    }

    public static void toggleItemValues() {
        CelleScannerMod.config.itemValueEnabled = !CelleScannerMod.config.itemValueEnabled;
        CelleScannerMod.config.save();
        message("Item værdi i tooltip: " + (CelleScannerMod.config.itemValueEnabled ? "til" : "fra"));
    }

    public static void reloadItemValues() {
        ItemValues.load();
        message("Priser genindlæst: " + ItemValues.count() + " varer.");
    }

    public static void fetchMyCeller() {
        MineCeller.fetch();
    }

    public static void toggleMineCellerEsp() {
        CelleScannerMod.config.mineCellerEspEnabled = !CelleScannerMod.config.mineCellerEspEnabled;
        CelleScannerMod.config.save();
        message("Mine celler ESP: " + (CelleScannerMod.config.mineCellerEspEnabled ? "til" : "fra"));
    }

    public static void addMyCelle(String id) {
        String trimmed = id == null ? "" : id.trim();
        if (trimmed.isEmpty()) {
            message("Ugyldigt celle-id.");
            return;
        }
        if (CelleScannerMod.config.isMyCelle(trimmed)) {
            message("Celle " + trimmed + " er allerede på listen.");
            return;
        }
        CelleScannerMod.config.myCelleIds.add(trimmed);
        CelleScannerMod.config.save();
        message("Celle " + trimmed + " tilføjet til mine celler.");
    }

    public static void removeMyCelle(String id) {
        String trimmed = id == null ? "" : id.trim();
        boolean removed = false;
        Iterator<String> it = CelleScannerMod.config.myCelleIds.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(trimmed)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            CelleScannerMod.config.save();
            message("Celle " + trimmed + " fjernet fra mine celler.");
        }
    }

    public static void clearMyCeller() {
        CelleScannerMod.config.myCelleIds.clear();
        CelleScannerMod.config.save();
        message("Mine celler-listen er ryddet.");
    }

    public static void toggleArmorSkins() {
        CelleScannerMod.config.armorSkinsEnabled = !CelleScannerMod.config.armorSkinsEnabled;
        CelleScannerMod.config.save();
        message("Rustnings-skins er nu " + (CelleScannerMod.config.armorSkinsEnabled ? "til" : "fra") + ".");
    }

    public static void cycleArmorPack() {
        CelleScannerMod.config.armorSkinPack = "hypixel".equals(CelleScannerMod.config.armorSkinPack) ? "mesterholm" : "hypixel";
        CelleScannerMod.config.save();
        message("Rustnings-tekstur-pack: " + armorPackName());
    }

    public static String armorPackName() {
        return "hypixel".equals(CelleScannerMod.config.armorSkinPack) ? "Hypixel+" : "MesterHolm";
    }

    public static void openArmorHud() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiArmorHud());
    }

    public static void toggleArmorHud() {
        CelleScannerMod.config.armorHudEnabled = !CelleScannerMod.config.armorHudEnabled;
        CelleScannerMod.config.save();
        message("Rustnings-HUD er nu " + (CelleScannerMod.config.armorHudEnabled ? "til" : "fra") + ".");
    }

    public static void adjustArmorHudWarn(int delta) {
        CelleScannerMod.config.armorHudWarnPercent = Math.max(1, Math.min(90, CelleScannerMod.config.armorHudWarnPercent + delta));
        CelleScannerMod.config.save();
        message("Rustnings-HUD advarsel: under " + CelleScannerMod.config.armorHudWarnPercent + "%");
    }

    public static void toggleChestAlarm() {
        CelleScannerMod.config.chestAlarmEnabled = !CelleScannerMod.config.chestAlarmEnabled;
        CelleScannerMod.config.save();
        message("Chest Alarm er nu " + (CelleScannerMod.config.chestAlarmEnabled ? "til" : "fra") + ".");
    }

    public static void toggleChestAlarmToast() {
        CelleScannerMod.config.chestAlarmToast = !CelleScannerMod.config.chestAlarmToast;
        CelleScannerMod.config.save();
        message("Chest Alarm notifikation: " + (CelleScannerMod.config.chestAlarmToast ? "til" : "fra"));
    }

    public static void toggleChestAlarmSound() {
        CelleScannerMod.config.chestAlarmSound = !CelleScannerMod.config.chestAlarmSound;
        CelleScannerMod.config.save();
        message("Chest Alarm lyd: " + (CelleScannerMod.config.chestAlarmSound ? "til" : "fra"));
    }

    public static void setChestAlarmKeyword(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            message("Ugyldigt nøgleord.");
            return;
        }
        CelleScannerMod.config.chestAlarmKeyword = trimmed;
        CelleScannerMod.config.save();
        message("Chest Alarm nøgleord sat til \"" + trimmed + "\".");
    }

    public static void testChestAlarm() {
        ChestAlarm.fire("CHEST-ALARM: TestSpiller låste den hemmelige chest op i PvP Minen!");
    }

    public static void toggleBandeEsp() {
        CelleScannerMod.config.bandeEspEnabled = !CelleScannerMod.config.bandeEspEnabled;
        CelleScannerMod.config.save();
        message("Bande ESP er nu " + (CelleScannerMod.config.bandeEspEnabled ? "til" : "fra") + ".");
    }

    public static void toggleBandeAutoTeam() {
        CelleScannerMod.config.bandeAutoTeam = !CelleScannerMod.config.bandeAutoTeam;
        CelleScannerMod.config.save();
        message("Bande auto (samme hold): " + (CelleScannerMod.config.bandeAutoTeam ? "til" : "fra"));
    }

    public static void toggleBandeEspAll() {
        CelleScannerMod.config.bandeEspAll = !CelleScannerMod.config.bandeEspAll;
        CelleScannerMod.config.save();
        message("ESP på alle spillere: " + (CelleScannerMod.config.bandeEspAll ? "til" : "fra"));
    }

    public static void addBandeMember(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            message("Ugyldigt spillernavn.");
            return;
        }
        if (CelleScannerMod.config.isBandeMember(trimmed)) {
            message(trimmed + " er allerede på bande-listen.");
            return;
        }
        CelleScannerMod.config.bandeMembers.add(trimmed);
        CelleScannerMod.config.save();
        message(trimmed + " tilføjet til banden.");
    }

    public static void removeBandeMember(String name) {
        String trimmed = name == null ? "" : name.trim();
        boolean removed = false;
        Iterator<String> it = CelleScannerMod.config.bandeMembers.iterator();
        while (it.hasNext()) {
            String existing = it.next();
            if (existing != null && existing.equalsIgnoreCase(trimmed)) {
                it.remove();
                removed = true;
            }
        }
        if (removed) {
            CelleScannerMod.config.save();
            message(trimmed + " fjernet fra banden.");
        } else {
            message(trimmed + " var ikke på bande-listen.");
        }
    }

    public static void clearBandeMembers() {
        CelleScannerMod.config.bandeMembers.clear();
        CelleScannerMod.config.save();
        message("Bande-listen er ryddet.");
    }

    public static void toggleAntiAfk() {
        CelleScannerMod.config.antiAfkEnabled = !CelleScannerMod.config.antiAfkEnabled;
        CelleScannerMod.config.save();
        message("Anti-AFK er nu " + (CelleScannerMod.config.antiAfkEnabled ? "aktiveret" : "deaktiveret") + ".");
    }

    public static void toggleAntiAfkSwing() {
        CelleScannerMod.config.antiAfkSwing = !CelleScannerMod.config.antiAfkSwing;
        CelleScannerMod.config.save();
        message("Anti-AFK slag: " + (CelleScannerMod.config.antiAfkSwing ? "til" : "fra"));
    }

    public static void toggleAntiAfkRotate() {
        CelleScannerMod.config.antiAfkRotate = !CelleScannerMod.config.antiAfkRotate;
        CelleScannerMod.config.save();
        message("Anti-AFK kig: " + (CelleScannerMod.config.antiAfkRotate ? "til" : "fra"));
    }

    public static void toggleAntiAfkJump() {
        CelleScannerMod.config.antiAfkJump = !CelleScannerMod.config.antiAfkJump;
        CelleScannerMod.config.save();
        message("Anti-AFK hop: " + (CelleScannerMod.config.antiAfkJump ? "til" : "fra"));
    }

    public static void adjustAntiAfkInterval(int delta) {
        CelleScannerMod.config.antiAfkIntervalSeconds = Math.max(5, Math.min(300, CelleScannerMod.config.antiAfkIntervalSeconds + delta));
        CelleScannerMod.config.save();
        message("Anti-AFK interval: " + CelleScannerMod.config.antiAfkIntervalSeconds + "s");
    }

    public static void openMenu() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleMenu());
    }

    public static void openSettings() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleSettings());
    }

    public static void openBotScreen() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleBot());
    }

    public static void openFinderScreen() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiCelleFinder());
    }

    public static void setFinderTarget(String id) {
        CelleFinder.setTarget(id);
        message("Finder søger nu efter celle " + id + ".");
    }

    public static void clearFinderTarget() {
        CelleFinder.clearTarget();
        message("Finder stoppet.");
    }

    public static void message(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.AQUA + "[Celle Scanner] " + EnumChatFormatting.RESET + text));
        }
    }
}
