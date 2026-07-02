package br.com.stockshift.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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
