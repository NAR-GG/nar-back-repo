package com.toy.nar.domain.ad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.toy.nar.domain.ad.entity.Ad;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdTest {

    private static Ad ad(String placement, String link, LocalDateTime s, LocalDateTime e) {
        return new Ad("t", placement, "https://img/x.png", link, 0, true, s, e);
    }

    @Test
    void 정상값은_저장되고_빈_링크는_null() {
        assertThat(ad("home", " ", null, null).getLinkUrl()).isNull();
        assertThat(ad("match_detail", "https://a.b", null, null).getLinkUrl()).isEqualTo("https://a.b");
        assertThat(ad("splash", null, null, null).getPlacement()).isEqualTo("splash");
        assertThat(ad("popup", null, null, null).getPlacement()).isEqualTo("popup");
    }

    @Test
    void 잘못된_위치_링크_기간은_거부() {
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> ad("nope", null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ad("home", "http://a.b", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ad("home", null, now, now)).isInstanceOf(IllegalArgumentException.class);
    }
}
