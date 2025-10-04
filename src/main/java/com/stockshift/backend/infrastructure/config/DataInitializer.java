package com.stockshift.backend.infrastructure.config;

import com.stockshift.backend.domain.user.User;
import com.stockshift.backend.domain.user.UserRole;
import com.stockshift.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Initializing database with default users...");

            // Admin user
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@stockshift.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(UserRole.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
            log.info("Created admin user: admin / admin123");

            // Manager user
            User manager = new User();
            manager.setUsername("manager");
            manager.setEmail("manager@stockshift.com");
            manager.setPassword(passwordEncoder.encode("manager123"));
            manager.setRole(UserRole.MANAGER);
            manager.setActive(true);
            userRepository.save(manager);
            log.info("Created manager user: manager / manager123");

            // Seller user
            User seller = new User();
            seller.setUsername("seller");
            seller.setEmail("seller@stockshift.com");
            seller.setPassword(passwordEncoder.encode("seller123"));
            seller.setRole(UserRole.SELLER);
            seller.setActive(true);
            userRepository.save(seller);
            log.info("Created seller user: seller / seller123");

            log.info("Database initialization completed!");
        } else {
            log.info("Database already initialized, skipping...");
        }
    }
}
