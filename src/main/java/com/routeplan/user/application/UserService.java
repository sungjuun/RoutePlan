package com.routeplan.user.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResult create(String rawNickname) {
        User user = User.create(rawNickname);
        if (userRepository.existsByNickname(user.getNickname())) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return UserResult.from(userRepository.save(user));
    }

    public record UserResult(Long id, String nickname, Instant createdAt) {

        static UserResult from(User user) {
            return new UserResult(user.getId(), user.getNickname(), user.getCreatedAt());
        }
    }
}
