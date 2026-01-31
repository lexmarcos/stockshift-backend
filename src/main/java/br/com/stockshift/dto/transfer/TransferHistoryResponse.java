package br.com.stockshift.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferHistoryResponse {
    private UUID transferId;
    private String transferCode;
    private List<TransferEventResponse> events;
}
