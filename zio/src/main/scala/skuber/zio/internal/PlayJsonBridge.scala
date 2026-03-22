package skuber.zio.internal

import zio.stream.*
import play.api.libs.json.Reads
import skuber.internal.{PlayJsonBridge => CoreBridge}

private[zio] object PlayJsonBridge:

  export CoreBridge.{encode, decode}

  def parseJsonLines[A](stream: ZStream[Any, Throwable, Byte])(using reads: Reads[A]): ZStream[Any, Nothing, Either[String, A]] =
    stream
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitLines)
      .filter(_.nonEmpty)
      .map(line => decode[A](line.getBytes("UTF-8")))
      .orDie   // byte-level stream errors become defects; callers handle A-level errors via Either
