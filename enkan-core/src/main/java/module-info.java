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

    // MixinUtils.lookupSpecial() uses privateLookupIn on interfaces in enkan.util.
    // org.hibernate.validator needs access for field-level constraint validation on
    // test inner classes defined in this package.
    opens enkan.util to enkan.core, org.hibernate.validator;
}
