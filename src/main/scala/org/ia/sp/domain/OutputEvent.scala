package org.ia.sp.domain

import java.time.Instant

import io.circe.Encoder
import io.circe.syntax._

final case class Output[P](dataType: DataType, created: Instant, payload: P)

trait DataType
case object TypeAInStock    extends DataType
case object TypeAOutOfStock extends DataType
case object TypeB           extends DataType

trait OutputEvent
final case class OutputEventA(id: Long, msg: String, numOfItems: Int) extends OutputEvent
final case class OutputEventB(id: Long, msg: String)                  extends OutputEvent

object OutputEvent {
  implicit val encA: Encoder[Output[OutputEventA]] = Encoder.instance(_.asJson)
  implicit val encB: Encoder[Output[OutputEventB]] = Encoder.instance(_.asJson)
}
