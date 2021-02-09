package org.ia.sp.kafka

import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import io.circe.generic.auto._
import io.chrisdavenport.cats.effect.time.JavaTime
import io.chrisdavenport.epimetheus.{CollectorRegistry, Counter, Histogram, Label, Name}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.scalaland.chimney.dsl._
import org.ia.sp.Config.KafkaConsumerConf
import org.ia.sp.domain.{EventB, Output, OutputEvent, OutputEventB, TypeB}
import org.ia.sp.kafka.KafkaReportConsumer.Metrics
import shapeless.Sized

class EventBConsumer[F[_] : ConcurrentEffect : ContextShift : Timer : Logger](
  source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
  config: KafkaConsumerConf,
  metrics: Metrics[F]
) extends KafkaConsumer[F, Output, EventB, OutputEvent, Array[Byte], Array[Byte]](
      source,
      config,
      metrics
    ) {

  override def toOutputEvent
    : KafkaEvent[F, EventB] => F[Option[KafkaEvent[F, Output[OutputEvent]]]] =
    ke => eventToOutput(ke.payload).map(out => ke.copy(payload = out).some)

  private def eventToOutput(event: EventB): F[Output[OutputEvent]] =
    f(eventProc(event))

  def eventProc: EventB => OutputEventB = _.into[OutputEventB].transform

  private def f: OutputEvent => F[Output[OutputEvent]] =
    event =>
      JavaTime[F].getInstant
        .map(time => toOutput(time, event, TypeB))
}

object EventBConsumer {
  def of[F[_] : ConcurrentEffect : ContextShift : Timer](
    source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
    config: KafkaConsumerConf,
    registry: CollectorRegistry[F]
  ): Resource[F, EventBConsumer[F]] = Resource.liftF(create(source, config, registry))

  private def create[F[_] : ConcurrentEffect : ContextShift : Timer](
    source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
    config: KafkaConsumerConf,
    registry: CollectorRegistry[F]
  ): F[EventBConsumer[F]] = Slf4jLogger.create[F].flatMap { implicit logger =>
    val inputCounter = Counter.labelled(
      registry,
      Name("typeB_consumer_input_events_total"),
      "Count of messages processed",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    val errorCounter = Counter.labelled(
      registry,
      Name("typeB_consumer_input_errors_total"),
      "Count of messages ignored",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    val latencyHist = Histogram.labelled(
      registry,
      Name("typeB_latency_seconds"),
      "Latency of input events processing",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    for {
      inCounter  <- inputCounter
      errCounter <- errorCounter
      lHist      <- latencyHist
    } yield new EventBConsumer[F](
      source,
      config,
      Metrics[F](
        inCounter.label(_).inc,
        errCounter.label(_).inc,
        (topic, latency) => lHist.label(topic).observe(latency.toMillis.doubleValue / 1000)
      )
    )
  }
}
