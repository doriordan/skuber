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

  final case class MockFileTokenAuthRefreshable(config: FileTokenConfiguration) extends TokenAuthRefreshable with MockFileReaderComponent {}

  "FileTokenAuthRefreshable" should {
    "Retrieve the token if none provided" in {
      val initialToken : Option[RefreshableToken] = None
      val fileTokenRefreshable = MockFileTokenAuthRefreshable(FileTokenConfiguration(cachedAccessToken = initialToken, tokenPath = Some("/tmp/token"), refreshInterval = 100.milliseconds))
      fileTokenRefreshable.accessToken.nonEmpty must beTrue
    }

    "Refresh the token after the refresh interval" in {
      val initialToken = RefreshableToken("cachedToken", DateTime.now.plus(100.milliseconds.toMillis))
      val fileTokenRefreshable = MockFileTokenAuthRefreshable(FileTokenConfiguration(Some(initialToken), Some("/tmp/token"), 100.milliseconds))
      fileTokenRefreshable.accessToken shouldEqual initialToken.accessToken

      Thread.sleep(150)
      val refreshed = fileTokenRefreshable.accessToken
      refreshed shouldNotEqual initialToken.accessToken

      Thread.sleep(150)
      fileTokenRefreshable.accessToken shouldNotEqual refreshed
    }
  }
}
