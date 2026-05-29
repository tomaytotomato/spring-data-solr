package com.tomaytotomato.data.solr.health;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolrHealthIndicatorTest {

  private static final String COLLECTION = "test-collection";

  @Mock
  private SolrClient solrClient;

  @Nested
  class WithCollection {

    @Mock
    private SolrPingResponse pingResponse;

    @Test
    void reportsUpWhenPingStatusIsZero() throws Exception {
      when(pingResponse.getStatus()).thenReturn(0);
      when(pingResponse.getElapsedTime()).thenReturn(42L);
      when(solrClient.ping(COLLECTION)).thenReturn(pingResponse);

      var indicator = new SolrHealthIndicator(solrClient, COLLECTION);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails()).containsEntry("collection", COLLECTION);
      assertThat(health.getDetails()).containsEntry("status", 0);
      assertThat(health.getDetails()).containsEntry("elapsed_time_ms", 42L);
    }

    @Test
    void reportsDownWhenPingStatusIsNonZero() throws Exception {
      when(pingResponse.getStatus()).thenReturn(500);
      when(solrClient.ping(COLLECTION)).thenReturn(pingResponse);

      var indicator = new SolrHealthIndicator(solrClient, COLLECTION);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsEntry("status", 500);
    }

    @Test
    void reportsDownWhenPingThrowsIOException() throws Exception {
      when(solrClient.ping(COLLECTION)).thenThrow(new IOException("connection refused"));

      var indicator = new SolrHealthIndicator(solrClient, COLLECTION);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    void reportsDownWhenPingThrowsSolrServerException() throws Exception {
      when(solrClient.ping(COLLECTION)).thenThrow(new SolrServerException("ping failed"));

      var indicator = new SolrHealthIndicator(solrClient, COLLECTION);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
  }

  @Nested
  class WithoutCollection {

    @Test
    void reportsUpWhenAdminEndpointReturnsValidResponse() throws Exception {
      var luceneInfo = new NamedList<Object>();
      luceneInfo.add("solr-spec-version", "10.0.0");
      var adminResponse = new NamedList<Object>();
      adminResponse.add("lucene", luceneInfo);

      when(solrClient.request(any(SolrRequest.class), isNull())).thenReturn(adminResponse);

      var indicator = new SolrHealthIndicator(solrClient, null);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails()).containsEntry("solr-version", "10.0.0");
    }

    @Test
    void reportsDownWhenAdminEndpointThrowsIOException() throws Exception {
      when(solrClient.request(any(SolrRequest.class), isNull()))
          .thenThrow(new IOException("connection refused"));

      var indicator = new SolrHealthIndicator(solrClient, null);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.DOWN);
      assertThat(health.getDetails()).containsKey("error");
    }
  }
}
