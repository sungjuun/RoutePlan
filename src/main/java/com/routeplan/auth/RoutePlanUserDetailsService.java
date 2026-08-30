package com.routeplan.auth;

import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RoutePlanUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public RoutePlanUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email == null ? "" : email.strip())
                .filter(candidate -> candidate.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("인증 정보를 확인할 수 없습니다."));
        return principal(user);
    }

    public static RoutePlanPrincipal principal(User user) {
        return new RoutePlanPrincipal(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPasswordHash(),
                user.getCreatedAt(),
                user.getSecurityVersion()
        );
    }
}
