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
package com.nokia.mesos

import org.apache.mesos.mesos._

/**
 * This interface replicates the Scheduler java interface with Scala types.
 * It depends on the Scala version of the SchedulerDriver interface.
 */
trait Scheduler {

  // tasks

  def statusUpdate(schedulerDriver: SchedulerDriver, taskStatus: TaskStatus): Unit

  // offers

  def resourceOffers(schedulerDriver: SchedulerDriver, offers: Seq[Offer]): Unit

  def offerRescinded(schedulerDriver: SchedulerDriver, offer: OfferID): Unit

  // registration

  def registered(schedulerDriver: SchedulerDriver, frameworkId: FrameworkID, masterInfo: MasterInfo): Unit

  def reregistered(schedulerDriver: SchedulerDriver, masterInfo: MasterInfo): Unit

  def disconnected(schedulerDriver: SchedulerDriver): Unit

  // framework communication

  def frameworkMessage(schedulerDriver: SchedulerDriver, executorId: ExecutorID, slaveId: SlaveID, data: Array[Byte]): Unit

  // errors

  def slaveLost(schedulerDriver: SchedulerDriver, slaveId: SlaveID): Unit

  def executorLost(schedulerDriver: SchedulerDriver, executorId: ExecutorID, slaveId: SlaveID, status: Int): Unit

  def error(schedulerDriver: SchedulerDriver, message: String): Unit
}
