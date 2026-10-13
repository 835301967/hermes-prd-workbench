package org.hermesprd.workbench.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hermesprd.workbench.shared.ApiException;
import org.hermesprd.workbench.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,32}$");
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final UserMapper userMapper;
    private final SessionMapper sessionMapper;

    public AuthService(UserMapper userMapper, SessionMapper sessionMapper) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
    }

    public AuthSession register(String username, String password, String displayName) {
        String normalized = normalizeUsername(username);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用户名需为 3-32 位字母、数字或下划线");
        }
        requirePassword(password);
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalized));
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被使用");
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(Ids.next("user_"));
        user.setUsername(normalized);
        user.setPasswordHash(Credentials.hashPassword(password));
        user.setDisplayName(resolveDisplayName(displayName, normalized));
        user.setRole("USER");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return issueSession(user);
    }

    public AuthSession login(String username, String password) {
        String normalized = normalizeUsername(username);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalized));
        if (user == null || !Credentials.passwordMatches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "用户名或密码不正确");
        }
        return issueSession(user);
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionMapper.delete(new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getTokenHash, Credentials.hashToken(token)));
    }

    public UserEntity authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
        }
        SessionEntity session = sessionMapper.selectOne(
                new LambdaQueryWrapper<SessionEntity>().eq(SessionEntity::getTokenHash, Credentials.hashToken(token))
        );
        if (session == null || session.getExpiresAt() == null || session.getExpiresAt().isBefore(Instant.now())) {
            if (session != null) {
                sessionMapper.deleteById(session.getId());
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "登录已过期，请重新登录");
        }
        UserEntity user = userMapper.selectById(session.getUserId());
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
        }
        return user;
    }

    private AuthSession issueSession(UserEntity user) {
        Instant now = Instant.now();
        String token = Credentials.newToken();
        SessionEntity session = new SessionEntity();
        session.setId(Ids.next("ses_"));
        session.setUserId(user.getId());
        session.setTokenHash(Credentials.hashToken(token));
        session.setExpiresAt(now.plus(SESSION_TTL));
        session.setCreatedAt(now);
        sessionMapper.insert(session);
        return new AuthSession(token, session.getExpiresAt(), user);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void requirePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "密码至少 8 个字符，UTF-8 编码不得超过 72 字节");
        }
    }

    private String resolveDisplayName(String displayName, String username) {
        if (displayName == null || displayName.isBlank()) {
            return username;
        }
        String trimmed = displayName.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    public UserEntity updateUserRole(String operatorUserId, String targetUserId, String newRole) {
        if (newRole == null || newRole.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "角色不能为空");
        }
        String normalizedRole = newRole.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ADMIN", "USER").contains(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "角色只能为 ADMIN 或 USER");
        }
        UserEntity target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "目标用户不存在");
        }
        if (operatorUserId != null && operatorUserId.equals(targetUserId) && !"ADMIN".equals(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DEMOTE_SELF", "不能取消自身的管理员权限");
        }
        target.setRole(normalizedRole);
        target.setUpdatedAt(Instant.now());
        userMapper.updateById(target);
        return target;
    }

    public UserEntity createUserByAdmin(String username, String password, String displayName, String role) {
        String normalized = normalizeUsername(username);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "用户名需为 3-32 位字母、数字或下划线");
        }
        requirePassword(password);
        String normalizedRole = role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ADMIN", "USER").contains(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "角色只能为 ADMIN 或 USER");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, normalized));
        if (exists != null && exists > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已被使用");
        }
        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(Ids.next("user_"));
        user.setUsername(normalized);
        user.setPasswordHash(Credentials.hashPassword(password));
        user.setDisplayName(resolveDisplayName(displayName, normalized));
        user.setRole(normalizedRole);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    public record AuthSession(String token, Instant expiresAt, UserEntity user) {
    }
}
