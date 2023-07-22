package com.retisio.arc.response.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetCatalogsResponse {

    private Pagination pagination;
    private List<GetCatalogResponse> catalogs;

    @Value
    @Data
    public static class Pagination {
        private Integer totalCount;
        private Integer limit;
        private Integer offset;
    }
}
