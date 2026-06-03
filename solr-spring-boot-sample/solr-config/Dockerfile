FROM solr:10.0.0

ENV SOLR_HEAP=512m
ENV SOLR_LOG_LEVEL=WARN

EXPOSE 8983

USER solr

COPY --chown=solr:solr managed-schema.xml /opt/solr/server/solr/configsets/_default/conf/managed-schema.xml

CMD ["solr-precreate", "books"]
