package com.tomaytotomato.data.solr.testfixtures;

import com.tomaytotomato.data.solr.mapping.SolrEntity;
import org.apache.solr.client.solrj.beans.Field;

@SolrEntity(collection = "test")
public class TestSolrDocument {

  @Field("id")
  public String id;
}
