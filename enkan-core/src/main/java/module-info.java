module enkan.core {
    exports enkan;
    exports enkan.annotation;
    exports enkan.chain;
    exports enkan.collection;
    exports enkan.data;
    exports enkan.exception;
    exports enkan.middleware;
    exports enkan.predicate;
    exports enkan.security;
    exports enkan.util;

    requires transitive jakarta.inject;
    requires jakarta.cdi;
    requires jakarta.validation;
    requires java.logging;
    requires org.slf4j;

    // MixinUtils.createFactory() generates subclass inside enkan.util at runtime
    opens enkan.util;
}
