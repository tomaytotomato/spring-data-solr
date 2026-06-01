package com.tomaytotomato.data.solr.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Base repository interface for Apache Solr documents.
 *
 * <p>The ID type is fixed as {@link String} because SolrJ represents document identifiers as
 * strings. There is no runtime mechanism in Solr that supports typed IDs — all document IDs are
 * stored and retrieved as strings. Attempting to use a non-String ID type would previously compile
 * but produce an unchecked cast and incorrect runtime behaviour.
 *
 * @param <T> the domain type managed by this repository
 */
@NoRepositoryBean
public interface SolrRepository<T> extends PagingAndSortingRepository<T, String>, CrudRepository<T, String> {
}
