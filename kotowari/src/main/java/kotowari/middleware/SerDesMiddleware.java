package kotowari.middleware;

import enkan.Middleware;
import enkan.MiddlewareChain;
import enkan.collection.Headers;
import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import enkan.component.SystemComponent;
import enkan.data.*;
import enkan.exception.MisconfigurationException;
import enkan.system.inject.ComponentInjector;
import enkan.util.CodecUtils;
import enkan.util.HttpRequestUtils;
import enkan.util.MixinUtils;
import kotowari.data.BodyDeserializable;
import kotowari.inject.ParameterInjector;
import kotowari.util.ParameterUtils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

import static enkan.util.BeanBuilder.builder;
import static enkan.util.ThreadingUtils.some;

/**
 * Serialize a java object to response body and deserialize  a response body
 * to a java object.
 *
 * @author kawasima
 */
@enkan.annotation.Middleware(name = "serDes", dependencies = {"contentNegotiation", "routing"}, mixins = BodyDeserializable.class)
public class SerDesMiddleware<NRES> implements Middleware<HttpRequest, HttpResponse, HttpRequest, NRES> {
    @Inject
    protected BeansConverter beans;

    private final List<MessageBodyReader<?>> bodyReaders = new ArrayList<>();
    private final List<MessageBodyWriter<?>> bodyWriters = new ArrayList<>();
    private List<ParameterInjector<?>> parameterInjectors;

    @PostConstruct
    private void loadReaderAndWriter() {
        Map<String, SystemComponent<?>> components = new HashMap<>();
        components.put("beans", (SystemComponent<?>) beans);
        ComponentInjector injector = new ComponentInjector(components);
        ClassLoader cl = Optional.ofNullable(Thread.currentThread().getContextClassLoader())
                .orElse(getClass().getClassLoader());
        for (MessageBodyReader<?> reader : ServiceLoader.load(MessageBodyReader.class, cl)) {
            injector.inject(reader);
            bodyReaders.add(reader);
        }
        for (MessageBodyWriter<?> writer : ServiceLoader.load(MessageBodyWriter.class, cl)) {
            injector.inject(writer);
            bodyWriters.add(writer);
        }

        if (parameterInjectors == null) {
            parameterInjectors = ParameterUtils.getDefaultParameterInjectors();
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T deserialize(HttpRequest request, Class<T> type, Type genericType, MediaType mediaType) throws IOException {
        try {
            MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
            return bodyReaders.stream()
                    .filter(reader -> reader.isReadable(type, genericType, null, mediaType))
                    .map(reader -> {
                        try {
                            return ((MessageBodyReader<T>) reader)
                                    .readFrom(type, genericType, null, mediaType, headers, request.getBody());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElse(null);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    protected InputStream serialize(Object obj, MediaType mediaType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        if (obj == null) {
            return new ByteArrayInputStream(new byte[0]);
        }

        try {
            return bodyWriters.stream()
                    .filter(writer -> writer.isWriteable(obj.getClass(), obj.getClass(), null, mediaType))
                    .map(writer -> {
                        try {
                            ((MessageBodyWriter<Object>) writer).writeTo(obj, obj.getClass(), obj.getClass(), null, mediaType, headers, baos);
                            return baos.toByteArray();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .map(ByteArrayInputStream::new)
                    .findFirst()
                    .orElse(null);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void deserializeBody(Method method, HttpRequest request) throws IOException {
        String contentType = HttpRequestUtils.contentType(request);
        if (contentType == null) return;

        String[] mediaTypeTokens = contentType.split("/", 2);
        if (mediaTypeTokens.length == 2) {
            MediaType mediaType = new MediaType(mediaTypeTokens[0], mediaTypeTokens[1]);
            Parameter[] parameters = method != null ? method.getParameters() : new Parameter[0];
            long bodyParamCount = Arrays.stream(parameters)
                    .filter(p -> parameterInjectors.stream().noneMatch(injector -> injector.isApplicable(p.getType())))
                    .count();
            if (bodyParamCount > 1) {
                throw new MisconfigurationException("kotowari.AMBIGUOUS_BODY_PARAMETER",
                        method.getDeclaringClass().getSimpleName(), method.getName());
            }
            BodyDeserializable bodyDeserializable = (BodyDeserializable) request;
            for (Parameter parameter : parameters) {
                Class<?> type = parameter.getType();
                Type genericType = parameter.getParameterizedType();

                if (parameterInjectors.stream().anyMatch(injector-> injector.isApplicable(type)))
                    continue;

                Object body = deserialize(request, type, genericType, mediaType);
                bodyDeserializable.setDeserializedBody(body);
                break;
            }
            if (bodyDeserializable.getDeserializedBody() == null) {
                bodyDeserializable.setDeserializedBody(deserialize(request, Object.class, Object.class, mediaType));
            }
        }
    }

    public void handleRequest(HttpRequest request) throws IOException {
        Method method = ((Routable) request).getControllerMethod();
        if (HttpRequestUtils.isUrlEncodedForm(request)) {
            BodyDeserializable bodyDeserializable = (BodyDeserializable) request;
            if (bodyDeserializable.getDeserializedBody() == null) {
                Parameter[] parameters = some(method, Method::getParameters).orElse(new Parameter[0]);
                for (Parameter parameter : parameters) {
                    Class<?> type = parameter.getType();
                    if (parameterInjectors.stream().anyMatch(injector-> injector.isApplicable(type)))
                        continue;
                    bodyDeserializable.setDeserializedBody(beans.createFrom(
                            request.getParams(), type
                    ));
                }
            }
        } else {
            deserializeBody(method, request);
        }
    }

    /** {@inheritDoc} Deserializes the request body and serializes the response body. */
    @Override
    public <NNREQ, NNRES> HttpResponse handle(HttpRequest request, MiddlewareChain<HttpRequest, NRES, NNREQ, NNRES> chain) {
        request = MixinUtils.mixin(request, BodyDeserializable.class);
        MediaType responseType = ((ContentNegotiable) request).getMediaType();
        try {
            handleRequest(request);
        } catch (IOException e) {
            try {
                return builder(HttpResponse.of(serialize(Parameters.of("title",
                        "bad request format"), responseType)))
                        .set(HttpResponse::setStatus, 400)
                        .build();
            } catch (IOException serializeEx) {
                throw new UncheckedIOException(serializeEx);
            }
        }

        NRES response = chain.next(request);

        if (response instanceof HttpResponse httpResponse) {
            return httpResponse;
        } else {
            try {
                InputStream in = serialize(extractBody(response), responseType);
                if (in != null) {
                    return builder(HttpResponse.of(in))
                            .set(HttpResponse::setHeaders, extractHeaders(response, responseType))
                            .set(HttpResponse::setStatus, extractStatus(response))
                            .build();
                } else {
                    return builder(HttpResponse.of("Not acceptable"))
                            .set(HttpResponse::setStatus, 406)
                            .build();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public void setBodyReaders(MessageBodyReader<?>... readers) {
        bodyReaders.addAll(Arrays.asList(readers));
    }

    public void setBodyWriters(MessageBodyWriter<?>... writers) {
        bodyWriters.addAll(Arrays.asList(writers));
    }

    public void setParameterInjectors(List<ParameterInjector<?>> parameterInjectors) {
        this.parameterInjectors = parameterInjectors;
    }

    private Object extractBody(NRES response) {
        if (response instanceof HasBody hasBody) {
            return hasBody.getBody();
        } else {
            return response;
        }
    }

    private Headers extractHeaders(NRES response, MediaType responseType) {
        Headers headers;
        if (response instanceof HasHeaders hasHeaders) {
            headers = hasHeaders.getHeaders();
        } else {
            headers = Headers.empty();
        }
        headers.put("Content-Type", CodecUtils.printMediaType(responseType));
        return headers;
    }

    private int extractStatus(NRES response) {
        if (response instanceof HasStatus hasStatus) {
            return hasStatus.getStatus();
        } else {
            return 200;
        }
    }
}
