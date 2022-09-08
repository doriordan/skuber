package skuber.api.client.token

import org.joda.time.DateTime
import org.specs2.mutable.Specification

import scala.concurrent.duration.DurationInt
import scala.util.Try

class FileTokenAuthRefreshableSpec extends Specification {
  "This is a specification for the 'FileTokenAuthRefreshable' class".txt

  trait MockFileReaderComponent extends ContentReaderComponent {
    val contentReader: ContentReader = new MockFileReaderComponent

    class MockFileReaderComponent extends ContentReader {
      def read(filePath: String): Try[String] = Try(DateTime.now.toString())
    }
  }

  final case class MockFileTokenAuthRefreshable(config: FileTokenConfiguration) extends TokenAuthRefreshable with MockFileReaderComponent

  "FileTokenAuthRefreshable" should {
    "Retrieve the token if none provided" in {
      val initialToken : Option[String] = None
      val fileTokenRefreshable = MockFileTokenAuthRefreshable(FileTokenConfiguration(cachedAccessToken = initialToken, tokenPath = "/tmp/token", refreshInterval = 100.milliseconds))
      fileTokenRefreshable.accessToken.nonEmpty must beTrue
    }

    "Don't Refresh the token before the refresh interval" in {
      val initialToken = "cachedToken"
      val fileTokenRefreshable = MockFileTokenAuthRefreshable(FileTokenConfiguration(Some(initialToken), "/tmp/token", 100.seconds))
      fileTokenRefreshable.accessToken shouldEqual initialToken

    }

    "Refresh the token after the refresh interval" in {
      val initialToken = "cachedToken"
      val fileTokenRefreshable = MockFileTokenAuthRefreshable(FileTokenConfiguration(Some(initialToken), "/tmp/token", 10.milliseconds))

      Thread.sleep(500)
      val refreshed = fileTokenRefreshable.accessToken
      refreshed shouldNotEqual initialToken

      Thread.sleep(500)
      fileTokenRefreshable.accessToken shouldNotEqual refreshed
    }
  }
}
