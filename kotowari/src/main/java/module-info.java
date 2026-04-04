module kotowari {
    exports kotowari.component;
    exports kotowari.data;
    exports kotowari.inject;
    exports kotowari.inject.parameter;
    exports kotowari.middleware;
    exports kotowari.middleware.serdes;
    exports kotowari.routing;
    exports kotowari.routing.factory;
    exports kotowari.util;

    requires transitive enkan.web;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.transaction;
    requires jakarta.validation;
    requires jakarta.ws.rs;
    requires java.logging;

    // MixinUtils (enkan.core) needs privateLookupIn on interfaces in kotowari.data
    opens kotowari.data to enkan.core;
    // ComponentInjector (enkan.system) needs field access for dependency injection
    opens kotowari.middleware to enkan.system;
    opens kotowari.middleware.serdes to enkan.system;
}
