The database for this project uses Redis and Elasticsearch for caching. Redis is set up with one master and two slaves, along with a sentinel system. The middleware Canal is also incorporated. The logs are configured with ELK and Kafka.The database will be added by yourselves.
Below is the version that I personally used for my setup:
kafka_2.12-2.8.1
elasticsearch-7.17.25
filebeat-7.14.0-windows-x86_64
kibana-7.14.2-windows-x86_64
logstash-7.17.25
canal and redis are not limited to specific versions
