package enkan.data.jpa;

import enkan.data.Extendable;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.Nullable;

public interface EntityManageable extends Extendable {
    default void setEntityManager(EntityManager entityManager) {
        setExtension("entityManager", entityManager);
    }

    default @Nullable EntityManager getEntityManager() {
        return getExtension("entityManager");
    }
}
