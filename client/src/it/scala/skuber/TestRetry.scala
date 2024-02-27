package skuber

import org.scalatest.{Canceled, Failed, Outcome, TestSuite}
import scala.annotation.tailrec

trait TestRetry extends TestSuite {

  //override retries to modify the number of retries
  val retries = 3
  override def withFixture(test: NoArgTest): Outcome = {
    retry(test, retries)
  }

  @tailrec
  private def retry(test: NoArgTest, count: Int): Outcome = {
    val outcome = super.withFixture(test)
    outcome match {
      case Failed(_) | Canceled(_) => if (count == 1) super.withFixture(test) else retry(test, count - 1)
      case other => other
    }
  }
}
