#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE=${CONFIG_FILE:-/opt/kafka/config/server.properties}
DATA_DIR=${DATA_DIR:-/var/lib/kafka/data}
KAFKA_BIN=/opt/kafka/bin

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Config $CONFIG_FILE not found!" >&2
  exit 1
fi

if [[ ! -f "$DATA_DIR/meta.properties" ]]; then
  echo "No metadata found in $DATA_DIR. Formatting storage..."
  CLUSTER_ID=${CLUSTER_ID:-$($KAFKA_BIN/kafka-storage.sh random-uuid)}
  echo "Using cluster id: $CLUSTER_ID"
  $KAFKA_BIN/kafka-storage.sh format -t "$CLUSTER_ID" -c "$CONFIG_FILE"
fi

exec $KAFKA_BIN/kafka-server-start.sh "$CONFIG_FILE"

