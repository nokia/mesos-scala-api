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
package com.nokia.mesos.impl.launcher

import java.util.UUID

import scala.collection.{ concurrent, immutable }
import scala.concurrent.{ ExecutionContext, Future, Promise }

import org.apache.mesos.mesos
import org.apache.mesos.mesos.TaskInfo

import com.nokia.mesos.api.async.{ MesosFramework, TaskLauncher }
import com.nokia.mesos.api.async.Scheduling
import com.nokia.mesos.api.stream.MesosEvents
import com.nokia.mesos.api.stream.MesosEvents._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import com.nokia.mesos.api.async.MesosDriver

/**
 * Default implementation of `TaskLauncher`
 */
trait TaskLauncherImpl extends TaskLauncher with LazyLogging {

  import TaskLauncherImpl._
  import TaskLauncher._

  protected def fw: MesosFramework

  def scheduling: Scheduling

  private[this] val waitingTasks = new concurrent.TrieMap[RequestedTasks, Promise[Vector[mesos.TaskInfo]]]

  /**
   * Selects the tasks which can
   * be launched with the specified offers
   */
  private[this] def tasksForResources(
    offers: Seq[mesos.Offer]
  ): Future[(RequestedTasks, TaskAllocation, Promise[Vector[mesos.TaskInfo]])] = {

    def allocate(
      taskRequest: RequestedTasks,
      promise: Promise[Vector[mesos.TaskInfo]]
    ): Future[(RequestedTasks, TaskAllocation, Promise[Vector[mesos.TaskInfo]])] = {
      scheduling.offer(offers)
      scheduling.schedule(
        taskRequest.tasks,
        taskRequest.filter.getOrElse(NoFilter)
      ).map((taskRequest, _, promise))
    }

    def go(
      tasks: Iterator[(RequestedTasks, Promise[Vector[mesos.TaskInfo]])]
    ): Future[(RequestedTasks, TaskAllocation, Promise[Vector[mesos.TaskInfo]])] = {
      if (tasks.isEmpty) {
        Future.failed(Scheduling.NoMatch())
      } else {
        val (req, p) = tasks.next()
        allocate(req, p).map{ res =>
          // success, remove waiting request
          waitingTasks.remove(req)
          res
        }.recoverWith {
          case _: Scheduling.ScheduleException =>
            // retry with the next request
            go(tasks)
        }
      }
    }

    go(waitingTasks.iterator)
  }

  /**
   * Handles the offers as they arrive
   */
  protected def handle(offers: Seq[mesos.Offer]): Future[Unit] = {
    logger.info(s"handling offers $offers")
    tasksForResources(offers).andThen {
      case _ =>
        // whatever happens, the offers won't be valid any more
        scheduling.rescind(offers.map(_.id))
    }.flatMap {
      case (request, allocation, promise) =>
        logger info s"Launching ${allocation.values.map(_.size).sum} tasks in ${allocation.size} offers"
        // We need separate invocations of launch for every slave:
        val futs = for ((slave, allocation) <- allocation.groupBy { case (off, tss) => off.slaveId }) yield {
          val tasks = allocation.toSeq.flatMap { case (off, tasks) => tasks.map((off, _)) }
          val taskInfos = tasks.map { case (off, task) => taskInfo(off, task) }
          val tids = fw.launch(
            allocation.keys.map(_.id),
            taskInfos
          )
          Future.sequence(tids).map { tids => (tasks.map(_._2)) zip tids }
        }

        // Complete the promise with the launched tasks:
        val f = Future.sequence(futs).map(_.flatten.toMap)
        promise.completeWith(f map { m => request.tasks.map(m) })

        // Collect unused offers and decline them:
        val offersToDecline = offers filterNot { off => allocation.keySet.contains(off) }
        for (offer <- offersToDecline) {
          logger info s"Declining unused offer: ${offer.id.value}"
          fw.decline(offer.id)
        }
        Future.successful(())
    }.recoverWith {
      case ex: Any => {
        val logAct: (Logger, mesos.Offer) => Unit = ex match {
          case Scheduling.NoMatch() =>
            (l, o) => l debug s"No match for offer ${o.id.value}, declining"
          case se: Scheduling.ScheduleException =>
            (l, o) => l warn s"Cannot schedule offer ${o.id.value}, declining (error was: ${se.getClass.getSimpleName} ${se.getMessage})"
          case _ =>
            (_, _) => ()
        }
        for (offer <- offers) {
          logAct(logger, offer)
          fw.decline(offer.id)
        }
        ex match {
          case _: Scheduling.ScheduleException =>
            // we've already logged it
            Future.successful(())
          case ex: Any =>
            Future.failed(ex)
        }
      }
    }
  }

  override def submitTasks(tasks: Seq[TaskDescriptor], filter: Option[Filter]): immutable.Seq[LaunchedTask] = {
    // TODO: what if we're not connected yet?
    val p = Promise[Vector[mesos.TaskInfo]]()
    val withIds = tasks.toVector.map { t =>
      // generate random task ID:
      TaskRequest(t, mesos.TaskID(UUID.randomUUID().toString))
    }
    waitingTasks.put(RequestedTasks(withIds, filter), p)
    logger info s"New tasks (${tasks.size}) registered, waiting for offers"

    withIds.zipWithIndex.map {
      case (TaskRequest(_, id), idx) =>
        LaunchedTask.impl(
          p.future.map { tis => tis(idx) },
          fw.currentDriver().eventProvider.events
            .collect(MesosEvents.collectByTaskId(id))
            .takeUntil { ev => MesosFramework.terminalStates.contains(ev.state) }
            .replay.autoConnect
        )
    }
  }
}

object TaskLauncherImpl {

  import TaskLauncher._

  private final case class RequestedTasks(
    tasks: Vector[TaskRequest],
    filter: Option[Filter]
  )

  private def taskInfo(offer: mesos.Offer, task: TaskRequest): mesos.TaskInfo = {
    task.desc.job match {
      case Left(command) =>
        mesos.TaskInfo(
          name = task.desc.name,
          taskId = task.id,
          slaveId = offer.slaveId,
          resources = task.desc.resources,
          executor = None,
          command = Some(command),
          container = None
        )
      // TODO: make this case more generic; this will run docker as a task, with the default `docker run image`
      case Right(container) =>
        mesos.TaskInfo(
          name = task.desc.name,
          taskId = task.id,
          slaveId = offer.slaveId,
          resources = task.desc.resources,
          executor = None,
          command = Some(mesos.CommandInfo(shell = Some(false))),
          container = Some(container)
        )
    }
  }
}
