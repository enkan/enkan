package kotowari.example;

import enkan.Env;
import enkan.collection.OptionMap;
import enkan.component.*;
import enkan.component.builtin.HmacEncoder;
import enkan.component.LocalJobExecutor;
import enkan.component.doma2.DomaProvider;
import enkan.component.flyway.FlywayMigration;
import enkan.component.freemarker.FreemarkerTemplateEngine;
import enkan.component.hikaricp.HikariCPComponent;
import enkan.component.jackson.JacksonBeansConverter;
import enkan.component.opentelemetry.OpenTelemetryComponent;
import enkan.component.undertow.UndertowComponent;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import enkan.config.EnkanSystemFactory;
import enkan.system.EnkanSystem;
import org.seasar.doma.jdbc.Naming;
import org.seasar.doma.jdbc.dialect.H2Dialect;

import static enkan.component.ComponentRelationship.component;
import static enkan.util.BeanBuilder.builder;

/**
 * @author kawasima
 */
public class ExampleSystemFactory implements EnkanSystemFactory {
    @Override
    public EnkanSystem create() {
        return EnkanSystem.of(
                "hmac", new HmacEncoder(),
                "doma", builder(new DomaProvider())
                        .set(DomaProvider::setDialect, new H2Dialect())
                        .set(DomaProvider::setNaming, Naming.SNAKE_LOWER_CASE)
                        .build(),
                "jackson", new JacksonBeansConverter(),
                "flyway", new FlywayMigration(),
                "template", new FreemarkerTemplateEngine(),
                "opentelemetry", new OpenTelemetryComponent(
                        OpenTelemetrySdk.builder()
                                .setTracerProvider(SdkTracerProvider.builder()
                                        .setResource(Resource.create(Attributes.of(
                                                AttributeKey.stringKey("service.name"), "kotowari-example")))
                                        .addSpanProcessor(SimpleSpanProcessor.create(new Slf4jSpanExporter()))
                                        .build())
                                .buildAndRegisterGlobal()),
                "jobExecutor", new LocalJobExecutor(),
                "datasource", new HikariCPComponent(OptionMap.of("uri", "jdbc:h2:mem:test")),
                "app", new ApplicationComponent<>("kotowari.example.ExampleApplicationFactory"),
                "http", builder(new UndertowComponent())
                        .set(WebServerComponent::setPort, Env.getInt("PORT", 3000))
                        .set(WebServerComponent::setSslPort, Env.getInt("SSL_PORT", 3002))
                        .set(WebServerComponent::setSsl, Env.getInt("SSL_PORT", 0) != 0)
                        .set(WebServerComponent::setKeystorePath, Env.get("KEYSTORE_PATH"))
                        .set(WebServerComponent::setKeystorePassword, Env.get("KEYSTORE_PASSWORD"))
                        .build()
        ).relationships(
                component("http").using("app"),
                component("app").using("datasource", "template", "doma", "jackson", "opentelemetry", "hmac", "jobExecutor"),
                component("doma").using("datasource", "flyway"),
                component("flyway").using("datasource")
        );

    }

}
