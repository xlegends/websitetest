package dev.streamshield.browser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class HomePage {
    private HomePage() {}

    static String dataUrl() {
        String html = """
                <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>New Tab</title><style>
                :root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;
                background:radial-gradient(circle at 50% 35%,#20283a 0,#11151e 42%,#090b10 100%);font:16px system-ui;color:#eef2ff}
                main{width:min(680px,88vw);text-align:center}h1{font-size:42px;margin:0 0 10px;letter-spacing:-1.5px}p{color:#aab3c8;margin:0}
                .pill{display:inline-block;margin-top:22px;padding:9px 14px;border:1px solid #36415b;border-radius:999px;color:#cdd6eb;background:#151b27}
                </style></head><body><main><h1>jpxfrd</h1><p>Chromium renderer · windowed GPU mode</p><div class="pill">Type a URL or search above</div></main></body></html>
                """;
        return "data:text/html;charset=utf-8," + URLEncoder.encode(html, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
