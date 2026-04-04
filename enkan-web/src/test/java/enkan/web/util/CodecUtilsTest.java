package enkan.web.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class CodecUtilsTest {
    @Test
    public void parseBytes() {
        byte[] b = CodecUtils.parseBytes("%e3%81%82%e3%81%84%e3%81%86");
        assertThat(b).isEqualTo(new byte[]{
                (byte) 0xE3, (byte) 0x81, (byte) 0x82,
                (byte) 0xE3, (byte) 0x81, (byte) 0x84,
                (byte) 0xE3, (byte) 0x81, (byte) 0x86,
        });
    }

    @Test
    public void urlEncode() {
        assertThat(CodecUtils.urlEncode("abcあいうdef"))
                .isEqualTo("abc%e3%81%82%e3%81%84%e3%81%86def");
    }

    @Test
    public void urlDecode() {
        assertThat(CodecUtils.urlDecode("abc%e3%81%82def"))
                .isEqualTo("abcあdef");
    }

    @Test
    public void urlDecodeWithDollarSign() {
        // %24 decodes to '$', which is special in Matcher.appendReplacement if not quoted.
        assertThat(CodecUtils.urlDecode("%24")).isEqualTo("$");
        assertThat(CodecUtils.urlDecode("%241")).isEqualTo("$1");
        assertThat(CodecUtils.urlDecode("price%3D%241")).isEqualTo("price=$1");
    }

    @Test
    public void urlDecodeWithBackslash() {
        // %5C decodes to '\', which is special in Matcher.appendReplacement if not quoted.
        assertThat(CodecUtils.urlDecode("%5C")).isEqualTo("\\");
        assertThat(CodecUtils.urlDecode("a%5Cb")).isEqualTo("a\\b");
    }

    @Test
    public void formEncode() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", null);
        assertThat(CodecUtils.formEncode(m)).isEqualTo("a=");
    }

    @Test
    public void formEncodeListParameter() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", Arrays.asList("1", "2", "3"));
        assertThat(CodecUtils.formEncode(m)).isEqualTo("a=1&a=2&a=3");
    }

    @Test
    public void formEncodeSetParameter() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", new TreeSet<>(Arrays.asList("1", "2", "3")));
        assertThat(CodecUtils.formEncode(m)).isEqualTo("a=1&a=2&a=3");
    }
}
