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
package com.nokia.mesos.impl.scheduler

import org.apache.mesos.mesos

import com.nokia.mesos.{ Scheduler, SchedulerDriver }
import com.nokia.mesos.api.stream.MesosEvents._
import com.typesafe.scalalogging.LazyLogging

/**
 * @see `EventProvidingScheduler`
 */
trait EventEmittingScheduler extends Scheduler with LazyLogging {

  protected def notify(event: MesosEvent): Unit

  override def statusUpdate(schedulerDriver: SchedulerDriver, taskStatus: mesos.TaskStatus): Unit =
    notify(TaskEvent(taskStatus))

  override def resourceOffers(schedulerDriver: SchedulerDriver, offers: Seq[mesos.Offer]): Unit =
    notify(Offer(offers.toList))

  override def offerRescinded(schedulerDriver: SchedulerDriver, offer: mesos.OfferID): Unit =
    notify(Rescindment(offer))

  override def registered(schedulerDriver: SchedulerDriver, frameworkId: mesos.FrameworkID, masterInfo: mesos.MasterInfo): Unit =
    notify(Registered(frameworkId, masterInfo))

  override def reregistered(schedulerDriver: SchedulerDriver, masterInfo: mesos.MasterInfo): Unit =
    notify(ReRegistered(masterInfo))

  override def disconnected(schedulerDriver: SchedulerDriver): Unit =
    notify(Disconnected)

  override def frameworkMessage(schedulerDriver: SchedulerDriver, executorId: mesos.ExecutorID, slaveId: mesos.SlaveID, data: Array[Byte]): Unit =
    notify(FrameworkMessage(executorId, slaveId, data))

  override def executorLost(schedulerDriver: SchedulerDriver, executorId: mesos.ExecutorID, slaveId: mesos.SlaveID, status: Int): Unit =
    notify(ExecutorLost(executorId, slaveId, status))

  override def slaveLost(schedulerDriver: SchedulerDriver, slaveId: mesos.SlaveID): Unit =
    notify(SlaveLost(slaveId))

  override def error(schedulerDriver: SchedulerDriver, message: String): Unit =
    notify(MesosError(message))
}
