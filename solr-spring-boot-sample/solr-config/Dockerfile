FROM solr:10.0
COPY managed-schema.xml /opt/solr/server/solr/configsets/_default/conf/managed-schema.xml
CMD ["solr-precreate", "books"]
