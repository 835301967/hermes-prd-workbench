package org.hermesprd.workbench.auth;

public final class CurrentUser {
    private static final ThreadLocal<UserEntity> HOLDER = new ThreadLocal<>();

    private CurrentUser() {
    }

    public static void set(UserEntity user) {
        HOLDER.set(user);
    }

    public static UserEntity get() {
        return HOLDER.get();
    }

    public static UserEntity require() {
        UserEntity user = HOLDER.get();
        if (user == null) {
            throw new org.hermesprd.workbench.shared.ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "请先登录"
            );
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
