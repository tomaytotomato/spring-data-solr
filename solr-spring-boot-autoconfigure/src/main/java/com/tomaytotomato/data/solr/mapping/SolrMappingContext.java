package com.tomaytotomato.data.solr.mapping;

import org.springframework.data.core.TypeInformation;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;

public class SolrMappingContext extends AbstractMappingContext<SolrPersistentEntity<?>, SolrPersistentProperty> {

  @Override
  protected <T> SolrPersistentEntity<T> createPersistentEntity(TypeInformation<T> typeInformation) {
    return new SolrPersistentEntity<>(typeInformation);
  }

  @Override
  protected SolrPersistentProperty createPersistentProperty(Property property,
      SolrPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
    return new SolrPersistentProperty(property, owner, simpleTypeHolder);
  }

  @Override
  protected boolean shouldCreatePersistentEntityFor(TypeInformation<?> type) {
    return type.getType().isAnnotationPresent(SolrDocument.class);
  }
}
