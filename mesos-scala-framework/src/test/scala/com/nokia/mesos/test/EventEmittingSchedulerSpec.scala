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

import org.apache.mesos.mesos._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, FlatSpec }

import com.nokia.mesos.SchedulerDriver
import com.nokia.mesos.api.stream.MesosEvents
import com.nokia.mesos.api.stream.MesosEvents.MesosEvent
import com.nokia.mesos.impl.scheduler.EventEmittingScheduler

class EventEmittingSchedulerSpec extends FlatSpec with Matchers with MockFactory {

  class TestEventEmittingScheduler extends EventEmittingScheduler {
    val notifier = mockFunction[MesosEvent, Unit]
    override def notify(event: MesosEvent): Unit = notifier(event)
  }

  val driver = stub[SchedulerDriver]

  val fwid = FrameworkID("fw-id")
  val master = MasterInfo("master-id", 1, 2)
  val slaveid = SlaveID("slave-id")
  val executorid = ExecutorID("executor-id")
  val offerid = OfferID("offer-id")
  val offer = Offer(offerid, fwid, slaveid, "slave-hostname")
  val taskid = TaskID("task-id")
  val data = Array[Byte]('a', 'b')

  val mut = new TestEventEmittingScheduler

  val notifier = mut.notifier

  "Scheduler" should "emit registered event" in {
    notifier.expects(MesosEvents.Registered(fwid, master))
    mut.registered(driver, fwid, master)
  }

  it should "emit reregistered event" in {
    notifier.expects(MesosEvents.ReRegistered(master))
    mut.reregistered(driver, master)
  }

  it should "emit disconnected event" in {
    notifier.expects(MesosEvents.Disconnected)
    mut.disconnected(driver)
  }

  it should "emit offer event" in {
    notifier.expects(MesosEvents.Offer(offer))
    mut.resourceOffers(driver, Seq(offer))
  }

  it should "emit offer rescinded event" in {
    notifier.expects(MesosEvents.Rescindment(offerid))
    mut.offerRescinded(driver, offerid)
  }

  it should "emit status update event" in {
    notifier.expects(MesosEvents.TaskEvent(taskid, TaskState.TASK_RUNNING, TaskStatus(taskid, TaskState.TASK_RUNNING)))
    mut.statusUpdate(driver, TaskStatus(taskid, TaskState.TASK_RUNNING))
  }

  it should "emit error event" in {
    notifier.expects(MesosEvents.MesosError("error message"))
    mut.error(driver, "error message")
  }

  it should "emit executor lost event" in {
    notifier.expects(MesosEvents.ExecutorLost(executorid, slaveid, -2))
    mut.executorLost(driver, executorid, slaveid, -2)
  }

  it should "emit slave lost event" in {
    notifier.expects(MesosEvents.SlaveLost(slaveid))
    mut.slaveLost(driver, slaveid)
  }

  it should "emit FW message event" in {
    notifier.expects(MesosEvents.FrameworkMessage(executorid, slaveid, data))
    mut.frameworkMessage(driver, executorid, slaveid, data)
  }
}
