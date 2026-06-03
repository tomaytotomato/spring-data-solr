#!/bin/bash
set -e

printf "Starting the Solr Bookstore sample 📚\n"

# Pre-create the books core using the bookstore configset (idempotent)
/opt/solr/docker/scripts/precreate-core books /opt/solr/server/solr/configsets/bookstore

# Start Solr in foreground, standalone mode (--user-managed), allowing root (--force)
exec /opt/solr/bin/solr start -f --force --user-managed
