package br.com.stockshift.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
  private UUID tenantId;
  private String businessName;
  private UUID userId;
  private String userEmail;
  private String accessToken;
  private String refreshToken;
  private String tokenType;
  private Long expiresIn;
}
