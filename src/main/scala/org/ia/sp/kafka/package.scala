package org.ia.sp

import java.time.Instant

import org.ia.sp.domain.{DataType, Output, OutputEvent}

package object kafka {
  def toOutput(timeStamp: Instant, event: OutputEvent, dataType: DataType): Output[OutputEvent] =
    Output(dataType = dataType, created = timeStamp, payload = event)
}
