package org.ia.sp.domain

trait InputEvent
final case class EventA(id: Long, msg: String, inStock: Boolean, numOfItems: Int) extends InputEvent
final case class EventB(id: Long, msg: String)                                    extends InputEvent
