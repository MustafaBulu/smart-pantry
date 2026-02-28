package com.mustafabulu.smartpantry.yemeksepeti.controller;

import com.mustafabulu.smartpantry.common.dto.request.YemeksepetiReverseGeocodeRequest;
import com.mustafabulu.smartpantry.common.dto.response.YemeksepetiVendorByLocationResponse;
import com.mustafabulu.smartpantry.yemeksepeti.service.YemeksepetiReverseGeocodeService;
import com.mustafabulu.smartpantry.yemeksepeti.service.YemeksepetiVendorResolverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/yemeksepeti")
@ConditionalOnProperty(prefix = "marketplace.ys", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YemeksepetiSettingsController {

    private final YemeksepetiReverseGeocodeService yemeksepetiReverseGeocodeService;
    private final YemeksepetiVendorResolverService yemeksepetiVendorResolverService;

    @PostMapping("/reverse-geocode")
    public ResponseEntity<String> reverseGeocodeYemeksepeti(
            @Valid @RequestBody YemeksepetiReverseGeocodeRequest request
    ) {
        return ResponseEntity.ok(yemeksepetiReverseGeocodeService.reverse(request));
    }

    @PostMapping("/vendor-by-location")
    public ResponseEntity<YemeksepetiVendorByLocationResponse> resolveYemeksepetiVendor(
            @Valid @RequestBody YemeksepetiReverseGeocodeRequest request
    ) {
        return ResponseEntity.ok(yemeksepetiVendorResolverService.resolve(request));
    }
}
