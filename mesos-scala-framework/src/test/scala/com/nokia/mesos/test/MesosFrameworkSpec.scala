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
package com.nokia.mesos.test

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Failure

import org.apache.mesos.mesos._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers, OneInstancePerTest }
import org.scalatest.concurrent.ScalaFutures

import com.nokia.mesos.SchedulerDriver
import com.nokia.mesos.api.async.MesosDriver
import com.nokia.mesos.api.async.MesosException
import com.nokia.mesos.api.async.Scheduling
import com.nokia.mesos.api.stream.MesosEvents
import com.nokia.mesos.api.stream.MesosEvents.MesosEvent
import com.nokia.mesos.impl.async.MesosFrameworkImpl

import rx.lang.scala.{ Observable, Subject }

class MesosFrameworkSpec extends FlatSpec with Matchers with ScalaFutures with MockFactory with OneInstancePerTest {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(4.second)

  val testTimeout = 2.second
  val timeoutMultiplier = 5

  val events = Subject[MesosEvent]()
  val driver = stub[SchedulerDriver]

  def emit(e: MesosEvent): Unit = events.onNext(e)

  // --== MUT ==--
  val fw = new MesosFrameworkImpl {

    override val connectTimeout = testTimeout
    override val launchTimeout = testTimeout
    override val killTimeout = testTimeout

    override def mkDriver =
      () => driver

    lazy val driver = new MesosDriver {
      override implicit val executor: ExecutionContext = scala.concurrent.ExecutionContext.global
      override val schedulerDriver: SchedulerDriver = MesosFrameworkSpec.this.driver
      override val eventProvider: MesosEvents = new MesosEvents {
        override def events: Observable[MesosEvent] = MesosFrameworkSpec.this.events
      }
    }

    protected def handle(offers: Seq[org.apache.mesos.mesos.Offer]): Future[Unit] = fail() // unused
    protected def scheduling: Scheduling = fail() // unused
  }

  //helpers
  protected def FwId(id: String) = FrameworkID(id)
  protected def Master(id: String) = MasterInfo(id = "master", port = 1234, ip = 1)

  private def terminate(): Unit = {
    (driver.join _).when.returns(Status.DRIVER_STOPPED)
    fw.terminate().futureValue should be(Status.DRIVER_STOPPED)
  }

  private def connect(): Unit = {
    (driver.start _).when.returns(Status.DRIVER_RUNNING)
    val connecting = fw.connect()
    emit(MesosEvents.Registered(FwId("fw-1"), Master("master-1")))
    connecting.futureValue._1 should be (FwId("fw-1"))
    connecting.futureValue._2 should be (Master("master-1"))
  }

  "connect" should "time out" in {
    (driver.start _).when.returns(Status.DRIVER_RUNNING)
    intercept[TimeoutException] { Await.result(fw.connect(), 100.milliseconds) }
  }

  it should "time out even if we wait for the future forever" in {
    (driver.start _).when.returns(Status.DRIVER_RUNNING)
    val ex = intercept[MesosException] { Await.result(fw.connect(), timeoutMultiplier * testTimeout) }
    assert(ex.getMessage.contains("timed out"))
  }

  it should "return the FW ID received from mesos" in {
    connect()
    terminate()
  }

  it should "fail if mesos disconnects" in {
    (driver.start _).when.returns(Status.DRIVER_RUNNING)
    val connecting = fw.connect()
    emit(MesosEvents.Disconnected)
    whenReady(connecting.failed) { e => e shouldBe a[MesosException] }
  }

  it should "fails on error - terminated framework id case" in {
    (driver.start _).when.returns(Status.DRIVER_RUNNING)
    val connecting = fw.connect()
    emit(MesosEvents.MesosError("Completed framework attempted to re-register"))
    whenReady(connecting.failed) { e => e shouldBe a[MesosException] }
  }

  "disconnect" should "work correctly" in {
    connect()
    (driver.join _).when.returns(Status.DRIVER_STOPPED)
    fw.disconnect.futureValue should be(Status.DRIVER_STOPPED)
    (driver.stop _).verify(true)
    (driver.join _).verify()
  }

  "terminate" should "work correctly" in {
    connect()
    terminate()
    (driver.stop _).verify(false)
    (driver.join _).verify()
  }

  "abort" should "work correctly" in {
    connect()
    (driver.join _).when.returns(Status.DRIVER_ABORTED)
    fw.abort().futureValue should be(Status.DRIVER_ABORTED)
  }

  val oid = OfferID("o1")
  val tid = TaskID("taskid-1")
  val task = TaskInfo("my task", tid, SlaveID("slave-1"))

  "launch" should "start a single task successfully" in {
    connect() //TODO refactor MesosFw into conn/task management so that this becomes unneccessary
    val launch = fw.launch(Seq(oid), Seq(task))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_RUNNING)))

    launch.head.futureValue.taskId should be(tid)

    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_FINISHED)))

    (driver.launchTasks _).verify(Seq(oid), Seq(task), None)
  }

  it should "launch single success, framework terminates" in {
    connect()
    val launch = fw.launch(Seq(oid), Seq(task))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_RUNNING)))
    launch.head.futureValue.taskId should be(tid)

    fw.terminate()

    (driver.launchTasks _).verify(Seq(oid), Seq(task), None)
  }

  it should "report failures" in {
    connect()
    val launch = fw.launch(Seq(oid), Seq(task))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_FAILED)))
    whenReady(launch.head.failed) { e =>
      e shouldBe a[MesosException]
    }

    (driver.launchTasks _).verify(Seq(oid), Seq(task), None)
  }

  it should "report timeout" in {
    connect()
    val launch = fw.launch(Seq(oid), Seq(task))
    val ex = intercept[MesosException] { Await.result(launch.head, timeoutMultiplier * testTimeout) }
    assert(ex.getMessage.contains("timed out"))
  }

  it should "handle transient stage and error" in { //TODO split to transient + task becomes failed after running
    connect()
    val launch = fw.launch(Seq(oid), Seq(task))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_STAGING)))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_RUNNING)))
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_FAILED)))
    launch.head.futureValue.taskId should be(tid)
  }

  "kill" should "work" in {
    connect()
    val kill = fw.kill(tid)
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_KILLED)))
    kill.futureValue should be(tid)

    (driver.killTask _).verify(tid)
  }

  it should "report timeout" in {
    connect()
    val ex = intercept[MesosException] { Await.result(fw.kill(tid), timeoutMultiplier * testTimeout) }
    assert(ex.getMessage.contains("timed out"))
  }

  it should "report nonexistent task" in {
    connect()
    val kill = fw.kill(tid)
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_LOST)))

    (driver.killTask _).verify(tid)
    Await.ready(kill, 1.second)
    kill.value match {
      case Some(Failure(ex: MesosException)) =>
        assert(ex.getMessage.toLowerCase.contains("lost"))
      case _ =>
        fail("Expected a MesosException")
    }
  }

  it should "handle normally finished task" in {
    connect()
    val kill = fw.kill(tid)
    emit(MesosEvents.TaskEvent(TaskStatus(tid, TaskState.TASK_FINISHED)))
    kill.futureValue should be(tid)

    (driver.killTask _).verify(tid)
  }
}
