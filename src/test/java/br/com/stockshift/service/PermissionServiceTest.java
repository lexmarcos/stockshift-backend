package br.com.stockshift.service;

import br.com.stockshift.dto.permission.PermissionResponse;
import br.com.stockshift.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Test
    void findAllShouldIncludeSalesPermissions() {
        when(permissionRepository.findByCodeIgnoreCase(anyString())).thenReturn(Optional.empty());
        PermissionService service = new PermissionService(permissionRepository);

        assertThat(service.findAll())
                .extracting(PermissionResponse::getCode)
                .contains("sales:create", "sales:read", "sales:cancel");
    }
}
