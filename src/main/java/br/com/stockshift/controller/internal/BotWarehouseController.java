package br.com.stockshift.controller.internal;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.internal.bot.BotWarehouseResponse;
import br.com.stockshift.service.internal.BotWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/bot/warehouses")
@RequiredArgsConstructor
public class BotWarehouseController {

    private final BotWarehouseService botWarehouseService;

    @GetMapping
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<List<BotWarehouseResponse>>> findActiveWarehouses() {
        return ResponseEntity.ok(ApiResponse.success(botWarehouseService.findActiveWarehouses()));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<List<BotWarehouseResponse>>> searchActiveWarehouses(
            @RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success(botWarehouseService.searchActiveWarehouses(query)));
    }
}
