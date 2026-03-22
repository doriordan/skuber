package skuber.internal

import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes}

object PlayJsonBridge {

  def encode[A](value: A)(implicit writes: Writes[A]): Array[Byte] =
    Json.toBytes(Json.toJson(value))

  def decode[A](bytes: Array[Byte])(implicit reads: Reads[A]): Either[String, A] =
    try {
      val json = Json.parse(bytes)
      json.validate[A] match {
        case JsSuccess(a, _) => Right(a)
        case JsError(errors) =>
          val message = errors
            .flatMap { case (path, validationErrors) =>
              validationErrors.map(e => s"$path: ${e.message}")
            }
            .mkString("; ")
          Left(s"JSON decode error: $message")
      }
    } catch {
      case e: Exception =>
        Left(s"JSON decode error: ${e.getMessage}")
    }
}
