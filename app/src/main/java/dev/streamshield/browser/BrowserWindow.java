package dev.streamshield.browser;

import dev.streamshield.nativeapi.WindowsCaptureGuard;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BrowserWindow {
    private static final String TITLE = "jpxfrd";
    private final CefApp cefApp;
    private final CefClient client;
    private final String initialInput;
    private final JFrame frame = new JFrame(TITLE);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextField address = new JTextField();
    private final JButton back = button("←", "Back");
    private final JButton forward = button("→", "Forward");
    private final JButton reload = button("↻", "Reload / Stop");
    private final JButton home = button("⌂", "Home");
    private final JButton addTab = button("+", "New tab");
    private final JProgressBar progress = new JProgressBar(0, 1000);
    private final JLabel protection = new JLabel(" ");
    private final Map<CefBrowser, BrowserTab> byBrowser = new IdentityHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();

    public BrowserWindow(CefApp cefApp, String initialInput) {
        this.cefApp = cefApp;
        this.client = cefApp.createClient();
        this.initialInput = initialInput;
        installHandlers();
        configureFrame();
    }

    public void open() {
        requireEdt();
        addTab(initialInput, true);
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            WindowsCaptureGuard.Result result = WindowsCaptureGuard.enable(frame);
            protection.setText(result.message());
            protection.setForeground(result.fullExclusion() ? new Color(40, 140, 72) : new Color(175, 102, 25));
        });
    }

    private void configureFrame() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 620));
        frame.setSize(1280, 820);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel toolbar = new JPanel(new BorderLayout(6, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(back);
        buttons.add(forward);
        buttons.add(reload);
        buttons.add(home);
        buttons.add(addTab);
        toolbar.add(buttons, BorderLayout.WEST);
        toolbar.add(address, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout(8, 0));
        progress.setPreferredSize(new Dimension(180, 5));
        progress.setBorderPainted(false);
        progress.setVisible(false);
        protection.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        status.add(progress, BorderLayout.NORTH);
        status.add(protection, BorderLayout.SOUTH);

        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);

        tabs.addChangeListener(this::tabChanged);
        address.addActionListener(e -> navigate(address.getText()));
        back.addActionListener(e -> { BrowserTab tab = current(); if (tab != null && tab.browser.canGoBack()) tab.browser.goBack(); });
        forward.addActionListener(e -> { BrowserTab tab = current(); if (tab != null && tab.browser.canGoForward()) tab.browser.goForward(); });
        reload.addActionListener(e -> { BrowserTab tab = current(); if (tab != null) { if (tab.loading) tab.browser.stopLoad(); else tab.browser.reload(); } });
        home.addActionListener(e -> navigate(UrlResolver.INTERNAL_HOME));
        addTab.addActionListener(e -> addTab(null, true));

        frame.getRootPane().registerKeyboardAction(e -> addTab(null, true), KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.getRootPane().registerKeyboardAction(e -> closeCurrentTab(), KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.getRootPane().registerKeyboardAction(e -> address.requestFocusInWindow(), KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.getRootPane().registerKeyboardAction(e -> { BrowserTab tab = current(); if (tab != null) tab.browser.reloadIgnoreCache(); }, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);

        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { closeAll(); }
        });
    }

    private void installHandlers() {
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                BrowserTab tab = byBrowser.get(browser);
                if (tab == null || !frame.isMain()) return;
                tab.url = url.equals(HomePage.dataUrl()) ? UrlResolver.INTERNAL_HOME : url;
                SwingUtilities.invokeLater(() -> { if (tab == current()) address.setText(tab.url); });
            }

            @Override public void onTitleChange(CefBrowser browser, String title) {
                BrowserTab tab = byBrowser.get(browser);
                if (tab == null) return;
                String clean = title == null || title.isBlank() ? "New Tab" : title.strip();
                if (clean.length() > 28) clean = clean.substring(0, 27) + "…";
                String finalClean = clean;
                SwingUtilities.invokeLater(() -> {
                    int index = tabs.indexOfComponent(tab.panel);
                    if (index >= 0 && tab.headerTitle != null) tab.headerTitle.setText(finalClean);
                    if (tab == current()) BrowserWindow.this.frame.setTitle(finalClean + " — " + TITLE);
                });
            }
        });

        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                BrowserTab tab = byBrowser.get(browser);
                if (tab == null) return;
                tab.loading = isLoading;
                SwingUtilities.invokeLater(() -> {
                    if (tab == current()) {
                        back.setEnabled(canGoBack);
                        forward.setEnabled(canGoForward);
                        reload.setText(isLoading ? "×" : "↻");
                        progress.setVisible(isLoading);
                        if (!isLoading) progress.setValue(0);
                    }
                });
            }
        });

        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                SwingUtilities.invokeLater(() -> addTab(targetUrl, true));
                return true;
            }
        });
    }

    private void addTab(String input, boolean select) {
        requireEdt();
        String resolved = UrlResolver.resolve(input);
        String loadUrl = UrlResolver.isInternalHome(resolved) ? HomePage.dataUrl() : resolved;
        CefBrowser browser = client.createBrowser(loadUrl, false, false);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browser.getUIComponent(), BorderLayout.CENTER);
        BrowserTab tab = new BrowserTab(browser, panel, resolved);
        byBrowser.put(browser, tab);
        tabs.addTab("New Tab", panel);
        int index = tabs.indexOfComponent(panel);
        tabs.setTabComponentAt(index, tabHeader(tab));
        if (select) tabs.setSelectedComponent(panel);
    }

    private Component tabHeader(BrowserTab tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        JLabel title = new JLabel("New Tab");
        JButton close = new JButton("×");
        close.setFocusable(false);
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.addActionListener(e -> closeTab(tab));
        panel.add(title);
        panel.add(close);
        tab.headerTitle = title;
        return panel;
    }

    private void navigate(String value) {
        BrowserTab tab = current();
        if (tab == null) return;
        String resolved = UrlResolver.resolve(value);
        tab.url = resolved;
        address.setText(resolved);
        tab.browser.loadURL(UrlResolver.isInternalHome(resolved) ? HomePage.dataUrl() : resolved);
    }

    private void tabChanged(ChangeEvent ignored) {
        BrowserTab tab = current();
        if (tab == null) return;
        address.setText(tab.url);
        back.setEnabled(tab.browser.canGoBack());
        forward.setEnabled(tab.browser.canGoForward());
        reload.setText(tab.loading ? "×" : "↻");
        progress.setVisible(tab.loading);
        String title = tab.headerTitle == null ? "New Tab" : tab.headerTitle.getText();
        frame.setTitle(title + " — " + TITLE);
    }

    private BrowserTab current() {
        Component selected = tabs.getSelectedComponent();
        if (selected == null) return null;
        for (BrowserTab tab : byBrowser.values()) if (tab.panel == selected) return tab;
        return null;
    }

    private void closeCurrentTab() {
        BrowserTab tab = current();
        if (tab != null) closeTab(tab);
    }

    private void closeTab(BrowserTab tab) {
        requireEdt();
        byBrowser.remove(tab.browser);
        tabs.remove(tab.panel);
        tab.browser.close(true);
        if (tabs.getTabCount() == 0) addTab(null, true);
    }

    private void closeAll() {
        if (!closing.compareAndSet(false, true)) return;
        for (BrowserTab tab : byBrowser.values().toArray(BrowserTab[]::new)) {
            try { tab.browser.close(true); } catch (Throwable ignored) {}
        }
        byBrowser.clear();
        try { client.dispose(); } catch (Throwable ignored) {}
        try { cefApp.dispose(); } catch (Throwable ignored) {}
        frame.dispose();
    }

    private static JButton button(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setMargin(new Insets(4, 9, 4, 9));
        return button;
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Swing EDT required");
    }

    private static final class BrowserTab {
        private final CefBrowser browser;
        private final JPanel panel;
        private String url;
        private boolean loading;
        private JLabel headerTitle;

        private BrowserTab(CefBrowser browser, JPanel panel, String url) {
            this.browser = browser;
            this.panel = panel;
            this.url = url;
        }
    }
}
