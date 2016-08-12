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
package com.example.mesos

import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import org.apache.mesos.mesos.{ CommandInfo, FrameworkInfo, Resource, Value }

import com.nokia.mesos.DriverFactory
import com.nokia.mesos.FrameworkFactory
import com.nokia.mesos.api.async.MesosException
import com.nokia.mesos.api.async.MesosFramework
import com.nokia.mesos.api.async.TaskLauncher
import com.nokia.mesos.api.async.TaskLauncher.TaskDescriptor
import com.nokia.mesos.api.stream.MesosEvents.{ MesosEvent, TaskEvent }
import com.typesafe.scalalogging.LazyLogging

import rx.lang.scala.Subscriber

object Examples extends LazyLogging {

  final val localMesos = "localhost:5050"

  def main(args: Array[String]): Unit = {
    val fut = Examples.runSingleTask(localMesos, Examples.shellTaskDescriptor("sleep 10"))
    Await.ready(fut, 20.seconds)
  }

  /**
   * Registers an instance of our framework, launches a task, and terminates the
   * framework upon its completion
   *
   * @param masterUrl the url of the Mesos master e.g. "localhost:5050"
   * @param task      a TaskDescriptor e.g. shellTaskDescriptor("sleep 10")
   * @return a Future completed with success if the task is Finished or failed if
   *         the task encounters an error
   */
  def runSingleTask(masterUrl: String, task: TaskDescriptor): Future[Unit] = {
    val fwInfo = FrameworkInfo(
      name = "runSingleCommand test framework",
      user = "" // if empty string, Mesos will run task as current user
    )
    val driver = DriverFactory.createDriver(fwInfo, masterUrl)
    val fw = FrameworkFactory.createFramework(driver)

    val p = Promise[Unit]

    for {
      (fwId, master) <- fw.connect()
      task <- fw.submitTask(task).info
      _ = logger.info(s"Task successfully started on slave ${task.slaveId.value}")
      s = driver.eventProvider.events.collect {
        case e @ TaskEvent(task.taskId, _, _) => e
      }
      _ = s.subscribe(new Subscriber[MesosEvent] {
        override def onNext(e: MesosEvent): Unit = e match {
          case te: TaskEvent if te.state.isTaskFinished => p.success(())
          case te: TaskEvent if (te.state.isTaskError || te.state.isTaskFailed ||
            te.state.isTaskLost || te.state.isTaskKilled || te.state.isTaskKilling) =>
            p.failure(new MesosException("task encountered error"))
          case _ =>
        }
      })
    } yield ()

    for {
      _ <- p.future
      _ <- fw.terminate
    } yield ()
  }

  def shellTaskDescriptor(cmd: String): TaskDescriptor = {
    TaskDescriptor(
      "runSingleCommand task",
      Seq(Resource("cpus", Value.Type.SCALAR, Some(Value.Scalar(1.0)))),
      Left(CommandInfo(shell = Some(true), value = Some(cmd)))
    )
  }

  def createFw(): MesosFramework with TaskLauncher = {
    val fwInfo = FrameworkInfo(
      name = "itTestFw",
      user = ""
    )
    val driver = DriverFactory.createDriver(fwInfo, localMesos)
    FrameworkFactory.createFramework(driver)
  }

  def connect(): Future[MesosFramework with TaskLauncher] = {
    val fw = createFw()
    for {
      _ <- fw.connect()
    } yield fw
  }
}
