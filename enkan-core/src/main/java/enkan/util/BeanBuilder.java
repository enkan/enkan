package enkan.util;

import enkan.exception.MisconfigurationException;

import org.jspecify.annotations.Nullable;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * A Utility of builder pattern.
 *
 * @author kawasima
 */
public class BeanBuilder<X> {
    private static final @Nullable Validator DEFAULT_VALIDATOR = createValidator();

    private static @Nullable Validator createValidator() {
        try {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Runtime.getRuntime().addShutdownHook(new Thread(factory::close,
                    "BeanBuilder-ValidatorFactory-shutdown"));
            return factory.getValidator();
        } catch (Exception | NoClassDefFoundError e) {
            // No validation provider on the classpath — skip validation.
            return null;
        }
    }

    private final X x;

    private BeanBuilder(X x) {
        this.x = x;
    }

    public static <Y> BeanBuilder<Y> builder(Y x) {
        return new BeanBuilder<>(x);
    }

    public <V> BeanBuilder<X> set(BiConsumer<X, V> caller, V v) {
        caller.accept(x, v);
        return this;
    }

    public X build() {
        if (DEFAULT_VALIDATOR != null) {
            Set<ConstraintViolation<X>> violations = DEFAULT_VALIDATOR.validate(x);
            if (!violations.isEmpty()) {
                throw new MisconfigurationException("core.BUILD_ERROR", x.getClass().getName(),
                        violations.stream().map(ConstraintViolation::getMessage)
                                .collect(Collectors.joining(",")));
            }
        }
        return x;
    }
}
