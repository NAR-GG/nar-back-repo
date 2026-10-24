package com.toy.nar.api.mobile.ad;

import com.toy.nar.domain.ad.entity.Ad;
import com.toy.nar.domain.ad.repository.AdRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 앱 광고 배너 조회. 공개 API(/api/** permitAll). 광고가 없으면 빈 배열 — 앱이 광고주 모집 배너로 대체한다. */
@Tag(name = "Mobile. 광고", description = "현재 노출 중인 광고 배너 (인증 불필요)")
@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class MobileAdController {

    private final AdRepository adRepository;

    public record AdResponse(Long id, String placement, String imageUrl, String linkUrl) {
        static AdResponse from(Ad a) {
            return new AdResponse(a.getId(), a.getPlacement(), a.getImageUrl(), a.getLinkUrl());
        }
    }

    @Operation(summary = "노출 중인 광고",
            description = "active 이고 기간 안인 광고 전부(순서 = sortOrder). placement(home|match_list|match_detail|splash|popup)별로 앱이 첫 건을 쓴다.")
    // 앱 첫 화면이 진입 즉시 부른다. 경기 시작 버스트에 요청마다 커넥션을 잡지 않게 60초 재사용한다.
    @Cacheable(value = "mobileAds", key = "'all'", sync = true)
    @GetMapping
    public List<AdResponse> ads() {
        return adRepository.findLive(LocalDateTime.now()).stream().map(AdResponse::from).toList();
    }
}
