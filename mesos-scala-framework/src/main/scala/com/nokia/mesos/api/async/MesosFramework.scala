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
package com.nokia.mesos.api.async

import scala.concurrent.Future

import org.apache.mesos.mesos._

/**
 * Higher level API for Mesos:
 * connect/disconnect and launching tasks.
 *
 * @see `TaskLauncher` for the automatic
 * handling of offers, and automatic task launching.
 */
trait MesosFramework {

  /**
   * Opens the connection to Mesos using the
   * FW and master info known by the driver.
   *
   * @return Future that completes once Mesos registered
   */
  def connect(): Future[(FrameworkID, MasterInfo)]

  /**
   * Closes the connection to Mesos
   * (with the possibility of reopening it later)
   *
   * @see `terminate`
   */
  def disconnect(): Future[Status]

  /**
   * Closes the connection to Mesos, with
   * no possibility of reopening
   *
   * @see `disconnect`
   */
  def terminate(): Future[Status]

  /**
   * Aborts the driver
   */
  def abort(): Future[Status]

  /**
   * Tries to launch the specified `tasks` by
   * using the selected `offerIds`
   *
   * @return Futures which are completed successfully
   * if and when the tasks were launched successfully
   *
   * @see `TaskLauncher.submitTasks` for automatic
   * offer selection for tasks
   */
  def launch(offerIds: Iterable[OfferID], tasks: Iterable[TaskInfo]): Iterable[Future[TaskInfo]]

  /**
   * Kills a previously launched task
   */
  def kill(taskId: TaskID): Future[TaskID]

  /**
   * Decline a previously received offer
   */
  def decline(offerId: OfferID): Unit

  // TODO: rest of driver methods (revive, etc.)
}

object MesosFramework {

  /**
   * After a task reaches any of these states,
   * we'll not expect state changes any more.
   */
  val terminalStates: Set[TaskState] = Set(
    TaskState.TASK_ERROR,
    TaskState.TASK_FINISHED,
    TaskState.TASK_FAILED,
    TaskState.TASK_KILLED,
    TaskState.TASK_LOST
  )
}
