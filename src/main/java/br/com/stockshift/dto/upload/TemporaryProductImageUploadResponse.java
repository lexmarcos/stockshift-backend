package br.com.stockshift.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TemporaryProductImageUploadResponse {
    private UUID uploadId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
}
