module kotowari.graalvm {
    exports enkan.graalvm;
    exports kotowari.graalvm;

    requires enkan.core;
    requires transitive enkan.system;
    requires transitive enkan.web;
    requires jakarta.annotation;
    requires transitive kotowari;
    requires transitive org.graalvm.nativeimage;

    opens kotowari.graalvm;
    opens enkan.graalvm;
}
