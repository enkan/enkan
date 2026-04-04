package enkan.web.middleware.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kawasima
 */
class IcuNormalizerTest {
    @Test
    void half2full() {
        IcuNormalizer normalizer = new IcuNormalizer("Halfwidth-Fullwidth");
        assertThat(normalizer.normalize("1234"))
                .isEqualTo("１２３４");
    }

    @Test
    void full2half() {
        IcuNormalizer normalizer = new IcuNormalizer("Fullwidth-Halfwidth");
        assertThat(normalizer.normalize("西新宿グランド１−５−４")).isEqualTo("西新宿ｸﾞﾗﾝﾄﾞ1−5−4");
    }
}
