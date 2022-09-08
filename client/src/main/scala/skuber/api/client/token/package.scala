package skuber.api.client

import scala.io.Source
import scala.util.Try

package object token {
  trait ContentReaderComponent {
    val contentReader: ContentReader

    trait ContentReader {
      def read(filePath: String): Try[String]
    }
  }

  trait FileReaderComponent extends ContentReaderComponent {
    val contentReader: ContentReader = new FileContentReader

    class FileContentReader extends ContentReader {
      def read(filePath: String): Try[String] = for {
        source <- Try(Source.fromFile(filePath, "utf-8"))
        content  <- Try(source.getLines().mkString("\n"))
        _      <- Try(source.close())
      } yield content
    }
  }
}
