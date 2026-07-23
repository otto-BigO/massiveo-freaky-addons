package com.otto.cellescanner;

import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads majestica_assets.zip from GitHub Releases asynchronously
 * and extracts all 120 3D models & textures into .minecraft/config/cellescanner/majestica/
 */
public class MajesticaDownloader {

    public static final MajesticaDownloader INSTANCE = new MajesticaDownloader();

    private static final String ASSET_URL = "https://github.com/otto-BigO/massiveo-freaky-addons/releases/download/v2.5.0-test/majestica_assets.zip";

    private boolean downloading = false;
    private int progressPercent = 0;
    private String statusMessage = "";

    private MajesticaDownloader() {
    }

    public File getMajesticaDir() {
        File mcDir = Minecraft.getMinecraft().mcDataDir;
        File dir = new File(mcDir, "config/cellescanner/majestica");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getManifestFile() {
        return new File(getMajesticaDir(), "assets/cellescanner/weapons_majestica.json");
    }

    public boolean isDownloaded() {
        return getManifestFile().exists();
    }

    public boolean isDownloading() {
        return downloading;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public synchronized void startDownload(final Runnable onComplete) {
        if (isDownloaded() || downloading) {
            if (isDownloaded() && onComplete != null) {
                onComplete.run();
            }
            return;
        }

        downloading = true;
        progressPercent = 0;
        statusMessage = "Henter 3D våben modeller...";

        Thread downloadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File mcDir = Minecraft.getMinecraft().mcDataDir;
                    File zipFile = new File(mcDir, "config/cellescanner/majestica_assets.zip");
                    zipFile.getParentFile().mkdirs();

                    URL url = new URL(ASSET_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    int totalSize = conn.getContentLength();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(zipFile);

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalRead = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (totalSize > 0) {
                            progressPercent = (int) ((totalRead / (float) totalSize) * 80); // 0-80% download
                        }
                    }

                    fos.close();
                    is.close();

                    // Extract ZIP
                    statusMessage = "Udpakker 3D modeller...";
                    progressPercent = 85;

                    File destDir = getMajesticaDir();
                    ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
                    ZipEntry entry;

                    while ((entry = zis.getNextEntry()) != null) {
                        File newFile = new File(destDir, entry.getName());
                        if (entry.isDirectory()) {
                            newFile.mkdirs();
                        } else {
                            newFile.getParentFile().mkdirs();
                            FileOutputStream out = new FileOutputStream(newFile);
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = zis.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                            out.close();
                        }
                        zis.closeEntry();
                    }
                    zis.close();
                    zipFile.delete();

                    progressPercent = 100;
                    statusMessage = "Færdig!";
                    downloading = false;

                    if (onComplete != null) {
                        Minecraft.getMinecraft().addScheduledTask(onComplete);
                    }

                } catch (Throwable t) {
                    System.err.println("[MajesticaDownloader] Download error: " + t.getMessage());
                    statusMessage = "Fejl ved hentning: " + t.getMessage();
                    downloading = false;
                }
            }
        }, "Majestica-Downloader");

        downloadThread.start();
    }
}
