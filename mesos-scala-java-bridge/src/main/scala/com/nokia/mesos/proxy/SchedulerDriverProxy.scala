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

import org.apache.mesos.{ SchedulerDriver => JavaSchedulerDriver }
import org.apache.mesos.mesos._

import com.nokia.mesos.{ SchedulerDriver => ScalaSchedulerDriver }

/**
 * Proxy implementation of the `ScalaSchedulerDriver` API.
 * All method calls are delegated to the given (java) scheduler instance.
 * Each call includes copy conversions of the parameters and the return value.
 *
 * @param target The wrapped scheduler driver to which calls are delegated.
 */
class SchedulerDriverProxy(target: JavaSchedulerDriver) extends ScalaSchedulerDriver {

  override def start(): Status = Status.fromJavaValue(target.start)

  override def stop(failover: Boolean): Status = Status.fromJavaValue(target.stop(failover))

  override def join(): Status = Status.fromJavaValue(target.join())

  override def run(): Status = Status.fromJavaValue(target.run())

  override def abort(): Status = Status.fromJavaValue(target.abort())

  override def declineOffer(id: OfferID, filters: Option[Filters] = None): Status = {
    filters match {
      case Some(filters) => Status.fromJavaValue(
        target.declineOffer(OfferID.toJavaProto(id), Filters.toJavaProto(filters))
      )
      case None => Status.fromJavaValue(
        target.declineOffer(OfferID.toJavaProto(id))
      )
    }
  }

  override def launchTasks(offerIds: Iterable[OfferID], tasks: Iterable[TaskInfo], filters: Option[Filters] = None): Status = {
    filters match {
      case Some(filters) => Status.fromJavaValue(
        target.launchTasks(
          offerIds.map(OfferID.toJavaProto(_)).asJavaCollection,
          tasks.map(TaskInfo.toJavaProto(_)).asJavaCollection,
          Filters.toJavaProto(filters)
        )
      )
      case None => Status.fromJavaValue(
        target.launchTasks(
          offerIds.map(OfferID.toJavaProto(_)).asJavaCollection,
          tasks.map(TaskInfo.toJavaProto(_)).asJavaCollection
        )
      )
    }
  }

  override def killTask(taskId: TaskID): Status = {
    Status.fromJavaValue(target.killTask(TaskID.toJavaProto(taskId)))
  }

  override def acknowledgeStatusUpdate(taskStatus: TaskStatus): Status = {
    Status.fromJavaValue(target.acknowledgeStatusUpdate(
      TaskStatus.toJavaProto(taskStatus)
    ))
  }

  override def requestResources(requests: Iterable[Request]): Status = {
    Status.fromJavaValue(
      target.requestResources(requests.map(Request.toJavaProto(_)).asJavaCollection)
    )
  }

  override def sendFrameworkMessage(executorId: ExecutorID, slaveId: SlaveID, data: Array[Byte]): Status = {
    Status.fromJavaValue(target.sendFrameworkMessage(
      ExecutorID.toJavaProto(executorId),
      SlaveID.toJavaProto(slaveId),
      data
    ))
  }

  override def reconcileTasks(statuses: Iterable[TaskStatus]): Status = {
    Status.fromJavaValue(
      target.reconcileTasks(statuses.map(TaskStatus.toJavaProto(_)).asJavaCollection)
    )
  }

  override def reviveOffers(): Status =
    Status.fromJavaValue(target.reviveOffers())
}
