package br.com.stockshift.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.stockshift.BaseIntegrationTest;
import br.com.stockshift.model.entity.RefreshToken;
import br.com.stockshift.model.entity.Tenant;
import br.com.stockshift.model.entity.User;
import br.com.stockshift.repository.RefreshTokenRepository;
import br.com.stockshift.repository.TenantRepository;
import br.com.stockshift.repository.UserRepository;
import br.com.stockshift.util.TestDataFactory;

// Exercises the concurrent-rotation loser path against a real database: the atomic
// claimRotation 0-row outcome and the findReplacedById scalar query (the winner path
// is covered by the controller integration tests on every refresh, but the loser path
// is otherwise only mocked).
class RefreshTokenServiceConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUpUser() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = TestDataFactory.createTenant(tenantRepository, "RT Concurrency Tenant", "22222222000102");
        user = TestDataFactory.createUser(userRepository, passwordEncoder, tenant.getId(), "rt-concurrency@test.com");
    }

    @Test
    void rotateLoserDiscardsItsOrphanAndReturnsTheCommittedSuccessor() {
        // A winner already rotated A -> B and committed (A.replaced_by_id = B).
        RefreshToken a = persistToken();
        RefreshToken b = persistToken();
        a.setRotatedAt(LocalDateTime.now());
        a.setReplacedById(b.getId());
        refreshTokenRepository.saveAndFlush(a);
        long tokensBefore = refreshTokenRepository.count();

        // Stale in-memory view of A, as if it was loaded before the winner committed:
        // replacedById is still null, so rotation mints an orphan and the atomic claim
        // then fails (0 rows) because the DB row is already rotated.
        RefreshToken staleA = new RefreshToken();
        staleA.setId(a.getId());
        staleA.setUser(user);
        staleA.setExpiresAt(LocalDateTime.now().plusDays(7));

        RefreshToken result = refreshTokenService.rotateRefreshToken(staleA, null);

        // The loser resolves to the committed successor and leaves no extra token behind.
        assertEquals(b.getId(), result.getId());
        assertEquals(tokensBefore, refreshTokenRepository.count());
    }

    private RefreshToken persistToken() {
        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusDays(7));
        token.setCreatedAt(LocalDateTime.now());
        return refreshTokenRepository.saveAndFlush(token);
    }
}
