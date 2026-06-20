package br.com.stockshift.security;

import java.util.UUID;

public record BotPrincipal(UUID tenantId) {
}
