package enkan.component.thymeleaf;

import enkan.component.ComponentLifecycle;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.web.util.HttpResponseUtils;
import kotowari.component.TemplateEngine;
import kotowari.data.TemplatedHttpResponse;
import kotowari.io.LazyRenderInputStream;
import org.thymeleaf.cache.ICacheManager;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.linkbuilder.ILinkBuilder;
import org.thymeleaf.messageresolver.IMessageResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * @author kawasima
 */
public class ThymeleafTemplateEngine extends TemplateEngine<ThymeleafTemplateEngine> {
    private String prefix = "templates/";
    private String suffix = ".html";
    private ClassLoader classLoader;
    private Charset charset = StandardCharsets.UTF_8;
    private Locale locale = Locale.getDefault();

    private Set<IDialect> dialects;
    private Set<ITemplateResolver> templateResolvers;
    private Set<IMessageResolver> messageResolvers;
    private Set<ILinkBuilder> linkBuilders;

    private ICacheManager cacheManager;

    private org.thymeleaf.TemplateEngine thymeleafEngine;

    @Override
    public HttpResponse render(String name, Object... keyOrVals) {
        if (thymeleafEngine == null) {
            throw new MisconfigurationException("core.COMPONENT_NOT_FOUND", "ThymeleafTemplateEngine", getClass().getSimpleName());
        }
        TemplatedHttpResponse response = TemplatedHttpResponse.create(name, keyOrVals);
        response.setBody(new LazyRenderInputStream(() -> {
            Context ctx = new Context(locale, response.getContext());
            return new ByteArrayInputStream(thymeleafEngine.process(name, ctx).getBytes(charset));
        }));

        HttpResponseUtils.contentType(response, "text/html; charset=" + charset.name());
        return response;
    }

    @Override
    public Object createFunction(Function<List<?>, Object> func) {
        return func;
    }

    @Override
    protected ComponentLifecycle<ThymeleafTemplateEngine> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(ThymeleafTemplateEngine component) {
                component.thymeleafEngine = new org.thymeleaf.TemplateEngine();

                // Template Resolver
                if (component.templateResolvers == null) {
                    component.thymeleafEngine.setTemplateResolver(component.createDefaultTemplateResolver());
                } else {
                    component.thymeleafEngine.setTemplateResolvers(component.templateResolvers);
                }

                // Dialect
                if (component.dialects != null) {
                    component.thymeleafEngine.setDialects(component.dialects);
                }

                // Message Resolver
                if (component.messageResolvers != null) {
                    component.thymeleafEngine.setMessageResolvers(component.messageResolvers);
                }

                // Link Builders
                if (component.linkBuilders != null) {
                    component.thymeleafEngine.setLinkBuilders(component.linkBuilders);
                }

                // Cache Manager
                if (component.cacheManager != null) {
                    component.thymeleafEngine.setCacheManager(component.cacheManager);
                }
            }

            @Override
            public void stop(ThymeleafTemplateEngine component) {
                component.thymeleafEngine = null;
            }
        };
    }

    private ITemplateResolver createDefaultTemplateResolver() {
        ClassLoader cl = (classLoader != null) ? classLoader
                : Thread.currentThread().getContextClassLoader();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(cl);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
        resolver.setCharacterEncoding(charset.name());
        return resolver;
    }

    public void setDialects(Set<IDialect> dialects) {
        this.dialects = dialects;
    }

    public void setTemplateResolvers(Set<ITemplateResolver> templateResolvers) {
        this.templateResolvers = templateResolvers;
    }

    public void setMessageResolvers(Set<IMessageResolver> messageResolvers) {
        this.messageResolvers = messageResolvers;
    }

    public void setLinkBuilders(Set<ILinkBuilder> linkBuilders) {
        this.linkBuilders = linkBuilders;
    }

    public void setCacheManager(ICacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public void setEncoding(String encoding) {
        try {
            this.charset = Charset.forName(encoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new MisconfigurationException("core.UNSUPPORTED_ENCODING", encoding, e);
        }
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }
}
