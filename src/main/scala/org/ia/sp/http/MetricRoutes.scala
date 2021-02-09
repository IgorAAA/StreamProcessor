package org.ia.sp.http

import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.epimetheus.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`

import scala.language.higherKinds

class MetricRoutes[F[_] : Sync](registry: CollectorRegistry[F]) extends Http4sDsl[F] {
  private val contentType = Sync[F].fromEither(`Content-Type`.parse(TextFormat.CONTENT_TYPE_004))

  def getMetrics: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root =>
      for {
        cType    <- contentType
        response <- Ok(registry.write004)
      } yield response.withContentType(cType)
  }
}

object MetricRoutes {
  def apply[F[_] : Sync](registry: CollectorRegistry[F]): MetricRoutes[F] =
    new MetricRoutes[F](registry)
}
