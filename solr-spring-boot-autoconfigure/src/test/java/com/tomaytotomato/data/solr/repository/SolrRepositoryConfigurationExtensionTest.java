package com.tomaytotomato.data.solr.repository;

import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.data.repository.config.RepositoryConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolrRepositoryConfigurationExtensionTest {

  @Mock
  private RepositoryConfigurationSource source;

  private final SolrRepositoryConfigurationExtension extension = new SolrRepositoryConfigurationExtension();

  @Nested
  class ModuleMetadata {

    @Test
    void returnsCorrectModuleName() {
      assertThat(extension.getModuleName()).isEqualTo("Solr");
    }

    @Test
    void returnsCorrectRepositoryFactoryBeanClassName() {
      assertThat(extension.getRepositoryFactoryBeanClassName())
          .isEqualTo(SolrRepositoryFactoryBean.class.getName());
    }
  }

  @Nested
  class BasePostProcess {

    @Test
    void usesDefaultTemplateRefWhenAttributeAbsent() {
      lenient().when(source.getAttribute("solrTemplateRef")).thenReturn(Optional.empty());

      var builder = BeanDefinitionBuilder.genericBeanDefinition();
      extension.postProcess(builder, source);

      var propertyValues = builder.getBeanDefinition().getPropertyValues();
      var templateRef = (RuntimeBeanReference) propertyValues.getPropertyValue("solrTemplate").getValue();
      assertThat(templateRef.getBeanName()).isEqualTo("solrTemplate");
    }

    @Test
    void usesDefaultTemplateRefWhenAttributeIsBlank() {
      lenient().when(source.getAttribute("solrTemplateRef")).thenReturn(Optional.of("   "));

      var builder = BeanDefinitionBuilder.genericBeanDefinition();
      extension.postProcess(builder, source);

      var propertyValues = builder.getBeanDefinition().getPropertyValues();
      var templateRef = (RuntimeBeanReference) propertyValues.getPropertyValue("solrTemplate").getValue();
      assertThat(templateRef.getBeanName()).isEqualTo("solrTemplate");
    }

    @Test
    void usesCustomTemplateRefWhenAttributeIsPresent() {
      when(source.getAttribute("solrTemplateRef")).thenReturn(Optional.of("myCustomSolrTemplate"));

      var builder = BeanDefinitionBuilder.genericBeanDefinition();
      extension.postProcess(builder, source);

      var propertyValues = builder.getBeanDefinition().getPropertyValues();
      var templateRef = (RuntimeBeanReference) propertyValues.getPropertyValue("solrTemplate").getValue();
      assertThat(templateRef.getBeanName()).isEqualTo("myCustomSolrTemplate");
    }

    @Test
    void alwaysWiresMappingContextRegardlessOfTemplateRef() {
      lenient().when(source.getAttribute("solrTemplateRef")).thenReturn(Optional.empty());

      var builder = BeanDefinitionBuilder.genericBeanDefinition();
      extension.postProcess(builder, source);

      var propertyValues = builder.getBeanDefinition().getPropertyValues();
      var mappingRef = (RuntimeBeanReference) propertyValues.getPropertyValue("mappingContext").getValue();
      assertThat(mappingRef.getBeanName()).isEqualTo("solrMappingContext");
    }
  }
}
