package org.ia.sp.http

import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.syntax.functor._
import io.chrisdavenport.epimetheus.CollectorRegistry
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.server.{Router, Server}
import org.ia.sp.Config.HttpServerConf

class HttpServer[F[_] : ConcurrentEffect : Timer: ContextShift](routes: HttpRoutes[F])(conf: HttpServerConf) {
  val start: Resource[F, Server[F]] =
    for {
      server <- BlazeServerBuilder[F]
        .bindHttp(host = conf.host, port = conf.port)
        .withHttpApp(routes.orNotFound)
        .resource
    } yield server

}

object HttpServer {
  def of[F[_] : ConcurrentEffect : Timer : ContextShift](
    registry: CollectorRegistry[F],
    conf: HttpServerConf
  ): F[HttpServer[F]] =
    for {
      healthCheckRoutes <- HealthCheckRoutes.of[F](registry)
    } yield {
      val routes = Router(
        "/health/check" -> healthCheckRoutes.healthCheck,
        "/metrics"      -> MetricRoutes[F](registry).getMetrics
      )

      val logRoutes = Logger.httpRoutes(logHeaders = false, logBody = false)(routes)

      new HttpServer[F](logRoutes)(conf)
    }
}
