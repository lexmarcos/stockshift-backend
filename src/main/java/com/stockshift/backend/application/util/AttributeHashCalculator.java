package com.stockshift.backend.application.util;

import com.stockshift.backend.api.dto.variant.CreateProductVariantRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AttributeHashCalculator {

    public String calculateHash(List<CreateProductVariantRequest.VariantAttributePair> attributes) {
        // Sort by definitionId to ensure consistent ordering
        String normalized = attributes.stream()
            .sorted(Comparator.comparing(CreateProductVariantRequest.VariantAttributePair::getDefinitionId))
            .map(attr -> attr.getDefinitionId() + ":" + attr.getValueId())
            .collect(Collectors.joining("|"));
        
        return hashString(normalized);
    }

    public String calculateHashFromIds(List<UUID> definitionIds, List<UUID> valueIds) {
        if (definitionIds.size() != valueIds.size()) {
            throw new IllegalArgumentException("Definition IDs and Value IDs lists must have the same size");
        }

        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < definitionIds.size(); i++) {
            if (i > 0) normalized.append("|");
            normalized.append(definitionIds.get(i)).append(":").append(valueIds.get(i));
        }

        return hashString(normalized.toString());
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
