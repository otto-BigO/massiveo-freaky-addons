package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Enumeration;

public class AccessSystem {

    public static boolean isVerified = false;

    // Licensing API (self-hosted on the Mac Mini behind a Cloudflare tunnel).
    private static final String API_URL = "https://license.ottomansfield.com/verify";

    /**
     * Verify a license key against the backend.
     *
     * On success we cache the key + HWID. If the licence server is later briefly
     * unreachable (Mac Mini reboot, ISP blip), a buyer who has verified before on
     * this same machine keeps working (fail-open). A key the server actively REJECTS
     * (invalid, or bound to someone else) never passes, and a brand-new key that has
     * never verified can't sneak in while the server is down.
     */
    public static boolean verifyKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }
        String cleanKey = key.trim();

        Minecraft mc = Minecraft.getMinecraft();
        String playerName = "";
        if (mc.thePlayer != null) {
            playerName = mc.thePlayer.getName();
        } else if (mc.getSession() != null) {
            playerName = mc.getSession().getUsername();
        }
        String hwid = getHWID();

        // Offline developer master key.
        if ("CELLE-SCANNER-ONLY".equalsIgnoreCase(cleanKey) && "MassiveO".equalsIgnoreCase(playerName)) {
            isVerified = true;
            return true;
        }

        try {
            String query = "?key=" + enc(cleanKey) + "&username=" + enc(playerName) + "&hwid=" + enc(hwid);
            HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + query).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            String body = readBody(in);

            boolean success = body != null && body.replace(" ", "").contains("\"status\":\"success\"");
            if (success) {
                isVerified = true;
                // Remember this good verification so a future outage can fail open.
                CelleScannerMod.config.accessKey = cleanKey;
                CelleScannerMod.config.verifiedKey = cleanKey;
                CelleScannerMod.config.verifiedHwid = hwid;
                CelleScannerMod.config.save();
                return true;
            }
            // The server answered but rejected the key - genuinely not licensed.
            DebugLog.log("AccessSystem", "Licens afvist af serveren (kode " + code + ").");
            return false;
        } catch (Exception e) {
            // Could not reach the server. Fail OPEN, but only for a key + machine that
            // verified successfully before.
            DebugLog.log("AccessSystem", "Licensserver ikke tilgaengelig, proever cache: " + e.getMessage());
            if (cleanKey.equalsIgnoreCase(CelleScannerMod.config.verifiedKey)
                    && hwid.equalsIgnoreCase(CelleScannerMod.config.verifiedHwid)) {
                isVerified = true;
                return true;
            }
            return false;
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readBody(InputStream in) {
        if (in == null) {
            return null;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Generates a unique, reproducible Hardware ID (HWID) signature for the current computer.
     */
    public static String getHWID() {
        try {
            StringBuilder sb = new StringBuilder();
            
            // 1. Collect hardware/OS characteristics
            sb.append(System.getProperty("os.name"));
            sb.append(System.getProperty("os.arch"));
            sb.append(System.getProperty("user.name"));
            
            // 2. Get hardware address (MAC) of primary active network interface
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0 && !ni.isLoopback() && !ni.isVirtual()) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    break;
                }
            }
            
            // 3. Digest and hash the result to produce a clean hex signature
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sb.toString().getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            // Format as XXXX-XXXX-XXXX-XXXX
            String fullHex = hexString.toString().toUpperCase();
            return fullHex.substring(0, 4) + "-" + 
                   fullHex.substring(4, 8) + "-" + 
                   fullHex.substring(8, 12) + "-" + 
                   fullHex.substring(12, 16);
        } catch (Exception e) {
            return "UNKNOWN-HWID-0000";
        }
    }
}
