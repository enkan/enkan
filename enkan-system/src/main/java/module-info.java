module enkan.system {
    exports enkan.component;
    exports enkan.component.builtin;
    exports enkan.config;
    exports enkan.system;
    exports enkan.system.command;
    exports enkan.system.inject;
    exports enkan.system.repl;

    requires transitive enkan.core;
    requires transitive jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.validation;
    requires jakarta.transaction;
    requires static java.desktop;  // optional: only used by StartCommand.execute() to open a browser
    requires java.logging;
    requires java.sql;
    requires org.crac;
    requires org.slf4j;
}
