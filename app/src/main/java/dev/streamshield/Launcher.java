package dev.streamshield;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/** Entry point for the self-contained Windows x64 build. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!os.startsWith("windows") || !(arch.contains("amd64") || arch.contains("x86_64"))) {
            showError("This bundled build supports Windows x64 only.\nDetected: " + os + " / " + arch);
            return;
        }

        try {
            RealLauncher.main(args);
        } catch (Throwable error) {
            error.printStackTrace(System.err);
            String detail = error.getMessage();
            if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
            showError("jpxfrd could not launch its bundled Chromium runtime.\n\n" + detail);
        }
    }

    private static void showError(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(message);
        } else {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null, message, "jpxfrd Chromium", JOptionPane.ERROR_MESSAGE));
        }
    }
}
