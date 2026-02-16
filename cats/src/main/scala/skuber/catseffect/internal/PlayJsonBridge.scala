package skuber.catseffect.internal

import cats.effect.Async
import fs2.Stream
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes}

private[catseffect] object PlayJsonBridge:

  def encode[A](value: A)(using writes: Writes[A]): Array[Byte] =
    Json.toBytes(Json.toJson(value))

  def decode[A](bytes: Array[Byte])(using reads: Reads[A]): Either[String, A] =
    try
      val json = Json.parse(bytes)
      json.validate[A] match
        case JsSuccess(a, _) => Right(a)
        case JsError(errors) =>
          val message = errors
            .flatMap { case (path, validationErrors) =>
              validationErrors.map(e => s"$path: ${e.message}")
            }
            .mkString("; ")
          Left(s"JSON decode error: $message")
    catch
      case e: Exception =>
        Left(s"JSON decode error: ${e.getMessage}")

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
