package br.com.stockshift.security;

import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;

@Component
public class WithMockUserPrincipalSecurityContextFactory implements WithSecurityContextFactory<WithMockUserPrincipal> {

    private final UserRepository userRepository;

    public WithMockUserPrincipalSecurityContextFactory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public SecurityContext createSecurityContext(WithMockUserPrincipal annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        User user = userRepository.findByEmail(annotation.email())
                .orElseThrow(() -> new RuntimeException("User not found for mocking: " + annotation.email()));

        UserPrincipal principal = UserPrincipal.create(user);

        // Ensure hasFullAccess is set correctly based on the annotation role if needed,
        // but UserPrincipal.create already looks at roles.

        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities());
        context.setAuthentication(auth);

        return context;
    }
}
