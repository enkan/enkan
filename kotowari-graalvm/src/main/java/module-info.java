module kotowari.graalvm {
    exports enkan.graalvm;
    exports kotowari.graalvm;

    requires enkan.core;
    requires enkan.web;
    requires jakarta.annotation;
    requires kotowari;
    requires org.graalvm.nativeimage;

    opens kotowari.graalvm;
    opens enkan.graalvm;
}
