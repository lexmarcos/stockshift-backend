package br.com.stockshift.controller;

import br.com.stockshift.dto.ApiResponse;
import br.com.stockshift.dto.report.DashboardAlertsResponse;
import br.com.stockshift.dto.report.DashboardKpisResponse;
import br.com.stockshift.dto.report.DashboardResponse;
import br.com.stockshift.dto.report.DashboardSummaryResponse;
import br.com.stockshift.dto.report.MovementTrendResponse;
import br.com.stockshift.dto.report.StockReportResponse;
import br.com.stockshift.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Reporting and dashboard endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard summary")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        DashboardResponse response = reportService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/stock")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get complete stock report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getStockReport() {
        List<StockReportResponse> report = reportService.getStockReport();
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/stock/low-stock")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get low stock report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getLowStockReport(
            @RequestParam(defaultValue = "10") Integer threshold,
            @RequestParam(required = false) Integer limit) {
        List<StockReportResponse> report = reportService.getLowStockReport(threshold, limit);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/stock/expiring")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get expiring products report")
    public ResponseEntity<ApiResponse<List<StockReportResponse>>> getExpiringProductsReport(
            @RequestParam(defaultValue = "30") Integer daysAhead,
            @RequestParam(required = false) Integer limit) {
        List<StockReportResponse> report = reportService.getExpiringProductsReport(daysAhead, limit);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard quick summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getDashboardSummary() {
        DashboardSummaryResponse response = reportService.getSummary();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/kpis")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard financial KPIs with month comparison")
    public ResponseEntity<ApiResponse<DashboardKpisResponse>> getDashboardKpis() {
        DashboardKpisResponse response = reportService.getKpis();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/alerts")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get dashboard operational alerts")
    public ResponseEntity<ApiResponse<DashboardAlertsResponse>> getDashboardAlerts() {
        DashboardAlertsResponse response = reportService.getAlerts();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/dashboard/movement-trend")
    @PreAuthorize("@permissionGuard.hasAny('reports:read')")
    @Operation(summary = "Get movement trend for chart")
    public ResponseEntity<ApiResponse<MovementTrendResponse>> getMovementTrend(
            @RequestParam(defaultValue = "30") Integer days) {
        MovementTrendResponse response = reportService.getMovementTrend(days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
