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

import org.apache.mesos.mesos._

trait TestData {

  val offerId = OfferID("offer id")
  val offerIds = Seq(offerId)
  val frameworkId = FrameworkID("fw id")
  val slaveId = SlaveID("slave id")
  val offers = Seq(Offer(offerId, frameworkId, slaveId, "host"))
  val taskId = TaskID("task id")
  val taskStatus = TaskStatus(taskId, TaskState.TASK_RUNNING)
  val masterInfo = MasterInfo("master id", 1, 2)
  val executorId = ExecutorID("executor id")
  val data = Array.fill[Byte](5)(0)
  val resource = Resource("resource", Value.Type.SCALAR, Some(Value.Scalar(5)))
  val request = Request(Some(slaveId), Seq(resource))
  val taskInfos = Seq(TaskInfo("task name", taskId, slaveId, Seq(resource)))
  val filters = Filters(Some(20.5))
}
