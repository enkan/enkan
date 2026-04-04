package kotowari.middleware.serdes;

import enkan.collection.Parameters;
import enkan.component.SystemComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.system.EnkanSystem;
import enkan.system.inject.ComponentInjector;
import enkan.web.util.CodecUtils;
import kotowari.test.dto.TestDto;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class UrlFormEncodedBodyWriterTest {
    @Test
    public void test() throws IOException {
        EnkanSystem system = EnkanSystem.of("beans", new JacksonBeansConverter());
        system.start();
        Map<String, SystemComponent<?>> components = new HashMap<>() {{
            put("beans", system.getComponent("beans"));
        }};
        ComponentInjector injector = new ComponentInjector(components);
        UrlFormEncodedBodyWriter bodyWriter = new UrlFormEncodedBodyWriter();
        injector.inject(bodyWriter);

        TestDto dto = new TestDto();
        dto.setA(8);
        dto.setB("あいうえお");
        try(ByteArrayOutputStream entityStream = new ByteArrayOutputStream()) {
            bodyWriter.writeTo(dto, TestDto.class, TestDto.class, null, new MediaType("", ""),
                    new MultivaluedHashMap<>(), entityStream);

            String out = entityStream.toString();
            Parameters params = CodecUtils.formDecode(out, "UTF-8");
            assertThat(params.getLong("a")).isEqualTo(8);
            assertThat(params.get("b")).isEqualTo("あいうえお");
        }
    }
}
