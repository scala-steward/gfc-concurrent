package org.gfccollective.concurrent

import java.util.concurrent.{TimeoutException, Executors}

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration.FiniteDuration

/**
 * Factory module to build timing out Futures.
 *
 * @author umatrangolo@gilt.com
 * @since 22-Nov-2014
 */
object Timeouts {
  import JavaConverters._
  private[concurrent] val scheduledExecutor = Executors.newSingleThreadScheduledExecutor().asScala

  /**
   * Returns a timing out Future.
   *
   * A failing Future is returned that will throw a TimeoutException after the given expiration time.
   *
   * @param after a FiniteDuration instance with the ttl of this Future.
   */
  def timeout[T](after: FiniteDuration): Future[T] = timeout(after, None)

  /**
   * Returns a timing out Future.
   *
   * A failing Future is returned that will throw a TimeoutException after the given expiration time.
   *
   * @param after a FiniteDuration instance with the ttl of this Future.
   * @param errorMessage Error message that will be used to construct any resultant TimeoutException
   */
  def timeout[T](after: FiniteDuration, errorMessage: Option[String]): Future[T] = scheduleTimeout(after, errorMessage)

  /**
   * Returns a Future that provides a value after a timeout.
   *
   * A succeeding Future is returned that will have the given value after the given expiration time.
   *
   * @param after A FiniteDuration instance with the ttl of this Future.
   * @param value Value of the returned Future after the timeout has lapsed
   */
  def delayedValue[T](after: FiniteDuration)(value: => T): Future[T] = {
    val timingOut = Promise[T]()

    scheduledExecutor.schedule(after) {
      timingOut.trySuccess(value)
    }

    timingOut.future
  }


  // TODO unclear if an HashedWheelTimer would be more efficient
  private def scheduleTimeout[T](after: FiniteDuration, errorMessage: Option[String]): Future[T] = {
    val timingOut = Promise()
    val now = System.currentTimeMillis()
    val origin = errorMessage.fold(new TimeoutException())(new TimeoutException(_))

    scheduledExecutor.schedule(after) {
      val elapsed = System.currentTimeMillis() - now
      timingOut.tryFailure(origin.initCause(new TimeoutException(s"""Timeout after ${after} (real: ${elapsed} ms.)""")))
    }

    timingOut.future
  }
}
