package kotowari.inject.parameter;

import enkan.web.data.HttpRequest;
import enkan.data.jpa.EntityManageable;
import kotowari.inject.ParameterInjector;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.EntityManager;
import java.util.Optional;

/**
 * The parameter injector for entity manager.
 *
 * @author kawasima
 */
public class EntityManagerInjector implements ParameterInjector<EntityManager> {
    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "entityManager";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicable(Class<?> type) {
        return EntityManager.class.isAssignableFrom(type);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable EntityManager getInjectObject(HttpRequest request) {
        return Optional.ofNullable(request)
                .filter(EntityManageable.class::isInstance)
                .map(EntityManageable.class::cast)
                .map(EntityManageable::getEntityManager)
                .orElse(null);
    }
}
