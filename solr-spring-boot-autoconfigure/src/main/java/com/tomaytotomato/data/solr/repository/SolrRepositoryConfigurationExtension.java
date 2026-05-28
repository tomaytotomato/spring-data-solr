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

  @Override
  public void postProcess(BeanDefinitionBuilder builder, RepositoryConfigurationSource source) {
    builder.addPropertyReference("solrTemplate", "solrTemplate");
    builder.addPropertyReference("mappingContext", "solrMappingContext");
  }

  @Override
  public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {
    var attributes = config.getAttributes();
    builder.addPropertyReference("solrTemplate", attributes.getString("solrTemplateRef"));
    builder.addPropertyReference("mappingContext", "solrMappingContext");
  }
}
