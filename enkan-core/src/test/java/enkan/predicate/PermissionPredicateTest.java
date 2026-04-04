package enkan.predicate;

import enkan.data.PrincipalAvailable;
import enkan.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * @author kawasima
 */
public class PermissionPredicateTest {
    static class PrincipalRequest implements PrincipalAvailable {
        private final Map<String, Object> extensions = new HashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getExtension(String name) {
            return (T) extensions.get(name);
        }

        @Override
        public <T> void setExtension(String name, T extension) {
            extensions.put(name, extension);
        }
    }

    static class TestUser implements UserPrincipal {
        private String name;
        private Set<String> permissions;

        public TestUser(String name, Set<String> permissions) {
            this.name = name;
            this.permissions = permissions;
        }

        @Override
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }

        public String getName() {
            return this.name;
        }

        public Set<String> getPermissions() {
            return this.permissions;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }

        public String toString() {
            return "PermissionPredicateTest.TestUser(name=" + this.getName() + ", permissions=" + this.getPermissions() + ")";
        }
    }

    @Test
    public void test() {
        PermissionPredicate<PrincipalRequest> pred = new PermissionPredicate<>("deleteRepository");
        PrincipalRequest req = new PrincipalRequest();
        TestUser user = new TestUser("kawasima", new HashSet<>(Arrays.asList("deleteRepository", "createRepository")));
        req.setPrincipal(user);
        assertThat(pred.test(req)).isTrue();

        TestUser user2 = new TestUser("kawasima", new HashSet<>(Collections.singletonList("readRepository")));
        req.setPrincipal(user2);
        assertThat(pred.test(req)).isFalse();
    }

}
