FROM solr:10.0.0

ENV SOLR_HEAP=512m
ENV SOLR_LOG_LEVEL=WARN

EXPOSE 8983
USER root

# Create a bookstore configset based on _default, then apply the custom schema
RUN cp -r /opt/solr/server/solr/configsets/_default /opt/solr/server/solr/configsets/bookstore && \
    chown -R solr:solr /opt/solr/server/solr/configsets/bookstore
COPY --chown=solr:solr managed-schema.xml /opt/solr/server/solr/configsets/bookstore/conf/managed-schema.xml

COPY --chown=solr:solr --chmod=+x start-bookstore.sh /opt/solr/docker/scripts

CMD ["start-bookstore.sh"]
