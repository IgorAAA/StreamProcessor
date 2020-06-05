package org.ia.sp.kafka

import fs2.kafka.CommittableOffset

final case class KafkaEvent[F[_], P](payload: P, committableOffset: CommittableOffset[F])
