package com.mustafabulu.smartpantry.common.controller;

import com.mustafabulu.smartpantry.common.dto.request.NeedListItemRequest;
import com.mustafabulu.smartpantry.common.dto.response.NeedListItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Need List", description = "Need list persistence APIs")
public interface NeedListController {

    @Operation(summary = "Replace all need list items")
    ResponseEntity<List<NeedListItemResponse>> replaceAll(List<NeedListItemRequest> request);
}
