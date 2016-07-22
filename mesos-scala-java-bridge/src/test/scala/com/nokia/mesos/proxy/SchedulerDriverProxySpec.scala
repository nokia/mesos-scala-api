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
package com.nokia.mesos.proxy

import scala.collection.JavaConverters._

import org.apache.mesos.{ Protos, SchedulerDriver }
import org.apache.mesos.mesos._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

class SchedulerDriverProxySpec extends FlatSpec with MockFactory with Matchers with TestData {

  val mockDriver = mock[SchedulerDriver]
  val driver = new SchedulerDriverProxy(mockDriver)

  "proxy driver" should "delegate start" in {
    (mockDriver.start _).expects.returning(Protos.Status.DRIVER_RUNNING)
    driver.start() shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate stop" in {
    (mockDriver.stop(_: Boolean)).expects(false).returning(Protos.Status.DRIVER_RUNNING)
    driver.stop() shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate join" in {
    (mockDriver.join _).expects.returning(Protos.Status.DRIVER_STOPPED)
    driver.join() shouldBe Status.DRIVER_STOPPED
  }

  it should "delegate run" in {
    (mockDriver.run _).expects.returning(Protos.Status.DRIVER_RUNNING)
    driver.run() shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate abort" in {
    (mockDriver.abort _).expects.returning(Protos.Status.DRIVER_ABORTED)
    driver.abort() shouldBe Status.DRIVER_ABORTED
  }

  it should "delegate fw message" in {
    (mockDriver.sendFrameworkMessage _)
      .expects(ExecutorID.toJavaProto(executorId), SlaveID.toJavaProto(slaveId), data)
      .returning(Protos.Status.DRIVER_RUNNING)
    driver.sendFrameworkMessage(executorId, slaveId, data) shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate kill task" in {
    (mockDriver.killTask _).expects(TaskID.toJavaProto(taskId)).returning(Protos.Status.DRIVER_RUNNING)
    driver.killTask(taskId) shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate task status acknowledgement" in {
    (mockDriver.acknowledgeStatusUpdate _)
      .expects(TaskStatus.toJavaProto(taskStatus))
      .returning(Protos.Status.DRIVER_RUNNING)
    driver.acknowledgeStatusUpdate(taskStatus) shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate revive offers" in {
    (mockDriver.reviveOffers _).expects.returning(Protos.Status.DRIVER_RUNNING)
    driver.reviveOffers() shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate request resources" in {
    (mockDriver.requestResources _)
      .expects(Seq(Request.toJavaProto(request)).asJavaCollection)
      .returning(Protos.Status.DRIVER_RUNNING)
    driver.requestResources(Seq(request)) shouldBe Status.DRIVER_RUNNING
  }

  it should "delegate reconcline tasks" in {
    (mockDriver.reconcileTasks _)
      .expects(Seq(TaskStatus.toJavaProto(taskStatus)).asJavaCollection)
      .returning(Protos.Status.DRIVER_RUNNING)
    driver.reconcileTasks(Seq(taskStatus)) shouldBe Status.DRIVER_RUNNING
  }
}
