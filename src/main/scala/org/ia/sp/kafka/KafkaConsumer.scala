package org.ia.sp.kafka

import cats.Applicative
import cats.effect.{Clock, ConcurrentEffect, ContextShift, Sync, Timer}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import fs2.{Pipe, Stream}
import fs2.kafka.{
  AutoOffsetReset,
  CommittableConsumerRecord,
  ConsumerSettings,
  Timestamp,
  consumerStream
}
import io.chrisdavenport.log4cats.Logger
import io.circe.Decoder
import io.circe.parser.decode
import org.ia.sp.Config.KafkaConsumerConf
import org.ia.sp.kafka.KafkaReportConsumer.Metrics

import scala.concurrent.duration.{Duration, MILLISECONDS}

abstract class KafkaConsumer[F[_] : ConcurrentEffect : ContextShift : Timer, G[_], A : Decoder, B, K, V](
  source: Stream[F, CommittableConsumerRecord[F, K, V]],
  config: KafkaConsumerConf,
  metrics: Metrics[F]
)(implicit logger: Logger[F])
    extends Consumer[F, G, B] {
  override def consume: Stream[F, KafkaEvent[F, G[B]]] =
    consumeKafkaEvent.evalMap(toOutputEvent).collect { case Some(evt) => evt }

  private val consumerSettings = ConsumerSettings[F, String, String]
    .withAutoOffsetReset(AutoOffsetReset.Earliest)
    .withBootstrapServers(config.bootstrapServers)
    .withClientId(config.source.clientId)
    .withGroupId(config.source.groupId)

  protected def consumeKafkaEvent: Stream[F, KafkaEvent[F, A]] =
    consumerStream(consumerSettings)
      .evalTap(_.subscribeTo(config.source.topic))
      .flatMap(_.stream)
      .evalTap(msg => meteredLatency(config.source.topic, msg.record.timestamp))
      .mapAsync(25) { commitable =>
        for {
          res <- decode[A](commitable.record.value) match {
            case Right(a) =>
              logger.debug(s"Kafka event $a is consumed") *> metrics.input(config.source.topic) *>
                Applicative[F].pure(KafkaEvent(a, commitable.offset).some)
            case Left(err) =>
              logger.error(s"Error when decoding kafka event $err") *> Applicative[F].pure(
                none[KafkaEvent[F, A]]
              )
          }

        } yield res
      }
      .collect { case Some(ke) => ke }

  private def meteredLatency(topic: String, timestamp: Timestamp): F[Unit] =
    timestamp.createTime.fold(Applicative[F].unit) { timestamp =>
      for {
        now <- Clock[F].realTime(MILLISECONDS)
        latency = Duration(Math.max(0L, now - timestamp), MILLISECONDS)
        meter <- metrics.latency(topic, latency)
      } yield meter
    }

  def toOutputEvent: KafkaEvent[F, A] => F[Option[KafkaEvent[F, G[B]]]]
}

object KafkaReportConsumer {
  private[kafka] final case class Metrics[F[_]](
    input: String => F[Unit],
    error: String => F[Unit],
    latency: (String, Duration) => F[Unit]
  )
}
