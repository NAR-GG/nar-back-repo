package com.toy.nar.api.admin;

import com.toy.nar.domain.ad.entity.Ad;
import com.toy.nar.domain.ad.repository.AdRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 백오피스 광고 관리. {@code /api/admin/**} 는 ROLE_ADMIN 보호.
 * 이미지는 공지 이미지 업로드({@code POST /api/admin/notices/images})로 올리고 받은 URL 을 imageUrl 에 넣는다.
 */
@RestController
@RequestMapping("/api/admin/ads")
@RequiredArgsConstructor
public class AdminAdController {

    private final AdRepository adRepository;

    public record AdRow(Long id, String title, String placement, String imageUrl, String linkUrl,
                        int sortOrder, boolean active, LocalDateTime startAt, LocalDateTime endAt,
                        LocalDateTime createdAt) {
        static AdRow from(Ad a) {
            return new AdRow(a.getId(), a.getTitle(), a.getPlacement(), a.getImageUrl(), a.getLinkUrl(),
                    a.getSortOrder(), a.isActive(), a.getStartAt(), a.getEndAt(), a.getCreatedAt());
        }
    }

    public record AdSaveRequest(String title, String placement, String imageUrl, String linkUrl,
                                int sortOrder, boolean active, LocalDateTime startAt, LocalDateTime endAt) {}

    // ponytail: 페이징 없음 — 광고는 수십 건 이하. 넘치면 Page 로.
    @GetMapping
    public List<AdRow> list() {
        return adRepository.findAllByOrderByCreatedAtDesc().stream().map(AdRow::from).toList();
    }

    @GetMapping("/{id}")
    public AdRow get(@PathVariable Long id) {
        return AdRow.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdRow create(@RequestBody AdSaveRequest r) {
        return AdRow.from(adRepository.save(new Ad(r.title(), r.placement(), r.imageUrl(), r.linkUrl(),
                r.sortOrder(), r.active(), r.startAt(), r.endAt())));
    }

    @PutMapping("/{id}")
    public AdRow update(@PathVariable Long id, @RequestBody AdSaveRequest r) {
        Ad ad = find(id);
        ad.update(r.title(), r.placement(), r.imageUrl(), r.linkUrl(), r.sortOrder(), r.active(),
                r.startAt(), r.endAt());
        return AdRow.from(adRepository.save(ad));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adRepository.delete(find(id));
    }

    private Ad find(Long id) {
        return adRepository.findById(id).orElseThrow(() -> new NoSuchElementException("광고를 찾을 수 없습니다: " + id));
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> onNotFound(NoSuchElementException e) {
        return Map.of("message", e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> onInvalid(RuntimeException e) {
        return Map.of("message", e instanceof NullPointerException ? "제목·이미지는 필수입니다." : e.getMessage());
    }
}
