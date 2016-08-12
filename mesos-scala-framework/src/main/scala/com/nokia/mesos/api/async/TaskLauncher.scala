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

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.mesos.mesos._

import com.nokia.mesos.api.stream.MesosEvents.TaskEvent

import rx.lang.scala.Observable

/**
 * High level task launching API
 *
 * Automatically handles the matching
 * of task requests with offers.
 */
trait TaskLauncher {

  import TaskLauncher._

  implicit protected def executor: ExecutionContext

  /**
   * Submits the specified `tasks`; the framework
   * will try to automatically allocate incoming
   * offers to launch these tasks.
   *
   * @param tasks The tasks to launch
   * @param filter Optionally a filter, which can
   * reject allocations (e.g., to implement affinity
   * rules)
   */
  def submitTasks(tasks: Seq[TaskDescriptor], filter: Option[Filter]): immutable.Seq[LaunchedTask]

  // convenience methods:

  /**
   * @see `submitTasks`
   */
  def submitTask(task: TaskDescriptor): LaunchedTask =
    submitTasks(Seq(task), None).head

  /**
   * @see `submitTasks`
   */
  def submitTask(task: TaskDescriptor, filter: Filter): LaunchedTask =
    submitTasks(Seq(task), Some(filter)).head

  /** Convenience overload, to submit without a filter */
  def submitTasks(tasks: Seq[TaskDescriptor]): immutable.Seq[LaunchedTask] =
    submitTasks(tasks, None)

  /** Convenience overload, to submit with a filter */
  def submitTasks(tasks: Seq[TaskDescriptor], filter: Filter): immutable.Seq[LaunchedTask] =
    submitTasks(tasks, Some(filter))
}

object TaskLauncher {

  /**
   * A TaskInfo structure without SlaveID, as it becomes known only when offers are found
   */
  final case class TaskDescriptor(
    name: String,
    resources: Seq[Resource],
    job: Either[CommandInfo, ContainerInfo],
    labels: Seq[Label] = Nil
  )

  /**
   * A task requested to be launched
   */
  final case class TaskRequest(
    desc: TaskDescriptor,
    id: TaskID
  )

  /**
   * Info about a launched task
   */
  sealed trait LaunchedTask {

    /** Completed when the task is successfully started */
    val info: Future[TaskInfo]

    /** Cold Observable of all events related to this task */
    val events: Observable[TaskEvent]
  }

  private[mesos] object LaunchedTask {
    def impl(i: Future[TaskInfo], e: Observable[TaskEvent]): LaunchedTask = new LaunchedTask {
      override val info = i
      override val events = e
    }
  }

  /**
   * Offers matched with the tasks to launch with them
   */
  type TaskAllocation = Map[Offer, List[TaskRequest]]

  /**
   * Filtering for possible allocations,
   * for rejecting allocations based on,
   * e.g., affinity rules.
   */
  type Filter = (TaskAllocation => Boolean)

  /** Constant true filter */
  val NoFilter: Filter = _ => true
}
