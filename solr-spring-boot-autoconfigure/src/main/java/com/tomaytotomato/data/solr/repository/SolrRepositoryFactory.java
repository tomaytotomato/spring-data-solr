package com.tomaytotomato.data.solr.repository;

import com.tomaytotomato.data.solr.SolrTemplate;
import com.tomaytotomato.data.solr.mapping.SolrMappingContext;
import java.util.Optional;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.ValueExpressionDelegate;

public class SolrRepositoryFactory extends RepositoryFactorySupport {

  private final SolrTemplate solrTemplate;
  private final SolrMappingContext mappingContext;
  private final int defaultPageSize;

  public SolrRepositoryFactory(SolrTemplate solrTemplate, SolrMappingContext mappingContext) {
    this(solrTemplate, mappingContext, 10);
  }

  public SolrRepositoryFactory(SolrTemplate solrTemplate, SolrMappingContext mappingContext, int defaultPageSize) {
    this.solrTemplate = solrTemplate;
    this.mappingContext = mappingContext;
    this.defaultPageSize = defaultPageSize;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T, ID> EntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
    return (EntityInformation<T, ID>) new SolrEntityInformation<>(domainClass);
  }

  @Override
  protected Object getTargetRepository(RepositoryInformation information) {
    return getTargetRepositoryViaReflection(information, solrTemplate, information.getDomainType());
  }

  @Override
  protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
    return SimpleSolrRepository.class;
  }

  @Override
  protected Optional<QueryLookupStrategy> getQueryLookupStrategy(Key key,
      ValueExpressionDelegate delegate) {
    return Optional.of(new SolrQueryLookupStrategy(solrTemplate, defaultPageSize));
  }
}
