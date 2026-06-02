package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.mapping.SolrDocument;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport;
import org.springframework.data.repository.config.RepositoryConfigurationSource;

public class SolrRepositoryConfigurationExtension extends RepositoryConfigurationExtensionSupport {

  @Override
  public String getModuleName() {
    return "Solr";
  }

  @Override
  protected String getModulePrefix() {
    return "solr";
  }

  @Override
  public String getRepositoryFactoryBeanClassName() {
    return SolrRepositoryFactoryBean.class.getName();
  }

  @Override
  protected Collection<Class<? extends Annotation>> getIdentifyingAnnotations() {
    return Collections.singleton(SolrDocument.class);
  }

  @Override
  protected Collection<Class<?>> getIdentifyingTypes() {
    return Collections.singleton(SolrRepository.class);
  }

  /**
   * Bean name assigned by {@code @EnableConfigurationProperties} for {@code SolrProperties}.
   * Spring Boot constructs this as {@code prefix-fully.qualified.ClassName}.
   */
  private static final String SOLR_PROPERTIES_BEAN_NAME =
      "spring.solr-com.tomaytotomato.data.solr.SolrProperties";

  /** Bean name for the {@link com.tomaytotomato.data.solr.mapping.SolrDocumentResolver}. */
  private static final String SOLR_DOCUMENT_RESOLVER_BEAN_NAME = "solrDocumentResolver";

  private static final String SOLR_MAPPING_CONTEXT_BEAN_NAME = "solrMappingContext";

  private static final String DEFAULT_SOLR_TEMPLATE_BEAN_NAME = "solrTemplate";

  @Override
  public void postProcess(BeanDefinitionBuilder builder, RepositoryConfigurationSource source) {
    var templateRef = source.getAttribute("solrTemplateRef")
        .filter(s -> !s.isBlank())
        .orElse(DEFAULT_SOLR_TEMPLATE_BEAN_NAME);
    builder.addPropertyReference("solrTemplate", templateRef);
    builder.addPropertyReference("mappingContext", SOLR_MAPPING_CONTEXT_BEAN_NAME);
    builder.addPropertyReference("solrProperties", SOLR_PROPERTIES_BEAN_NAME);
    builder.addPropertyReference("documentResolver", SOLR_DOCUMENT_RESOLVER_BEAN_NAME);
  }

  @Override
  public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {
    var attributes = config.getAttributes();
    builder.addPropertyReference("solrTemplate", attributes.getString("solrTemplateRef"));
    builder.addPropertyReference("mappingContext", SOLR_MAPPING_CONTEXT_BEAN_NAME);
    builder.addPropertyReference("solrProperties", SOLR_PROPERTIES_BEAN_NAME);
    builder.addPropertyReference("documentResolver", SOLR_DOCUMENT_RESOLVER_BEAN_NAME);
  }
}
