package kotowari.scope;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static kotowari.scope.ExportableScope.*;

/**
 * @author kawasima
 */
public record ExportSetting(Map<ExportableScope, String> exports) {
    public static final ExportSetting DEFAULT_EXPORTS = ExportSetting.of(
            REQUEST, "request",
            SESSION, "session",
            PARAMS,  "params",
            USER_PRINCIPAL, "userPrincipal",
            CONVERSATION, "conversation",
            CONVERSATION_STATE, "conversationState"
    );

    public static ExportSetting of(Object... keyOrVals) {
        Map<ExportableScope, String> map = new HashMap<>();
        for (int i=0; i<keyOrVals.length; i += 2) {
            map.put((ExportableScope) keyOrVals[i], (String) keyOrVals[i+1]);
        }
        return new ExportSetting(Map.copyOf(map));
    }

    public @Nullable String getExportName(ExportableScope scope) {
        return exports.get(scope);
    }

    public boolean contains(ExportableScope scope) {
        return exports.containsKey(scope);
    }
}
