package dev.streamshield.nativeapi;

import com.sun.jna.Native;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.*;
import java.util.Locale;

public final class WindowsCaptureGuard {
    private static final int WDA_MONITOR = 0x00000001;
    private static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

    private WindowsCaptureGuard() {}

    public static Result enable(JFrame frame) {
        if (!isWindows()) return new Result(false, false, "Capture exclusion is Windows-only");
        try {
            HWND hwnd = WindowUtils.getHWND(frame);
            if (User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)) {
                return new Result(true, true, "WDA_EXCLUDEFROMCAPTURE active");
            }
            int firstError = Native.getLastError();
            if (User32Ex.INSTANCE.SetWindowDisplayAffinity(hwnd, WDA_MONITOR)) {
                return new Result(true, false, "0x11 failed (Win32 " + firstError + "); WDA_MONITOR fallback active");
            }
            return new Result(false, false, "SetWindowDisplayAffinity failed (Win32 " + Native.getLastError() + ")");
        } catch (Throwable error) {
            return new Result(false, false, error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    public record Result(boolean applied, boolean fullExclusion, String message) {}

    private interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean SetWindowDisplayAffinity(HWND hWnd, int dwAffinity);
    }
}
