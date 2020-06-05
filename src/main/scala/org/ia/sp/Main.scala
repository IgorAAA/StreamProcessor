package org.ia.sp

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    IO(println("Hello world")).as(ExitCode.Success)
}
