package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotWarehouseResponse {
    private UUID id;
    private String name;
    private String code;
    private String city;
    private String state;
}
