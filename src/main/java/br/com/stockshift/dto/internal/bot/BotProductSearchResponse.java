package br.com.stockshift.dto.internal.bot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotProductSearchResponse {
    private List<BotProductSearchResultResponse> results;
    private Boolean hasMore;
}
