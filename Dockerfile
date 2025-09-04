FROM openjdk:17-jdk-slim

ARG KAFKA_VERSION=3.6.0
ARG SCALA_VERSION=2.13

ENV KAFKA_HOME=/opt/kafka
ENV PATH="$PATH:$KAFKA_HOME/bin"

RUN set -eux; \
    apt-get update && apt-get install -y --no-install-recommends curl ca-certificates tzdata && rm -rf /var/lib/apt/lists/*; \
    curl -fSLk "https://archive.apache.org/dist/kafka/${KAFKA_VERSION}/kafka_${SCALA_VERSION}-${KAFKA_VERSION}.tgz" -o /tmp/kafka.tgz; \
    mkdir -p "$KAFKA_HOME"; \
    tar -xzf /tmp/kafka.tgz --strip-components=1 -C "$KAFKA_HOME"; \
    rm -f /tmp/kafka.tgz; \
    mkdir -p /var/lib/kafka/data;

# Default mount points
VOLUME ["/var/lib/kafka/data"]

WORKDIR $KAFKA_HOME

COPY scripts/start-kafka.sh /start-kafka.sh
RUN chmod +x /start-kafka.sh

ENTRYPOINT ["/start-kafka.sh"]

