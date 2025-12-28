package br.com.stockshift.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "StockShift API", version = "1.0.0", description = "Multi-tenant Stock Management System API", contact = @Contact(name = "StockShift", email = "support@stockshift.com")), servers = {
        @Server(url = "http://localhost:8080", description = "Local Development"),
        @Server(url = "https://api.stockshift.com", description = "Production")
})
@SecurityScheme(name = "Bearer Authentication", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer", description = "Enter JWT token obtained from /api/auth/login or /api/auth/register")
public class OpenApiConfig {
}
