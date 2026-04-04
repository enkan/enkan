package enkan.predicate;

import enkan.data.UriAvailable;
import org.junit.jupiter.api.Test;

import static enkan.predicate.PathPredicate.*;
import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class PathPredicateTest {
    private static class Request implements UriAvailable {
        private String uri;
        private String requestMethod;

        public Request(String uri, String requestMethod) {
            this.uri = uri;
            this.requestMethod = requestMethod;
        }

        public String getUri() {
            return this.uri;
        }

        public String getRequestMethod() {
            return this.requestMethod;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public void setRequestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
        }

        public String toString() {
            return "PathPredicateTest.Request(uri=" + this.getUri() + ", requestMethod=" + this.getRequestMethod() + ")";
        }
    }

    @Test
    public void anyMethod() {
        assertThat(ANY("/any").test(new Request("/any", "GET"))).isTrue();
        assertThat(ANY("/any").test(new Request("/any", "POST"))).isTrue();
        assertThat(ANY("/any").test(new Request("/anyy", "GET"))).isFalse();

        assertThat(ANY("/any/?.*").test(new Request("/any", "POST"))).isTrue();
        assertThat(ANY("/any(/?$|/.*)").test(new Request("/anyo", "POST"))).isFalse();
        assertThat(ANY("/any/?.*").test(new Request("/any/asvao", "POST"))).isTrue();
    }

    @Test
    public void getMethod() {
        assertThat(GET("/get").test(new Request("/get", "GET"))).isTrue();
        assertThat(GET("/get").test(new Request("/get", "POST"))).isFalse();
    }
}
