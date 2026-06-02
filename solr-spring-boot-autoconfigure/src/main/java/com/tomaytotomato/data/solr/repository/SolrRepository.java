package com.tomaytotomato.data.solr.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * Base repository interface for Apache Solr documents.
 *
 * <p>Combines {@link PagingAndSortingRepository} and {@link CrudRepository} for the common case
 * where a single interface provides full CRUD and paginated access to a Solr collection.
 *
 * <p>The ID type is fixed as {@link String} because SolrJ represents document identifiers as
 * strings. There is no runtime mechanism in Solr that supports typed IDs — all document IDs are
 * stored and retrieved as strings. Attempting to use a non-String ID type would previously compile
 * but produce an unchecked cast and incorrect runtime behaviour.
 *
 * <p>Declare a concrete sub-interface for each domain type and annotate the domain class with
 * {@link com.tomaytotomato.data.solr.mapping.SolrEntity} to specify the target collection:
 * <pre>{@code
 * public interface BookRepository extends SolrRepository<Book> {}
 * }</pre>
 *
 * @param <T> the domain type managed by this repository
 * @since 0.1.0
 */
@NoRepositoryBean
public interface SolrRepository<T> extends PagingAndSortingRepository<T, String>, CrudRepository<T, String> {
}
