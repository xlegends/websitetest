package dev.streamshield;

import dev.streamshield.browser.BrowserWindow;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter;
import org.cef.CefApp;
import org.cef.CefSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RealLauncher {
    private RealLauncher() {}

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> reportFatal("Unexpected error on " + thread.getName(), error));
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException error) {
            reportFatal("Invalid command line", error);
            return;
        }

        if (options.help()) {
            System.out.println(usage());
            return;
        }

        installSystemLookAndFeel();
        Splash splash = Splash.showNow();
        try {
            CefApp cefApp = buildChromium(options.lowMemory());
            SwingUtilities.invokeLater(() -> {
                splash.close();
                BrowserWindow window = new BrowserWindow(cefApp, options.initialInput());
                window.open();
            });
        } catch (Throwable error) {
            splash.close();
            reportFatal("jpxfrd Chromium could not start", error);
        }
    }

    private static CefApp buildChromium(boolean lowMemory) throws Exception {
        Path appHome = Path.of(System.getProperty("user.home"), ".jpxfrd");
        File installDir = appHome.resolve("jcef-bundle").toFile();
        File profileDir = appHome.resolve("profile").toFile();
        installDir.mkdirs();
        profileDir.mkdirs();

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(installDir);
        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.getCefSettings().cache_path = profileDir.getAbsolutePath();
        builder.getCefSettings().root_cache_path = profileDir.getAbsolutePath();
        builder.getCefSettings().persist_session_cookies = true;
        builder.getCefSettings().log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        builder.getCefSettings().log_file = appHome.resolve("jcef.log").toString();
        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefApp.CefAppState state) {
                if (state == CefApp.CefAppState.TERMINATED) System.exit(0);
            }
        });

        List<String> chromiumArgs = new ArrayList<>();
        chromiumArgs.add("--no-first-run");
        chromiumArgs.add("--no-default-browser-check");
        chromiumArgs.add("--disable-component-update");
        chromiumArgs.add("--disable-default-apps");
        chromiumArgs.add("--disable-sync");
        chromiumArgs.add("--disable-background-networking");
        chromiumArgs.add("--disable-breakpad");
        chromiumArgs.add("--disable-domain-reliability");
        chromiumArgs.add("--enable-gpu-rasterization");
        chromiumArgs.add("--enable-zero-copy");
        chromiumArgs.add("--renderer-process-limit=" + (lowMemory ? 2 : 4));
        chromiumArgs.add("--disk-cache-size=" + (lowMemory ? 134_217_728 : 268_435_456));
        chromiumArgs.add("--media-cache-size=67_108_864");
        chromiumArgs.add("--disable-features=MediaRouter,OptimizationHints,Translate,AutofillServerCommunication,InterestFeedContentSuggestions");
        builder.addJcefArgs(chromiumArgs.toArray(String[]::new));
        return builder.build();
    }

    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
        }
    }

    private static void reportFatal(String title, Throwable error) {
        error.printStackTrace(System.err);
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) detail = error.getClass().getSimpleName();
        String message = title + ".\n\n" + detail;
        if (GraphicsEnvironment.isHeadless()) System.err.println(message);
        else SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE));
    }

    private static String usage() {
        return """
                jpxfrd Chromium

                java -jar jpxfrd-chromium.jar [options] [URL or search]

                  --url <value>   Open a URL or Google search
                  --low-memory    Limit Chromium to two renderer processes
                  --help          Show this help
                """;
    }

    private record Options(String initialInput, boolean lowMemory, boolean help) {
        private static Options parse(String[] args) {
            String initial = null;
            boolean lowMemory = false;
            boolean help = false;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help", "-h" -> help = true;
                    case "--low-memory" -> lowMemory = true;
                    case "--url" -> {
                        if (++i >= args.length) throw new IllegalArgumentException("--url requires a value");
                        initial = args[i];
                    }
                    default -> {
                        if (arg.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + arg);
                        if (initial != null) throw new IllegalArgumentException("Only one URL/search value is supported");
                        initial = arg;
                    }
                }
            }
            return new Options(initial, lowMemory, help);
        }
    }

    private static final class Splash {
        private final JWindow window;
        private Splash(JWindow window) { this.window = window; }

        static Splash showNow() {
            if (GraphicsEnvironment.isHeadless()) return new Splash(null);
            final JWindow[] result = new JWindow[1];
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JWindow window = new JWindow();
                    JPanel panel = new JPanel(new BorderLayout(12, 12));
                    panel.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(55, 63, 81)),
                            BorderFactory.createEmptyBorder(22, 28, 22, 28)));
                    JLabel label = new JLabel("Starting bundled Chromium…");
                    label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
                    JProgressBar bar = new JProgressBar();
                    bar.setIndeterminate(true);
                    panel.add(label, BorderLayout.NORTH);
                    panel.add(bar, BorderLayout.CENTER);
                    window.setContentPane(panel);
                    window.pack();
                    window.setLocationRelativeTo(null);
                    window.setVisible(true);
                    result[0] = window;
                });
            } catch (Exception ignored) {
            }
            return new Splash(result[0]);
        }

        void close() {
            if (window != null) SwingUtilities.invokeLater(window::dispose);
        }
    }
}
