package org.hermesprd.workbench.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local, explicit bootstrap after Flyway and before the web server starts. No HTTP bootstrap endpoint. */
@Component
@DependsOnDatabaseInitialization
public class AdminBootstrap {
    private final AuthService authService;
    private final UserMapper users;
    private final String username;
    private final String passwordFile;

    public AdminBootstrap(AuthService authService, UserMapper users,
                          @Value("${PRD_BOOTSTRAP_ADMIN_USERNAME:}") String username,
                          @Value("${PRD_BOOTSTRAP_ADMIN_PASSWORD_FILE:}") String passwordFile) {
        this.authService = authService;
        this.users = users;
        this.username = username;
        this.passwordFile = passwordFile;
    }

    @PostConstruct
    public void initialize() {
        if (username == null || username.isBlank()) return;
        Long count = users.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getRole, "ADMIN"));
        if (count != null && count > 0) return; // Never change existing administrators or their passwords.
        if (passwordFile == null || passwordFile.isBlank()) {
            throw new IllegalStateException("管理员初始化需要 PRD_BOOTSTRAP_ADMIN_PASSWORD_FILE");
        }
        try {
            String password = Files.readString(Path.of(passwordFile), StandardCharsets.UTF_8);
            if (password.endsWith("\r\n")) password = password.substring(0, password.length() - 2);
            else if (password.endsWith("\n")) password = password.substring(0, password.length() - 1);
            authService.createUserByAdmin(username, password, username, "ADMIN");
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取管理员初始化密码文件");
        }
    }
}
