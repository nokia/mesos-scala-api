/* Copyright (c) 2016, Nokia Solutions and Networks Oy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Nokia Solutions and Networks Oy nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL NOKIA SOLUTIONS AND NETWORKS OY BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nokia.mesos.impl.async

import java.util.concurrent.atomic.AtomicReference

import scala.collection.concurrent
import scala.concurrent.{ blocking, Future, Promise }
import scala.concurrent.duration._

import org.apache.mesos.mesos.{ OfferID, TaskInfo, _ }
import org.apache.mesos.mesos.TaskState.{ TASK_STAGING, TASK_STARTING }

import com.nokia.mesos.api.async._
import com.nokia.mesos.api.async.MesosDriver
import com.nokia.mesos.api.stream.MesosEvents._
import com.typesafe.scalalogging.LazyLogging

import rx.lang.scala.{ Observable, Subscriber, Subscription }

/** Default implementation of `MesosFramework` */
trait MesosFrameworkImpl extends MesosFramework with LazyLogging {

  import MesosFrameworkImpl._

  /**
   * We depend on a driver, which
   * actually communicates with Mesos
   */
  protected def mkDriver: () => MesosDriver

  /**
   * If no connection related event arrives for this duration,
   * the connection attempt will be considered unsuccessful.
   */
  val connectTimeout: Duration = 30.seconds

  /**
   * If no task launch related event arrives for this duration,
   * the launch attempt will be considered unsuccessful.
   */
  val launchTimeout: Duration = 30.seconds

  /**
   * If no task kill related event arrives for this duration,
   * the kill attempt will be considered unsuccessful.
   */
  val killTimeout: Duration = 30.seconds

  /** Subscriptions of currently running tasks */
  private[this] val taskStateSubscriptions = new concurrent.TrieMap[TaskID, Subscription]

  /** Current state of the framework */
  private[this] val state = new AtomicReference[State](State.Disconnected)

  protected def currentDriver(): MesosDriver =
    currentDriver("currently in disconnected state")

  protected def currentDriver(msg: String): MesosDriver = {
    state.get() match {
      case hc: HalfConnected =>
        hc.driver
      case State.Disconnected =>
        throw new IllegalArgumentException(msg)
    }
  }

  override def connect(): Future[(FrameworkID, MasterInfo, MesosDriver)] = {
    val driver = mkDriver()
    val connecting = State.Connecting(driver)
    if (!state.compareAndSet(State.Disconnected, connecting)) {
      require(false, "not in disconnected state")
    }

    val p: Promise[(FrameworkID, MasterInfo, MesosDriver)] = Promise()
    val connectionEvents = driver.eventProvider.events.collect { case e @ (_: Registered | Disconnected | _: MesosError) => e }
    connectionEvents.timeout(connectTimeout).subscribe(
      new Subscriber[MesosEvent] {

        override def onError(ex: Throwable): Unit = {
          state.compareAndSet(connecting, State.Disconnected)
          MesosFrameworkImpl.handleTimeout(ex, p, "connection attempt timed out")
        }

        override def onNext(e: MesosEvent): Unit = e match {
          case r: Registered =>
            unsubscribe()
            state.set(State.Connected(driver))
            p.success((r.frameworkId, r.masterInfo, driver))
          case Disconnected =>
            unsubscribe()
            state.set(State.Disconnected)
            p.failure(new MesosException("Disconnected while connecting"))
          case e: MesosError =>
            unsubscribe()
            state.compareAndSet(connecting, State.Disconnected)
            p.failure(new MesosException(e.message))
          case _ =>
            // this can never happen
            throw new AssertionError(s"unexpected event: $e")
        }
      }
    )

    logger info "Starting Mesos driver"
    val status = driver.schedulerDriver.start
    if (!status.isDriverRunning) {
      p.tryFailure(new RuntimeException(s"driver is not running, but $status"))
    }

    p.future
  }

  override def disconnect(): Future[Status] = {
    stop(Failover)
  }

  /**
   * This method signifies that it is expected that this framework will
   * never reconnect to Mesos. So Mesos will unregister the framework
   * and shutdown all its tasks and executors
   */
  override def terminate(): Future[Status] = {
    stop(Stop)
  }

  override def abort(): Future[Status] = {
    stop(Abort)
  }

  override def launch(offerIds: Iterable[OfferID], tasks: Iterable[TaskInfo]): Iterable[Future[TaskInfo]] = {
    val driver = state.get().requireConnected()
    val promises = tasks.map(task => (task.taskId, launchHandler(task, driver)))
    logger info s"Launching tasks $tasks in offers $offerIds"
    driver.schedulerDriver.launchTasks(offerIds, tasks)
    promises.map { case (_, p) => p.future }
  }

  /**
   * Subscribes to task events of the given task, and returns a promise
   * that completes with success if the task becomes TASK_RUNNING or with failure
   * in case the task becomes failed.
   *
   * @param task The task which will be launched
   * @return Promise of task to be completed once task reaches a running state
   */
  private[this] def launchHandler(task: TaskInfo, driver: MesosDriver): Promise[TaskInfo] = {
    // FIXME: what to do if 2 TASK_RUNNING arrives for 1 task (in theory, that shouldn't happen)
    val promise = Promise[TaskInfo]()
    val taskEventStream = driver.eventProvider.events.collect(collectByTaskId(task.taskId))
    val replayStream = taskEventStream.replay
    taskEventStream.timeout(launchTimeout).subscribe(
      new Subscriber[TaskEvent]() {

        override def onError(ex: Throwable): Unit =
          MesosFrameworkImpl.handleTimeout(ex, promise, "task launch attempt timed out")

        override def onNext(e: TaskEvent): Unit = e match {
          case TaskEvent(_, TaskState.TASK_RUNNING, _) => {
            logger info s"Task ${task.taskId.value} is RUNNING, so we successfully launched it"
            unsubscribe()
            subscribeForTaskStateChanges(taskEventStream, task.taskId)
            promise.success(task)
          }
          case TaskEvent(_, st @ (TASK_STAGING | TASK_STARTING), _) => {
            // don't unsubscribe, wait for it to really start
            logger info s"Task ${task.taskId.value} is ${st}, so we're still waiting for it to start"
          }
          case TaskEvent(_, st, _) => {
            unsubscribe()
            promise.failure(new MesosException(s"Task '${task.taskId.value}' became $st"))
          }
        }
      }
    )
    promise
  }

  /**
   * Subscribes for task state updates.
   * Unsubscribes if the task reaches a
   * terminal state.
   */
  private[this] def subscribeForTaskStateChanges(stream: Observable[TaskEvent], tid: TaskID): Unit = {
    // Note: we subscribe WITHOUT timeout, since
    // a task can take a long time to finish.
    if (state.get().isConnected) {
      val prev = taskStateSubscriptions.put(
        tid,
        stream.subscribe(
          new Subscriber[TaskEvent]() {
            override def onNext(e: TaskEvent): Unit = {
              if (MesosFramework.terminalStates.contains(e.state)) {
                unsubscribe()
              }
            }
          }
        )
      )

      // clean up previous subscription (if any):
      prev.foreach(_.unsubscribe())

      if (!state.get().isConnected) {
        // disconnected in the meantime
        taskStateSubscriptions.remove(tid).foreach(_.unsubscribe())
      }
    }
  }

  override def kill(taskId: TaskID): Future[TaskID] = {
    val driver = state.get().requireConnected()
    val p: Promise[TaskID] = Promise()
    driver.eventProvider.events
      .collect(collectByTaskId(taskId))
      .timeout(killTimeout)
      .subscribe(new Subscriber[TaskEvent]() {

        override def onError(ex: Throwable): Unit =
          MesosFrameworkImpl.handleTimeout(ex, p, "task kill timed out")

        override def onNext(e: TaskEvent): Unit = e match {
          case TaskEvent(`taskId`, TaskState.TASK_KILLED, _) => {
            logger info s"Successfully killed task ${taskId.value}"
            unsubscribe()
            p.success(taskId)
          }
          case TaskEvent(`taskId`, TaskState.TASK_LOST, ts) => {
            unsubscribe()
            // we probably tried to kill an already completed task
            logger warn s"Failed to kill task ${taskId.value} (TASK_LOST)"
            p.failure(new MesosException(s"TASK_LOST: ${ts.message.getOrElse(taskId.value)}"))
          }
          case TaskEvent(`taskId`, otherState, _) if MesosFramework.terminalStates(otherState) => {
            unsubscribe()
            // we cannot kill it, but it is terminated anyway
            logger info s"Task ${taskId.value} is already in terminal state"
            p.success(taskId)
          }
          case _ => // not yet killed, ignore
        }
      })

    logger info s"Trying to kill task ${taskId.value}"
    driver.schedulerDriver.killTask(taskId)
    p.future
  }

  override def decline(offerId: OfferID): Unit = {
    val driver = currentDriver("cannot decline in disconnected state")
    logger debug s"Declining offer ${offerId.value}"
    driver.schedulerDriver.declineOffer(offerId)
  }

  /**
   * Cleans up subscriptions, and
   * disconnects form Mesos
   *
   * @param act Whether to abort, terminate or
   * simply disconnect
   */
  private def stop(act: Action): Future[Status] = {
    val driver = startDisconnecting()
    // unsubscribe from updates of currently running tasks:
    for (id <- taskStateSubscriptions.keys) {
      taskStateSubscriptions.remove(id).foreach(_.unsubscribe())
    }

    act match {
      case Stop =>
        logger info "Stopping Mesos driver"
        driver.schedulerDriver.stop(failover = false)
      case Failover =>
        logger info "Stopping Mesos driver (suspend only)"
        driver.schedulerDriver.stop(failover = true)
      case Abort =>
        logger info "Aborting Mesos driver"
        driver.schedulerDriver.abort()
    }

    Future {
      val status = blocking { driver.schedulerDriver.join() }
      finishDisconnecting()
      status
    } (driver.executor)
  }

  private def startDisconnecting(): MesosDriver = {
    state.get() match {
      case c @ State.Connected(dr) =>
        if (!state.compareAndSet(c, State.Disconnecting(dr))) {
          // retry
          startDisconnecting()
        } else {
          // we're done
          dr
        }
      case _ =>
        throw new IllegalArgumentException(notConnected)
    }
  }

  private def finishDisconnecting(): Unit = {
    state.get() match {
      case c @ State.Disconnecting(_) =>
        if (!state.compareAndSet(c, State.Disconnected)) {
          // retry
          finishDisconnecting()
        }
      case _ =>
        throw new IllegalStateException(s"state changed to ${state} while disconnecting")
    }
  }
}

private object MesosFrameworkImpl {

  private final val notConnected = "not in connected state"

  private def handleTimeout(ex: Throwable, promise: Promise[_], msg: String): Unit = ex match {
    case t: java.util.concurrent.TimeoutException =>
      promise.failure(new MesosException(msg))
    case t: Any =>
      promise.failure(t)
  }

  private sealed trait State {
    def isConnected: Boolean =
      false
    def requireConnected(): MesosDriver =
      throw new IllegalArgumentException(notConnected)
  }

  private sealed trait HalfConnected extends State {
    def driver: MesosDriver
  }

  private object State {

    case object Disconnected extends State

    final case class Connecting(driver: MesosDriver) extends HalfConnected

    final case class Connected(driver: MesosDriver) extends HalfConnected {

      override def isConnected =
        true

      override def requireConnected: MesosDriver =
        driver
    }

    final case class Disconnecting(driver: MesosDriver) extends HalfConnected
  }

  private sealed trait Action
  private case object Stop extends Action
  private case object Failover extends Action
  private case object Abort extends Action
}
