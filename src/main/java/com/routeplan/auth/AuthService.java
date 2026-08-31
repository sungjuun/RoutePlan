package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailQueue mail;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthMailQueue mail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
    }

    @Transactional
    public User register(String rawEmail, String nickname, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        validatePassword(rawPassword);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(nickname.strip())) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_NICKNAME);
        }
        User user = userRepository.saveAndFlush(User.register(
                email,
                nickname,
                passwordEncoder.encode(rawPassword)
        ));
        mail.enqueue(email, "VERIFY_EMAIL");
        return user;
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException("비밀번호는 10자 이상이어야 합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
    }
}
