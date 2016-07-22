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

import scala.collection.JavaConversions._

import org.apache.mesos.{ SchedulerDriver => JavaSchedulerDriver }
import org.apache.mesos.mesos._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ FlatSpec, Matchers }

import com.nokia.mesos.Scheduler

class SchedulerProxySpec extends FlatSpec with Matchers with MockFactory with TestData {

  val mockSched = mock[Scheduler]
  val mockDriver = stub[JavaSchedulerDriver]

  val scheduler = new SchedulerProxy(mockSched) // MUT

  "Proxy" should "delegate resource offers" in {
    (mockSched.resourceOffers _).expects(*, offers)
    scheduler.resourceOffers(mockDriver, offers.map(Offer.toJavaProto(_)))
  }

  it should "delegate rescindement" in {
    (mockSched.offerRescinded _).expects(*, offerId)
    scheduler.offerRescinded(mockDriver, OfferID.toJavaProto(offerId))
  }

  it should "delegate status update" in {
    (mockSched.statusUpdate _).expects(*, taskStatus)
    scheduler.statusUpdate(mockDriver, TaskStatus.toJavaProto(taskStatus))
  }

  it should "delegate registered" in {
    (mockSched.registered _).expects(*, frameworkId, masterInfo)
    scheduler.registered(mockDriver, FrameworkID.toJavaProto(frameworkId), MasterInfo.toJavaProto(masterInfo))
  }

  it should "delegate reregistered" in {
    (mockSched.reregistered _).expects(*, masterInfo)
    scheduler.reregistered(mockDriver, MasterInfo.toJavaProto(masterInfo))
  }

  it should "delegate disconnected" in {
    (mockSched.disconnected _).expects(*)
    scheduler.disconnected(mockDriver)
  }

  it should "delegate slaveLost" in {
    (mockSched.slaveLost _).expects(*, slaveId)
    scheduler.slaveLost(mockDriver, SlaveID.toJavaProto(slaveId))
  }

  it should "delegate executorLost" in {
    (mockSched.executorLost _).expects(*, executorId, slaveId, 5)
    scheduler.executorLost(mockDriver, ExecutorID.toJavaProto(executorId), SlaveID.toJavaProto(slaveId), 5)
  }

  it should "delegate frameworkMessage" in {
    (mockSched.frameworkMessage _).expects(*, executorId, slaveId, data)
    scheduler.frameworkMessage(mockDriver, ExecutorID.toJavaProto(executorId), SlaveID.toJavaProto(slaveId), data)
  }

  it should "delegate error" in {
    (mockSched.error _).expects(*, "message")
    scheduler.error(mockDriver, "message")
  }
}
