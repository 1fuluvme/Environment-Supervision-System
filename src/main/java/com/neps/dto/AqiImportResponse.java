package com.neps.dto;

import java.util.List;

public record AqiImportResponse(
        boolean success,
        Long batchId,
        String batchNo,
        int recordCount,
        List<RowError> errors) {

    public record RowError(
            int rowNumber,
            String reason) {
    }
}
