package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.TransferEventType;
import br.com.stockshift.model.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEventResponse {
    private UUID id;
    private TransferEventType eventType;
    private TransferStatus fromStatus;
    private TransferStatus toStatus;
    private UUID performedBy;
    private String performedByName;
    private LocalDateTime performedAt;
    private Map<String, Object> metadata;
}
