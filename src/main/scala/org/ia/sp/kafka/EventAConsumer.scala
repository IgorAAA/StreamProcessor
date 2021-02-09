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
import org.ia.sp.domain.{
  DataType,
  EventA,
  Output,
  OutputEvent,
  OutputEventA,
  TypeAInStock,
  TypeAOutOfStock
}
import org.ia.sp.kafka.KafkaReportConsumer.Metrics
import shapeless.Sized

class EventAConsumer[F[_] : ConcurrentEffect : ContextShift : Timer : Logger](
  source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
  config: KafkaConsumerConf,
  metrics: Metrics[F]
) extends KafkaConsumer[F, Output, EventA, OutputEvent, Array[Byte], Array[Byte]](
      source,
      config,
      metrics
    ) {

  override def toOutputEvent
    : KafkaEvent[F, EventA] => F[Option[KafkaEvent[F, Output[OutputEvent]]]] =
    ke => eventToOutput(ke.payload).map(out => ke.copy(payload = out).some)

  private def eventToOutput(event: EventA): F[Output[OutputEvent]] =
    f.tupled(eventToOutputAndDataType(event))

  private def eventToOutputAndDataType: EventA => (OutputEventA, DataType) = {
    case event @ EventA(_, _, inStock, _) if inStock => (eventProc(event), TypeAInStock)
    case event: EventA                               => (eventProc(event), TypeAOutOfStock)
  }

  def eventProc: EventA => OutputEventA = _.into[OutputEventA].transform

  private def f: (OutputEvent, DataType) => F[Output[OutputEvent]] =
    (event, dataType) =>
      JavaTime[F].getInstant
        .map(time => toOutput(time, event, dataType))
}

object EventAConsumer {
  def of[F[_] : ConcurrentEffect : ContextShift : Timer](
    source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
    config: KafkaConsumerConf,
    registry: CollectorRegistry[F]
  ): Resource[F, EventAConsumer[F]] = Resource.liftF(create(source, config, registry))

  private def create[F[_] : ConcurrentEffect : ContextShift : Timer](
    source: Stream[F, CommittableConsumerRecord[F, Array[Byte], Array[Byte]]],
    config: KafkaConsumerConf,
    registry: CollectorRegistry[F]
  ): F[EventAConsumer[F]] = Slf4jLogger.create[F].flatMap { implicit logger =>
    val inputCounter = Counter.labelled(
      registry,
      Name("typeA_consumer_input_events_total"),
      "Count of messages processed",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    val errorCounter = Counter.labelled(
      registry,
      Name("typeA_consumer_input_errors_total"),
      "Count of messages ignored",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    val latencyHist = Histogram.labelled(
      registry,
      Name("typeA_latency_seconds"),
      "Latency of input events processing",
      Sized(Label("topic")),
      (label: String) => Sized(label)
    )

    for {
      inCounter  <- inputCounter
      errCounter <- errorCounter
      lHist      <- latencyHist
    } yield new EventAConsumer[F](
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
