FROM solr:10.0

ENV SOLR_HEAP=256m
ENV SOLR_LOG_LEVEL=WARN

EXPOSE 8983

USER root
# Railway runs containers as root; patch solr-fg so solr start accepts it via --force
RUN printf '#!/bin/bash\nexec /opt/solr/bin/solr start -f --force "$@"\n' \
    > /opt/solr/docker/scripts/solr-fg && \
    chmod +x /opt/solr/docker/scripts/solr-fg
COPY --chown=solr:solr managed-schema.xml /opt/solr/server/solr/configsets/_default/conf/managed-schema.xml
USER solr

CMD ["solr-precreate", "books"]
