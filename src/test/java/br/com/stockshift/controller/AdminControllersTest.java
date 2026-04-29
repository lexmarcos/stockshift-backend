package br.com.stockshift.controller;

import br.com.stockshift.dto.role.RoleRequest;
import br.com.stockshift.dto.role.RoleResponse;
import br.com.stockshift.dto.stockmovement.CreateStockMovementRequest;
import br.com.stockshift.dto.stockmovement.StockMovementResponse;
import br.com.stockshift.dto.stockmovement.WarehouseMovementSummaryResponse;
import br.com.stockshift.dto.transfer.CancelTransferRequest;
import br.com.stockshift.dto.transfer.CompleteValidationResponse;
import br.com.stockshift.dto.transfer.CreateTransferRequest;
import br.com.stockshift.dto.transfer.DiscrepancyReportResponse;
import br.com.stockshift.dto.transfer.ScanBarcodeRequest;
import br.com.stockshift.dto.transfer.ScanBarcodeResponse;
import br.com.stockshift.dto.transfer.TransferResponse;
import br.com.stockshift.dto.transfer.UpdateTransferRequest;
import br.com.stockshift.dto.transfer.ValidationLogResponse;
import br.com.stockshift.dto.user.CreateUserRequest;
import br.com.stockshift.dto.user.CreateUserResponse;
import br.com.stockshift.dto.user.UpdateUserRequest;
import br.com.stockshift.dto.user.UserResponse;
import br.com.stockshift.model.enums.StockMovementType;
import br.com.stockshift.model.enums.TransferStatus;
import br.com.stockshift.service.RoleService;
import br.com.stockshift.service.UserService;
import br.com.stockshift.service.stockmovement.StockMovementService;
import br.com.stockshift.service.transfer.TransferService;
import br.com.stockshift.service.transfer.TransferValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllersTest {

    @Mock
    private RoleService roleService;
    @Mock
    private UserService userService;
    @Mock
    private StockMovementService stockMovementService;
    @Mock
    private TransferService transferService;
    @Mock
    private TransferValidationService transferValidationService;

    @Test
    void roleControllerShouldWrapCrudResponses() {
        RoleController controller = new RoleController(roleService);
        UUID id = UUID.randomUUID();
        RoleResponse response = RoleResponse.builder().id(id).name("Seller").build();
        when(roleService.create(any())).thenReturn(response);
        when(roleService.findAll()).thenReturn(List.of(response));
        when(roleService.findById(id)).thenReturn(response);
        when(roleService.update(eq(id), any())).thenReturn(response);

        assertThat(controller.create(new RoleRequest("Seller", "desc", Set.of())).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findAll().getBody().getData()).hasSize(1);
        assertThat(controller.findById(id).getBody().getData().getName()).isEqualTo("Seller");
        assertThat(controller.update(id, new RoleRequest("Seller", "desc", Set.of())).getBody().getSuccess()).isTrue();
        assertThat(controller.delete(id).getBody().getSuccess()).isTrue();
        verify(roleService).delete(id);
    }

    @Test
    void userControllerShouldWrapCrudResponses() {
        UserController controller = new UserController(userService);
        UUID id = UUID.randomUUID();
        UserResponse response = UserResponse.builder().id(id).email("user@example.com").build();
        when(userService.listUsers()).thenReturn(List.of(response));
        when(userService.createUser(any())).thenReturn(CreateUserResponse.builder().userId(id).build());
        when(userService.findById(id)).thenReturn(response);
        when(userService.updateUser(eq(id), any())).thenReturn(response);

        assertThat(controller.listUsers().getBody().getData()).hasSize(1);
        assertThat(controller.createUser(new CreateUserRequest("user@example.com", "User", Set.of(), Set.of()))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.findById(id).getBody().getData().getEmail()).isEqualTo("user@example.com");
        assertThat(controller.updateUser(id, new UpdateUserRequest("User", true, Set.of(), Set.of()))
                .getBody().getSuccess()).isTrue();
        assertThat(controller.deleteUser(id).getBody().getSuccess()).isTrue();
        verify(userService).deleteUser(id);
    }

    @Test
    void stockMovementControllerShouldWrapWriteReadAndSummaryResponses() {
        StockMovementController controller = new StockMovementController(stockMovementService);
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        StockMovementResponse response = StockMovementResponse.builder().id(id).warehouseId(warehouseId).build();
        when(stockMovementService.create(any(CreateStockMovementRequest.class))).thenReturn(response);
        when(stockMovementService.list(eq(warehouseId), eq(null), eq(StockMovementType.USAGE), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(stockMovementService.getById(id)).thenReturn(response);
        when(stockMovementService.getWarehouseSummary(null, null))
                .thenReturn(WarehouseMovementSummaryResponse.builder().warehouses(List.of()).build());

        CreateStockMovementRequest request = CreateStockMovementRequest.builder().type(StockMovementType.USAGE).build();
        assertThat(controller.create(request).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.list(warehouseId, null, StockMovementType.USAGE, null, null, PageRequest.of(0, 10))
                .getBody().getData().getContent()).hasSize(1);
        assertThat(controller.getById(id).getBody().getData().getId()).isEqualTo(id);
        assertThat(controller.warehouseSummary(null, null).getBody().getSuccess()).isTrue();
    }

    @Test
    void transferControllerShouldWrapLifecycleAndValidationResponses() {
        TransferController controller = new TransferController(transferService, transferValidationService);
        UUID id = UUID.randomUUID();
        TransferResponse response = TransferResponse.builder().id(id).status(TransferStatus.DRAFT).build();
        when(transferService.create(any())).thenReturn(response);
        when(transferService.list(eq(TransferStatus.DRAFT), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(response)));
        when(transferService.getById(id)).thenReturn(response);
        when(transferService.update(eq(id), any())).thenReturn(response);
        when(transferService.cancel(eq(id), any())).thenReturn(response);
        when(transferService.execute(id)).thenReturn(response);
        when(transferValidationService.startValidation(id)).thenReturn(response);
        when(transferValidationService.scanBarcode(eq(id), any()))
                .thenReturn(ScanBarcodeResponse.builder().valid(true).build());
        when(transferValidationService.completeValidation(id))
                .thenReturn(CompleteValidationResponse.builder().transferId(id).status(TransferStatus.COMPLETED).build());
        when(transferValidationService.getDiscrepancyReport(id))
                .thenReturn(DiscrepancyReportResponse.builder().transferId(id).build());
        when(transferValidationService.getValidationLogs(id))
                .thenReturn(List.of(ValidationLogResponse.builder().barcode("ABC").build()));

        assertThat(controller.create(new CreateTransferRequest()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(controller.list(TransferStatus.DRAFT, null, null, PageRequest.of(0, 10))
                .getBody().getData().getContent()).hasSize(1);
        assertThat(controller.getById(id).getBody().getData().getId()).isEqualTo(id);
        assertThat(controller.update(id, new UpdateTransferRequest()).getBody().getSuccess()).isTrue();
        assertThat(controller.cancel(id, null).getBody().getSuccess()).isTrue();
        assertThat(controller.execute(id).getBody().getSuccess()).isTrue();
        assertThat(controller.startValidation(id).getBody().getSuccess()).isTrue();
        assertThat(controller.scanBarcode(id, new ScanBarcodeRequest("ABC")).getBody().getData().isValid()).isTrue();
        assertThat(controller.completeValidation(id).getBody().getData().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(controller.getDiscrepancyReport(id).getBody().getData().getTransferId()).isEqualTo(id);
        assertThat(controller.getValidationLogs(id).getBody().getData()).hasSize(1);
    }
}
