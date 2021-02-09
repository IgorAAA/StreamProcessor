package org.ia.sp.http

import cats.effect.{Sync, Timer}
import cats.syntax.apply._
import cats.syntax.functor._
import io.chrisdavenport.epimetheus._
import io.chrisdavenport.epimetheus.implicits._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

class HealthCheckRoutes[F[_] : Sync : Timer](counter: F[Unit]) extends Http4sDsl[F] {
  def healthCheck: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root => counter *> Ok("Ok")
  }
}

object HealthCheckRoutes {
  def of[F[_] : Sync : Timer](registry: CollectorRegistry[F]): F[HealthCheckRoutes[F]] =
    for {
      counter <- Counter.noLabels(
        registry,
        Name("health_check_received"),
        "Count of received health check requests"
      )
    } yield new HealthCheckRoutes(counter.inc)
}
