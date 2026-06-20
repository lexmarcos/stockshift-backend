package br.com.stockshift.controller.internal;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.internal.bot.BotProductSearchResponse;
import br.com.stockshift.service.internal.BotProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/internal/bot/products")
@RequiredArgsConstructor
public class BotProductController {

    private final BotProductSearchService botProductSearchService;

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('bot:internal')")
    public ResponseEntity<ApiResponse<BotProductSearchResponse>> search(
            @RequestParam String query,
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) Integer limit) {
        BotProductSearchResponse response = botProductSearchService.search(query, warehouseId, limit);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
