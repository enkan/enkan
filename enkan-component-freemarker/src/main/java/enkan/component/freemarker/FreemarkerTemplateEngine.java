package enkan.component.freemarker;

import enkan.component.ComponentLifecycle;
import enkan.web.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.web.util.HttpResponseUtils;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.OutputFormat;
import freemarker.ext.beans.BeanModel;
import freemarker.template.*;
import kotowari.component.TemplateEngine;
import kotowari.data.TemplatedHttpResponse;
import kotowari.data.Validatable;
import kotowari.io.LazyRenderInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;


/**
 * Template engine component using by Freemarker.
 *
 * @author kawasima
 */
public class FreemarkerTemplateEngine extends TemplateEngine<FreemarkerTemplateEngine> {
    private Configuration config;
    private String prefix = "templates";
    private String suffix = ".ftl";
    private ClassLoader classLoader;
    private Charset encoding = StandardCharsets.UTF_8;
    private TemplateLoader templateLoader;

    private OutputFormat outputFormat = HTMLOutputFormat.INSTANCE;

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpResponse render(String name, Object... keyOrVals) {
        TemplatedHttpResponse response = TemplatedHttpResponse.create(name, keyOrVals);
        response.setBody(new LazyRenderInputStream(() -> {
            try {
                Template template = config.getTemplate(name + suffix, encoding.name());
                StringWriter writer = new StringWriter();
                template.process(response.getContext(), writer);
                return new ByteArrayInputStream(writer.toString().getBytes(encoding));
            } catch (TemplateException e) {
                throw new MisconfigurationException("freemarker.TEMPLATE", e.getFTLInstructionStack(), e.getMessageWithoutStackTop(), e);
            } catch (IOException e) {
                throw new MisconfigurationException("freemarker.TEMPLATE", e.getMessage(),
                        String.format(Locale.US, "Make a template '%s'.", name), e);
            }

        }));
        HttpResponseUtils.contentType(response, "text/html");
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ComponentLifecycle<FreemarkerTemplateEngine> lifecycle() {
        return new ComponentLifecycle<>() {
            @Override
            public void start(FreemarkerTemplateEngine component) {
                ClassLoader effectiveLoader = (classLoader != null) ? classLoader
                        : Thread.currentThread().getContextClassLoader();
                config = new Configuration(Configuration.VERSION_2_3_34);
                config.setTemplateLoader(createTemplateLoader(effectiveLoader));
                config.setOutputFormat(outputFormat);
                config.setObjectWrapper(new DefaultObjectWrapper(Configuration.VERSION_2_3_34) {
                    @Override
                    protected TemplateModel handleUnknownType(final Object obj) throws TemplateModelException {
                        if (obj instanceof Validatable v) {
                            return new ValidatableFormAdapter(v, this);
                        }
                        return super.handleUnknownType(obj);
                    }
                });
            }

            @Override
            public void stop(FreemarkerTemplateEngine component) {
                config = null;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object createFunction(Function<List<?>, Object> func) {
        return (TemplateMethodModelEx) arguments ->
                func.apply(((List<Object>) arguments).stream().map(arg -> {
                    if (arg instanceof BeanModel bm) {
                        return bm.getWrappedObject();
                    } else {
                        return arg;
                    }
                }).toList());
    }

    private TemplateLoader createTemplateLoader(ClassLoader effectiveLoader) {
        TemplateLoader classTemplateLoader = new ClassTemplateLoader(effectiveLoader, prefix);
        if (templateLoader == null) {
            return classTemplateLoader;
        }
        if (templateLoader instanceof MultiTemplateLoader mtl) {
            List<TemplateLoader> loaders = new ArrayList<>();
            for (int i = 0; i < mtl.getTemplateLoaderCount(); i++) {
                loaders.add(mtl.getTemplateLoader(i));
            }
            loaders.add(classTemplateLoader);
            return new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0]));
        } else {
            return new MultiTemplateLoader(new TemplateLoader[]{templateLoader, classTemplateLoader});
        }
    }

    /**
     * Set an output format used by this freemarker.
     *
     * @param outputFormat FreeMarker output format
     */
    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * Set a template loader used by this freemarker.
     *
     * @param templateLoader template loader
     */
    public void setTemplateLoader(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    /**
     * Set the character encoding for template rendering.
     *
     * @param encoding charset name (e.g. "UTF-8")
     */
    public void setEncoding(String encoding) {
        this.encoding = Charset.forName(encoding);
    }

    /**
     * Set the ClassLoader used to load templates from the classpath.
     *
     * @param classLoader class loader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
}
