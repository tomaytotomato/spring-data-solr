package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrProperties;
import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrDocumentResolver;
import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

public class SolrRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
    extends RepositoryFactoryBeanSupport<T, S, ID> {

  private SolrTemplate solrTemplate;
  private SolrMappingContext mappingContext;
  private SolrDocumentResolver documentResolver;
  private int defaultPageSize = 10;

  protected SolrRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
    super(repositoryInterface);
  }

  public void setSolrTemplate(SolrTemplate solrTemplate) {
    this.solrTemplate = solrTemplate;
  }

  public void setMappingContext(SolrMappingContext mappingContext) {
    this.mappingContext = mappingContext;
  }

  /**
   * Injects the {@link SolrDocumentResolver} bean so that {@code ${placeholder}} values in
   * {@link com.tomaytotomato.data.solr.mapping.SolrEntity#collection()} are expanded against
   * the Spring {@link org.springframework.core.env.Environment} throughout the repository layer.
   * Wired automatically by {@link SolrRepositoryConfigurationExtension}.
   */
  public void setDocumentResolver(SolrDocumentResolver documentResolver) {
    this.documentResolver = documentResolver;
  }

  /**
   * Injects {@link SolrProperties} to read the configured default page size.
   * Wired automatically by {@link SolrRepositoryConfigurationExtension}.
   */
  public void setSolrProperties(SolrProperties solrProperties) {
    if (solrProperties != null) {
      this.defaultPageSize = solrProperties.getDefaultPageSize();
    }
  }

  @Override
  protected RepositoryFactorySupport createRepositoryFactory() {
    return new SolrRepositoryFactory(solrTemplate, mappingContext, defaultPageSize,
        documentResolver);
  }
}
