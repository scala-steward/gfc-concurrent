package com.gilt.gfc.concurrent

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * Little helpers for scala futures
 *
 * @author Gregor Heine
 * @since 11/Jul/2014 13:25
 */
object ScalaFutures {
  implicit class FutureOps[A](val f: Future[A]) extends AnyVal {
    /**
     * Create a new Future that times out with a [[java.util.concurrent.TimeoutException]] after the given FiniteDuration
     */
    def withTimeout(after: FiniteDuration)(implicit ec: ExecutionContext): Future[A] =
      Future.firstCompletedOf(Seq(f, Timeouts.timeout(after)))
  }

  implicit class AsFuture[A](val a: A) extends AnyVal {
    @inline def asFuture: Future[A] = Future.successful(a)
  }

  /**
   * Asynchronously tests whether a predicate holds for some of the elements of a collection of futures
   */
  def exists[T](futures: TraversableOnce[Future[T]])
               (predicate: T => Boolean)
               (implicit executor: ExecutionContext): Future[Boolean] = {
    if (futures.isEmpty) Future.successful(false)
    else Future.find(futures)(predicate).map(_.isDefined)
  }

  /**
   * Asynchronously tests whether a predicate holds for all elements of a collection of futures
   */
  def forall[T](futures: TraversableOnce[Future[T]])
               (predicate: T => Boolean)
               (implicit executor: ExecutionContext): Future[Boolean] = {
    if (futures.isEmpty) Future.successful(true)
    else Future.find(futures)(!predicate(_)).map(_.isEmpty)
  }

  /**
   * Future of an empty Option
   */
  val FutureNone: Future[Option[Nothing]] = Future.successful(None)

  /**
   * Convert a Try into a Future
   */
  def fromTry[T](t: Try[T]): Future[T] = t match {
    case Success(s) => Future.successful(s)
    case Failure(e) => Future.failed(e)
  }

  /**
   * Convert Exceptions thrown by F into a failed Future
   */
  def safely[T](f: => Future[T]): Future[T] = Try(f) match {
    case Success(s) => s
    case Failure(e) => Future.failed(e)
  }

  /**
   * Improved version of [[scala.concurrent.Future.fold]], that fails the resulting Future as soon as one of the input Futures fails.
   */
  def foldFast[T, R >: T](futures: TraversableOnce[Future[T]])(zero: R)(foldFun: (R, T) => R)(implicit executor: ExecutionContext): Future[R] = {
    if (futures.isEmpty) Future.successful(zero)
    else {
      val atomic = new AtomicReference[R](zero)
      val promise = Promise[R]()
      val counter = new AtomicInteger()

      @tailrec def update(f: R => R): R = {
        val oldValue = atomic.get()
        val newValue = f(oldValue)
        if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
      }

      futures.foreach { _.onComplete {
        // succeed slow: only succeed when all futures have succeeded
        case Success(v) =>
          update(foldFun(_, v))
          if (counter.incrementAndGet() == futures.size) {
            promise.trySuccess(atomic.get)
          }
        // fail fast: fail as soon as the first future has failed
        case Failure(t) =>
          promise.tryFailure(t)
      }}

      promise.future
    }
  }

  /**
   * Retries a Future until it succeeds or a maximum number of retries has been reached.
   *
   * @param maxRetryTimes The maximum number of retries, defaults to Long.MaxValue. The future f is triggered at most maxRetryTimes + 1 times.
   *                      In other words, iff maxRetryTimes == 0, f will be called exactly once, iff maxRetryTimes == 1, it will be called at
   *                      most twice, etc.
   * @param f A function that returns a new Future
   * @param ec The ExecutionContext on which to retry the Future if it failed.
   * @param log An optional log function to report failed iterations to. By default prints the thrown Exception to the console.
   * @return A successful Future if the Future succeeded within maxRetryTimes or a failed Future otherwise.
   */
  def retry[T](maxRetryTimes: Long = Long.MaxValue)
              (f: => Future[T])
              (implicit ec: ExecutionContext,
                        log: Throwable => Unit = _.printStackTrace): Future[T] = {
    safely(f).recoverWith {
      case NonFatal(e) if maxRetryTimes > 0 =>
        log(e)
        retry(maxRetryTimes - 1)(f)
    }
  }


  /**
   * Retries a Future until it succeeds or a maximum number of retries has been reached, or a retry timeout
   * has been reached. Each retry iteration is being exponentially delayed. The delay grows from a given start value
   * and by a given factor until it reaches a given maximum delay value. If maxRetryTimeout is reached, the last
   * Future is scheduled at the point of the timeout. E.g. if the initial delay is 1 second, the retry timeout 10 seconds
   * and all other parameters at their default, the future will be retried after 1, 3 (=1+2), 7 (=1+2+4) and 10 seconds before it fails.
   *
   * @param maxRetryTimes The maximum number of retries, defaults to Long.MaxValue. The future f is triggered at most maxRetryTimes + 1 times.
   *                      In other words, iff maxRetryTimes == 0, f will be called exactly once, iff maxRetryTimes == 1, it will be called at
   *                      most twice, etc.
   * @param maxRetryTimeout The retry Deadline until which to retry the Future, defaults to 1 day from now
   * @param initialDelay The initial delay value, defaults to 1 nanosecond
   * @param maxDelay The maximum delay value, defaults to 1 day
   * @param exponentFactor The factor by which the delay increases between retry iterations
   * @param f A function that returns a new Future
   * @param ec The ExecutionContext on which to retry the Future if it failed.
   * @param log An optional log function to report failed iterations to. By default prints the thrown Exception to the console.
   * @return A successful Future if the Future succeeded within maxRetryTimes or a failed Future otherwise.
   */
  def retryWithExponentialDelay[T](maxRetryTimes: Long = Long.MaxValue,
                                   maxRetryTimeout: Deadline = 1 day fromNow,
                                   initialDelay: Duration = 1 millisecond,
                                   maxDelay: FiniteDuration = 1 day,
                                   exponentFactor: Double = 2)
                                  (f: => Future[T])
                                  (implicit ec: ExecutionContext,
                                   log: Throwable => Unit = _.printStackTrace): Future[T] = {
    require(exponentFactor >= 1)
    safely(f).recoverWith {
      case NonFatal(e) if (maxRetryTimes > 0 && !maxRetryTimeout.isOverdue()) =>
        log(e)
        val p = Promise[T]
        val delay = Seq(initialDelay, maxDelay, maxRetryTimeout.timeLeft).min
        Timeouts.scheduledExecutor.schedule(new Runnable() {
          override def run() {
            //              p.completeWith(retryWithExponentialDelay(maxRetryTimes - 1, maxRetryTimeout, delay * exponentFactor, maxDelay, exponentFactor)(f))
            Try(retryWithExponentialDelay(maxRetryTimes - 1, maxRetryTimeout, delay * exponentFactor, maxDelay, exponentFactor)(f)) match {
              case Success(future) => p.completeWith(future)
              case Failure(t) => p.failure(t)
            }
          }
        }, delay.toNanos, TimeUnit.NANOSECONDS)

        p.future
    }
  }

  object Implicits {
    implicit val sameThreadExecutionContext = SameThreadExecutionContext
  }
}
