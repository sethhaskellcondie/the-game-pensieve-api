package com.sethhaskellcondie.thegamepensieveapi.api.security;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.User;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.UserRepository;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges the lightweight {@link User} model to Spring Security by loading an account by its email.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        final User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for email: " + email));
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.email())
                .password(user.passwordHash())
                .disabled(!user.enabled())
                .authorities(AuthorityUtils.NO_AUTHORITIES)
                .build();
    }
}
