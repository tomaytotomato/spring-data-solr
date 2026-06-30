package com.tomaytotomato.data.solr;

import com.tomaytotomato.data.solr.query.Criteria;
import com.tomaytotomato.data.solr.query.SimpleQuery;
import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.SolrContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code spring.data.solr.cloud} auto-configuration path,
 * exercising a real SolrCloud cluster running inside a Testcontainers container.
 *
 * <p>The Solr Docker image supports an embedded ZooKeeper via the {@code -DzkRun} flag.
 * {@link SolrContainer#withZookeeper(boolean)} enables this mode, which exposes both the
 * Solr port (8983) and the embedded ZooKeeper port (9983). The auto-configuration is
 * pointed at the containerised ZK host so that a real {@link CloudSolrClient} is wired.
 *
 * <h2>Coverage vs. issue #22</h2>
 * <ul>
 *   <li>COVERED: {@link CloudSolrClient} bean is wired from {@code spring.data.solr.cloud.*}</li>
 *   <li>COVERED: Collection routing — document indexed and queried via a cloud collection</li>
 *   <li>COVERED: Single-node ZooKeeper host in the connection string</li>
 *   <li>TODO #22: ZooKeeper chroot paths (e.g. {@code zk1:2181/solr}) — requires a custom
 *       ZK container with a chroot pre-created; out of scope for this PR</li>
 *   <li>TODO #22: Multiple ZooKeeper hosts in the connection string — requires composing
 *       multiple ZK containers; out of scope for this PR</li>
 *   <li>TODO #22: Multi-shard collection routing — requires a multi-node Solr cluster;
 *       out of scope for this PR</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("SolrCloud Integration Tests")
class SolrCloudIntegrationTest {

  static final String COLLECTION = "books";

  /**
   * A single Solr 10 node started with an embedded ZooKeeper ({@code -DzkRun}).
   * This gives us a functional SolrCloud cluster with one shard and one replica —
   * enough to verify the full {@link CloudSolrClient} wiring path.
   */
  @Container
  static final SolrContainer solr = new SolrContainer(DockerImageName.parse("solr:10"))
      .withZookeeper(true)
      .withCollection(COLLECTION);

  static {
    // Solr advertises itself in ZK as localhost:8983, so the host ports must match.
    solr.setPortBindings(List.of("8983:8983", "9983:9983"));
  }

  private ApplicationContextRunner cloudContextRunner() {
    var zkHost = solr.getHost() + ":" + solr.getZookeeperPort();
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SolrAutoConfiguration.class))
        .withPropertyValues(
            "spring.data.solr.cloud.zk-host=" + zkHost,
            "spring.data.solr.cloud.default-collection=" + COLLECTION,
            "spring.data.solr.commit-mode=IMMEDIATE"
        );
  }

  static AbstractSolrIntegrationTest.TestBook book(String id, String title, String author, int year) {
    return AbstractSolrIntegrationTest.book(id, title, author, year);
  }

  @Nested
  class ClientWiring {

    @Test
    void cloudSolrClientBeanIsCreatedWhenCloudPropertiesAreSet() {
      cloudContextRunner().run(ctx -> {
        assertThat(ctx).hasNotFailed();
        var client = ctx.getBean(SolrClient.class);
        assertThat(client).isInstanceOf(CloudSolrClient.class);
      });
    }

    @Test
    void solrTemplateBeanIsCreatedAlongsideCloudClient() {
      cloudContextRunner().run(ctx -> {
        assertThat(ctx).hasNotFailed();
        assertThat(ctx).hasSingleBean(SolrTemplate.class);
      });
    }
  }

  @Nested
  class DocumentIndexingAndSearch {

    @Test
    void indexesDocumentAndRetrievesItByIdViaCloudClient() {
      cloudContextRunner().run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.deleteByQuery(COLLECTION, "*:*");

        var picard = book("cloud-1", "Make It So", "Jean-Luc Picard", 2350);
        template.save(COLLECTION, picard);

        var found = template.findById(COLLECTION, "cloud-1", AbstractSolrIntegrationTest.TestBook.class);
        assertThat(found).isPresent();
        assertThat(found.get().title).isEqualTo("Make It So");
        assertThat(found.get().author).isEqualTo("Jean-Luc Picard");
      });
    }

    @Test
    void queriesMultipleDocumentsByFieldViaCloudCollection() {
      cloudContextRunner().run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.deleteByQuery(COLLECTION, "*:*");

        template.saveAll(COLLECTION, List.of(
            book("cloud-2", "Dune", "Frank Herbert", 1965),
            book("cloud-3", "Foundation", "Isaac Asimov", 1951),
            book("cloud-4", "Dune Messiah", "Frank Herbert", 1969)
        ));

        var query = new SimpleQuery(Criteria.where("author_s").is("Frank Herbert"));
        var results = template.queryForPage(COLLECTION, query, AbstractSolrIntegrationTest.TestBook.class);

        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent()).extracting(b -> b.title)
            .containsExactlyInAnyOrder("Dune", "Dune Messiah");
      });
    }

    @Test
    void countReturnsCorrectDocumentCountFromCloudCollection() {
      cloudContextRunner().run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.deleteByQuery(COLLECTION, "*:*");

        template.saveAll(COLLECTION, List.of(
            book("cloud-5", "1984", "George Orwell", 1949),
            book("cloud-6", "Animal Farm", "George Orwell", 1945),
            book("cloud-7", "Brave New World", "Aldous Huxley", 1932)
        ));

        var count = template.count(
            COLLECTION,
            new SimpleQuery(Criteria.where("author_s").is("George Orwell"))
        );

        assertThat(count).isEqualTo(2);
      });
    }
  }

  @Nested
  class CollectionRouting {

    /**
     * Verifies that documents are routed into the cloud collection and are retrievable
     * end-to-end through the ZooKeeper-based {@link CloudSolrClient}. In a real
     * multi-shard setup the client would route the request to the correct shard leader;
     * with a single-shard container cluster this validates the routing code path
     * without requiring multi-node infrastructure.
     */
    @Test
    void documentsAreRoutedToCollectionAndQueryable() {
      cloudContextRunner().run(ctx -> {
        var template = ctx.getBean(SolrTemplate.class);
        template.deleteByQuery(COLLECTION, "*:*");

        template.saveAll(COLLECTION, List.of(
            book("route-1", "Neuromancer", "William Gibson", 1984),
            book("route-2", "Count Zero", "William Gibson", 1986),
            book("route-3", "Mona Lisa Overdrive", "William Gibson", 1988)
        ));

        var query = new SimpleQuery(Criteria.where("author_s").is("William Gibson"));
        var page = template.queryForPage(COLLECTION, query, AbstractSolrIntegrationTest.TestBook.class);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(b -> b.id)
            .containsExactlyInAnyOrder("route-1", "route-2", "route-3");
      });
    }
  }
}
