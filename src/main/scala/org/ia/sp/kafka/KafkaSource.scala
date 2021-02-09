package org.ia.sp.kafka

import cats.effect.{ConcurrentEffect, ContextShift, Timer}
import fs2._
import fs2.kafka.{
  AutoOffsetReset,
  CommittableConsumerRecord,
  ConsumerSettings,
  RecordDeserializer,
  consumerStream
}
import org.ia.sp.Config.KafkaConsumerConf

class KafkaSource[
  F[_] : ConcurrentEffect : ContextShift : Timer, K : RecordDeserializer[F, *], V : RecordDeserializer[F,*]
](config: KafkaConsumerConf) {
  private def consumerSettings =
    ConsumerSettings[F, K, V]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(config.bootstrapServers)
      .withClientId(config.source.clientId)
      .withGroupId(config.source.groupId)

  def source: Stream[F, CommittableConsumerRecord[F, K, V]] =
    consumerStream(consumerSettings)
      .evalTap(_.subscribeTo(config.source.topic))
      .flatMap(_.stream)
}
