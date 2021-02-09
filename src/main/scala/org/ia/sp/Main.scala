package org.ia.sp

import cats.NonEmptyParallel
import cats.effect._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.epimetheus._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import Config.{ApplicationConf, KafkaSource}
import org.ia.sp.http.HttpServer
import org.ia.sp.kafka.{EventAConsumer, EventBConsumer, Producer}

object Main extends IOApp {

  def program[F[_] : ConcurrentEffect : Timer : ContextShift : NonEmptyParallel]: F[Unit] =
    Slf4jLogger.create[F].flatMap { logger =>
      def initialResources: F[(ApplicationConf, HttpServer[F], CollectorRegistry[F])] =
        for {
          conf       <- Sync[F].delay(ApplicationConf())
          _          <- logger.info(s"Config is loaded. Starting application..")
          metricsReg <- CollectorRegistry.buildWithDefaults[F]
          server     <- HttpServer.of[F](metricsReg, conf.httpServer)
        } yield (conf, server, metricsReg)

      val resources = for {
        r <- Resource.liftF(initialResources)
        (conf, server, metricReg) = r
        sourceA = new kafka.KafkaSource[F, Array[Byte], Array[Byte]](conf.kafka.consumerA)
        consumerA <- EventAConsumer.of(sourceA.source, conf.kafka.consumerA, metricReg)
        sourceB = new kafka.KafkaSource[F, Array[Byte], Array[Byte]](conf.kafka.consumerB)
        consumerB <- EventBConsumer.of(sourceB.source, conf.kafka.consumerB, metricReg)
        groupIds = List(conf.kafka.consumerA.source.groupId, conf.kafka.consumerB.source.groupId)
        producer  <- Producer.of[F](conf.kafka.producer, groupIds, metricReg)
        _         <- server.start
      } yield (consumerA, consumerB, producer)

      resources
        .use {
          case (consumerA, consumerB, producer) =>
            consumerA.consume
              .merge(consumerB.consume)
              .through(producer.produceKafkaEvent)
              .compile
              .drain
        }
        .handleErrorWith(err => logger.error(err)(s"Failed to start the microservice"))
    }

  override def run(args: List[String]): IO[ExitCode] =
    program[IO].as(ExitCode.Success)
}
