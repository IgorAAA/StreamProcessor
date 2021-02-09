package org.ia.sp

import pureconfig._
import pureconfig.generic.auto._

object Config {
  final case class HttpServerConf(host: String, port: Int)

  final case class KafkaSource(topic: String, groupId: String, clientId: String)

  final case class Sink(topic: String)

  final case class KafkaProducerConf(bootstrapServers: String, sink: Sink)
  final case class KafkaConsumerConf(bootstrapServers: String, source: KafkaSource)

  final case class KafkaConf(
    consumerA: KafkaConsumerConf,
    consumerB: KafkaConsumerConf,
    producer: KafkaProducerConf
  )

  final case class ApplicationConf(httpServer: HttpServerConf, kafka: KafkaConf)

  object ApplicationConf {
    def apply(): ApplicationConf = ConfigSource.default.loadOrThrow[ApplicationConf]
  }
}
