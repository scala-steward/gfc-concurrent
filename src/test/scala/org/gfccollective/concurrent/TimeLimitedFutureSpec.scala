package org.gfccollective.concurrent

import java.util.concurrent.TimeoutException
import scala.concurrent.{ Future, Await }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimeLimitedFutureSpec extends AnyWordSpec with Matchers {
  import TimeLimitedFutureSpec._

  "RichFuture" when {
    import ScalaFutures._

    "withTimeout" should {
      "return the completed original Future if it completes before the given timeout" in {
        val now = System.currentTimeMillis
        val future: Future[String] = (Future { Thread.sleep(1000); "Here I am" }).withTimeout(Duration(5, "seconds"))
        val msg: String = Await.result(future, Duration(10, "seconds"))
        val elapsed = (System.currentTimeMillis - now)
        msg should equal ("Here I am")
        elapsed should be (2000L +- 1000L)
      }

      "return the failure of the original Future if it fails before the given timeout" in {
        val now = System.currentTimeMillis
        val future = (Future { Thread.sleep(1000); throw new NullPointerException("That hurts!") }).withTimeout(Duration(5, "seconds"))
        a [NullPointerException] should be thrownBy { Await.result(future, Duration(10, "seconds")) }
        val elapsed = (System.currentTimeMillis - now)
        elapsed should be (2000L +- 1000L)
      }

      "return the timeout of the original Future if it had one and it went off and was shorter than the given one" in {
        val now = System.currentTimeMillis
        val timingOutEarlier = Timeouts.timeout(Duration(1, "seconds"))
        val future = timingOutEarlier.withTimeout(Duration(5, "seconds"))
        a [TimeoutException] should be thrownBy { Await.result(future, Duration(10, "seconds")) }
        val elapsed: Long = (System.currentTimeMillis - now)
        elapsed should be >= 500l
        elapsed should be <= 4000l
      }

      "return the timeout if the original Future does not timeout of its own" in {
        val now = System.currentTimeMillis
        val timingOutLater = Timeouts.timeout(Duration(3, "seconds"))
        val future = timingOutLater.withTimeout(Duration(1, "seconds"))
        a [TimeoutException] should be thrownBy  { Await.result(future, Duration(10, "seconds")) }
        val elapsed: Long = (System.currentTimeMillis - now)
        elapsed should be >= 1000l
        elapsed should be <= 2500l
      }
    }

    "withTimeoutDefault" should {
      "return the completed original Future if it completes before the given timeout" in {
        val now = System.currentTimeMillis
        val future: Future[String] = (Future {
          Thread.sleep(1000); "Here I am"
        }).withTimeoutDefault(Duration(5, "seconds"))("timed out!")
        val msg: String = Await.result(future, Duration(10, "seconds"))
        val elapsed = (System.currentTimeMillis - now)
        msg should equal("Here I am")
        elapsed should be(2000L +- 1000L)
      }

      "return the failure of the original Future if it fails before the given timeout" in {
        val now = System.currentTimeMillis
        val future = (Future {
          Thread.sleep(1000); throw new NullPointerException("That hurts!")
        }).withTimeoutDefault(Duration(5, "seconds"))("timed out!")
        a[NullPointerException] should be thrownBy {
          Await.result(future, Duration(10, "seconds"))
        }
        val elapsed = (System.currentTimeMillis - now)
        elapsed should be(2000L +- 1000L)
      }

      "return the timeout of the original Future if it had one and it went off and was shorter than the given one" in {
        val now = System.currentTimeMillis
        val timingOutEarlier = Timeouts.delayedValue(Duration(1, "seconds"))("first timeout")
        val future = timingOutEarlier.withTimeoutDefault(Duration(5, "seconds"))("second timeout")
        val msg: String = Await.result(future, Duration(10, "seconds"))
        val elapsed: Long = (System.currentTimeMillis - now)
        msg should equal("first timeout")
        elapsed should be >= 500l
        elapsed should be <= 4000l
      }

      "return the timeout value if the original Future does not timeout of its own" in {
        val now = System.currentTimeMillis
        val timingOutLater = Timeouts.delayedValue(Duration(3, "seconds"))("first timeout")
        val future = timingOutLater.withTimeoutDefault(Duration(1, "seconds"))("second timeout")
        val msg: String = Await.result(future, Duration(10, "seconds"))
        val elapsed: Long = (System.currentTimeMillis - now)
        msg should equal("second timeout")
        elapsed should be >= 1000l
        elapsed should be <= 2500l
      }
    }

    // an example of how it could be used
    "used in our most common use case" should {
      "fit nicely" in {
        val call: Future[String] = svcCall(1000).withTimeout(Duration(5000, "milliseconds")).recover {
          case _: TimeoutException => "recover.timeout"
          case other => s"recover.${other.getMessage}"
        }
        Await.result(call, Duration(10, "seconds")) should be ("data-1000")

        val call2: Future[String] = svcCall(5000).withTimeout(Duration(1000, "milliseconds")).recover {
          case _: TimeoutException => "recover.timeout"
          case other => s"recover.${other.getMessage}"
        }
        Await.result(call2, Duration(10, "seconds")) should be ("recover.timeout")
      }
    }
  }
}

object TimeLimitedFutureSpec {
  def svcCall(latency: Long): Future[String] = Future { Thread.sleep(latency); s"data-${latency}" }
}
