package org.ia.sp.kafka

import fs2.Stream

trait Consumer[F[_], G[_], A] {
  def consume: Stream[F, KafkaEvent[F, G[A]]]
}
