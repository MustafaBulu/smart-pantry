package com.mustafabulu.smartpantry.common.controller.impl;

import com.mustafabulu.smartpantry.common.controller.NeedListController;
import com.mustafabulu.smartpantry.common.dto.request.NeedListItemRequest;
import com.mustafabulu.smartpantry.common.dto.response.NeedListItemResponse;
import com.mustafabulu.smartpantry.common.service.NeedListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/needs")
@RequiredArgsConstructor
public class NeedListControllerImpl implements NeedListController {

    private final NeedListService needListService;

    @GetMapping
    public ResponseEntity<List<NeedListItemResponse>> listItems() {
        return ResponseEntity.ok(needListService.listItems());
    }

    @PutMapping
    @Override
    public ResponseEntity<List<NeedListItemResponse>> replaceAll(@RequestBody List<NeedListItemRequest> request) {
        return ResponseEntity.ok(needListService.replaceAll(request));
    }
}
