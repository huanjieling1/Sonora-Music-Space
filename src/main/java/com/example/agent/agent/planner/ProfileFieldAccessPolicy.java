package com.example.agent.agent.planner;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Owner-bound profile policy with explicit grants for sensitive paths. */
@Component
public final class ProfileFieldAccessPolicy {
    private static final Set<String> SENSITIVE_SEGMENTS = Set.of(
            "explicitpreferences", "rawevents", "listeninghistory", "privatenotes",
            "userid", "email", "phone", "credential", "credentials", "token", "tokens");

    public Decision authorize(String path, ReferenceResolutionContext context) {
        if (context.principalId().isBlank()
                || !context.principalId().equals(context.profileOwnerPrincipalId())) {
            return new Decision(false, "PROFILE_OWNER_MISMATCH",
                    "画像只能由其所属的当前登录用户读取");
        }
        if (!isProfilePath(path)) {
            return new Decision(false, "PROFILE_PATH_OUT_OF_SCOPE",
                    "画像引用必须位于 $.musicProfile 命名空间");
        }
        if (sensitive(path) && context.allowedSensitiveProfilePaths().stream()
                .noneMatch(allowed -> path.equals(allowed) || path.startsWith(allowed + ".")
                        || path.startsWith(allowed + "["))) {
            return new Decision(false, "SENSITIVE_PROFILE_FIELD_DENIED",
                    "敏感画像字段需要显式路径授权");
        }
        return new Decision(true, "", "");
    }

    private static boolean isProfilePath(String path) {
        return path != null && (path.equals("$.musicProfile") || path.startsWith("$.musicProfile.")
                || path.startsWith("$.musicProfile["));
    }

    private static boolean sensitive(String path) {
        String normalized = path.toLowerCase(Locale.ROOT).replace("_", "");
        return SENSITIVE_SEGMENTS.stream().anyMatch(normalized::contains);
    }

    public record Decision(boolean allowed, String code, String message) {}
}
