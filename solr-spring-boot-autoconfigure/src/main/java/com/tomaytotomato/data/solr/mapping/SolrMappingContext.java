package com.tomaytotomato.data.solr.mapping;

import org.springframework.core.env.Environment;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.lang.Nullable;

/**
 * Spring Data {@link org.springframework.data.mapping.context.MappingContext} implementation for
 * Apache Solr.
 *
 * <p>Manages {@link SolrPersistentEntity} and {@link SolrPersistentProperty} instances for all
 * domain types annotated with {@link SolrDocument}. Only types carrying that annotation are
 * considered for entity creation; all others are skipped.
 *
 * <p>When constructed with an {@link Environment}, any {@code ${placeholder}} expression in a
 * document's collection name is resolved eagerly against that environment at entity creation time.
 * Use the no-arg constructor when no placeholder resolution is needed.
 *
 * <p>This context is registered as a Spring bean by the auto-configuration and is used internally
 * by the repository infrastructure and the field-name resolution layer.
 *
 * @since 0.1.0
 */
public class SolrMappingContext extends AbstractMappingContext<SolrPersistentEntity<?>, SolrPersistentProperty> {

  @Nullable
  private final Environment environment;

  /**
   * Creates a new {@link SolrMappingContext} without placeholder resolution support.
   */
  public SolrMappingContext() {
    this(null);
  }

  /**
   * Creates a new {@link SolrMappingContext} that resolves {@code ${placeholder}} expressions in
   * collection names against the supplied {@link Environment}.
   *
   * @param environment Spring {@link Environment} for placeholder resolution; may be {@code null}
   */
  public SolrMappingContext(@Nullable Environment environment) {
    this.environment = environment;
  }

  /**
   * Creates a new {@link SolrPersistentEntity} for the given type information, passing the
   * configured {@link Environment} so that collection-name placeholders are resolved eagerly.
   *
   * @param <T> the entity type
   * @param typeInformation type metadata provided by the Spring Data infrastructure
   * @return a new {@link SolrPersistentEntity} wrapping the given type
   */
  @Override
  protected <T> SolrPersistentEntity<T> createPersistentEntity(TypeInformation<T> typeInformation) {
    return new SolrPersistentEntity<>(typeInformation, environment);
  }

  /**
   * Creates a new {@link SolrPersistentProperty} for the given property within an entity.
   *
   * @param property the property descriptor
   * @param owner the owning persistent entity
   * @param simpleTypeHolder holder of simple types known to the mapping infrastructure
   * @return a new {@link SolrPersistentProperty} for the given property
   */
  @Override
  protected SolrPersistentProperty createPersistentProperty(Property property,
      SolrPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
    return new SolrPersistentProperty(property, owner, simpleTypeHolder);
  }

  /**
   * Returns {@code true} only for types annotated with {@link SolrDocument}.
   *
   * @param type the type to evaluate
   * @return {@code true} if the type carries a {@link SolrDocument} annotation
   */
  @Override
  protected boolean shouldCreatePersistentEntityFor(TypeInformation<?> type) {
    return type.getType().isAnnotationPresent(SolrDocument.class);
  }
}
