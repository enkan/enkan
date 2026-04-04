module enkan.web {
    exports enkan.web.application;
    exports enkan.web.collection;
    exports enkan.web.data;
    exports enkan.web.endpoint;
    exports enkan.web.middleware;
    exports enkan.web.middleware.idempotency;
    exports enkan.web.middleware.multipart;
    exports enkan.web.middleware.negotiation;
    exports enkan.web.middleware.normalizer;
    exports enkan.web.middleware.session;
    exports enkan.web.security.backend;
    exports enkan.web.util;
    exports enkan.web.util.sf;

    requires transitive enkan.core;
    requires transitive enkan.system;
    requires jakarta.cdi;
    requires jakarta.validation;
    requires jakarta.ws.rs;
    requires java.logging;
    requires static com.ibm.icu;

    // MixinUtils.createFactory() generates DefaultHttpRequest$Mixin inside enkan.web.data at runtime
    opens enkan.web.data;
}
