package com.genshinui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * "Open files" dialog using the operating system's own tools, for Minecraft versions that no longer ship LWJGL's
 * tinyfd (26.3 moved to SDL). Blocking: call it off the render thread.
 * Windows: PowerShell + WinForms; macOS: osascript; Linux: zenity, then kdialog.
 */
public final class NativeFileDialog {
    private NativeFileDialog() {}

    /** Returns the chosen files (possibly empty if cancelled or no dialog tool is available). */
    public static List<Path> openImages(String title, String startDir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) return windows(title, startDir);
            if (os.contains("mac")) return mac(title);
            return linux(title, startDir);
        } catch (Exception e) {
            System.err.println("[GenshinUI] file dialog failed: " + e);
            return List.of();
        }
    }

    private static List<Path> windows(String title, String startDir) throws Exception {
        String script = String.join("\n",
                "Add-Type -AssemblyName System.Windows.Forms",
                "$d = New-Object System.Windows.Forms.OpenFileDialog",
                "$d.Title = '" + title.replace("'", "''") + "'",
                "$d.Multiselect = $true",
                "$d.Filter = 'Images or Wallpaper Engine scene.pkg|*.png;*.jpg;*.jpeg;*.bmp;*.gif;*.pkg|All files|*.*'",
                startDir == null ? "" : "$d.InitialDirectory = '" + startDir.replace("'", "''") + "'",
                // a topmost owner so the dialog doesn't open behind the game window
                "$f = New-Object System.Windows.Forms.Form -Property @{TopMost = $true; ShowInTaskbar = $false}",
                "if ($d.ShowDialog($f) -eq 'OK') { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $d.FileNames -join '|' }");
        Path ps1 = Files.createTempFile("genshinui-dialog", ".ps1");
        try {
            Files.writeString(ps1, "﻿" + script, StandardCharsets.UTF_8);
            return run(List.of("powershell", "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass", "-File", ps1.toString()), "\\|");
        } finally {
            Files.deleteIfExists(ps1);
        }
    }

    private static List<Path> mac(String title) throws Exception {
        String script = "set fs to choose file with prompt \"" + title.replace("\"", "'") + "\" with multiple selections allowed\n"
                + "set out to \"\"\nrepeat with f in fs\nset out to out & POSIX path of f & \"|\"\nend repeat\nreturn out";
        return run(List.of("osascript", "-e", script), "\\|");
    }

    private static List<Path> linux(String title, String startDir) throws Exception {
        try {
            return run(List.of("zenity", "--file-selection", "--multiple", "--separator=|", "--title=" + title,
                    "--file-filter=Images | *.png *.jpg *.jpeg *.bmp *.gif *.pkg"), "\\|");
        } catch (IOException noZenity) {
            return run(List.of("kdialog", "--getopenfilename", startDir == null ? "." : startDir,
                    "*.png *.jpg *.jpeg *.bmp *.gif *.pkg", "--multiple", "--separate-output"), "\\n");
        }
    }

    private static List<Path> run(List<String> cmd, String separator) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor(10, TimeUnit.SECONDS);
        List<Path> paths = new ArrayList<>();
        for (String s : out.split(separator)) {
            s = s.strip();
            if (s.isEmpty()) continue;
            Path f = Path.of(s);
            if (Files.isRegularFile(f)) paths.add(f);
        }
        return paths;
    }
}
