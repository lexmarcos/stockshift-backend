package br.com.stockshift.dto.permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private UUID id;
    private String resource;
    private String resourceDisplayName;
    private String action;
    private String actionDisplayName;
    private String scope;
    private String scopeDisplayName;
    private String description;
}
