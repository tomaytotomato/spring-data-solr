FROM solr:10.0

ENV SOLR_HEAP=256m
ENV SOLR_LOG_LEVEL=WARN

EXPOSE 8983

COPY managed-schema.xml /opt/solr/server/solr/configsets/_default/conf/managed-schema.xml
CMD ["solr-precreate", "books"]
