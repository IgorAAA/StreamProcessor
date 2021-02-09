package org.ia.sp.kafka

import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.circe.syntax._
import fs2.{Pipe, Stream}
import fs2.kafka._
import io.chrisdavenport.epimetheus.{CollectorRegistry, Counter, Label, Name}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.ia.sp.Config.KafkaProducerConf
import org.ia.sp.domain.{Output, OutputEvent, OutputEventA, OutputEventB}
import org.ia.sp.kafka.Producer.ProducerMetrics
import shapeless.Sized

import scala.concurrent.duration._

class Producer[F[_] : ConcurrentEffect : ContextShift : Timer](
  config: KafkaProducerConf,
  metrics: ProducerMetrics[F],
  finalizing: Pipe[F, CommittableOffset[F], Unit]
)(implicit logger: Logger[F]) {
  import Producer._

  private val producerSettings =
    ProducerSettings[F, String, String]
      .withBootstrapServers(config.bootstrapServers)
      .withParallelism(100)
      .withCloseTimeout(60.seconds)

  val produceKafkaEvent: Pipe[F, KafkaEvent[F, Output[OutputEvent]], Unit] =
    _.evalTap(ke => logger.debug(s"Producing kafka event: $ke"))
      .map { ke =>
        val payload = serialize(ke.payload)
        val record  = ProducerRecord(config.sink.topic, "", payload)
        ProducerRecords.one(record, ke.commitableOffset)
      }
      .through(produce(producerSettings))
      .map(_.passthrough)
      .evalTap(_ => metrics.output(config.sink.topic))
      .through(finalizing)

}

object Producer {
  final case class ProducerMetrics[F[_]](output: String => F[Unit])

  def serialize[A <: OutputEvent](event: Output[A]): String = event.payload match {
    case _: OutputEventA =>
      event.asInstanceOf[Output[OutputEventA]].asJson.noSpaces
    case _: OutputEventB =>
      event.asInstanceOf[Output[OutputEventB]].asJson.noSpaces
  }

  def of[F[_] : ConcurrentEffect : ContextShift : Timer](
    config: KafkaProducerConf,
    groupIds: List[String],
    registry: CollectorRegistry[F]
  ): Resource[F, Producer[F]] = Resource.liftF(create(config, groupIds, registry))

  private def create[F[_] : ConcurrentEffect : ContextShift : Timer](
    config: KafkaProducerConf,
    groupIds: List[String],
    registry: CollectorRegistry[F]
  ): F[Producer[F]] = Slf4jLogger.create[F].flatMap { implicit logger =>
    def commit4Groups(gIds: List[String]): Pipe[F, CommittableOffset[F], Unit] =
      str => {
        val commits: List[Pipe[F, CommittableOffset[F], Unit]] = gIds.map(
          gId =>
            (st: Stream[F, CommittableOffset[F]]) =>
              st.collect { case cOffset if cOffset.consumerGroupId.contains(gId) => cOffset }
                .through(commitBatchWithin(1000, 15.seconds))
        )

        str.broadcastTo[F](commits: _*)
      }

    val outputCounter = Counter.labelled(
      registry,
      Name("producer_output_events_total"),
      "Count of events produced",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    for {
      outCounter <- outputCounter
    } yield new Producer[F](config, ProducerMetrics(outCounter.label(_).inc), commit4Groups(groupIds))
  }
}
