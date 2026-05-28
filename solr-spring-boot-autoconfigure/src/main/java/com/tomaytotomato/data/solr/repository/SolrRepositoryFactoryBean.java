package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

public class SolrRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
    extends RepositoryFactoryBeanSupport<T, S, ID> {

  private SolrTemplate solrTemplate;
  private SolrMappingContext mappingContext;

  protected SolrRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
    super(repositoryInterface);
  }

  public void setSolrTemplate(SolrTemplate solrTemplate) {
    this.solrTemplate = solrTemplate;
  }

  public void setMappingContext(SolrMappingContext mappingContext) {
    this.mappingContext = mappingContext;
  }

  @Override
  protected RepositoryFactorySupport createRepositoryFactory() {
    return new SolrRepositoryFactory(solrTemplate, mappingContext);
  }
}
