package skuber.catseffect.internal

import cats.effect.Async
import fs2.Stream
import play.api.libs.json.{Reads, Writes}

private[catseffect] object PlayJsonBridge:

  export skuber.internal.PlayJsonBridge.{encode, decode}

  def decodeOrThrow[A](bytes: Array[Byte])(using reads: Reads[A]): A =
    decode[A](bytes) match
      case Right(a)    => a
      case Left(error) => throw new RuntimeException(error)

  def parseJsonLines[F[_]: Async, A](stream: Stream[F, Byte])(using reads: Reads[A]): Stream[F, Either[String, A]] =
    stream
      .through(fs2.text.utf8.decode)
      .through(fs2.text.lines)
      .filter(_.nonEmpty)
      .map(line => decode[A](line.getBytes("UTF-8")))
