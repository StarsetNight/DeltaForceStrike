package org.starset.deltaforcestrike.live;

import org.starset.deltaforcestrike.DeltaForceStrike;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * OBS 导播覆盖层页面加载器。
 * HTML 模板位于 resources/overlay.html（此前内嵌在 Java 文本块中，21KB 字符串）。
 */
public final class LiveOverlayHtml {

    private LiveOverlayHtml() {}

    public static String page() {
        try (InputStream in = DeltaForceStrike.getInstance().getResource("overlay.html")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }
        return "<html><body style='background:#111;color:#f66;font-family:sans-serif'>"
                + "<h2>overlay.html 缺失</h2><p>请检查插件 jar 是否包含该资源。</p>"
                + "</body></html>";
    }
}
