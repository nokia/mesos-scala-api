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

import scala.collection.concurrent
import scala.concurrent.Future

import org.apache.mesos.mesos.{ Offer, OfferID }

import com.nokia.mesos.api.async.Scheduling
import com.nokia.mesos.api.async.TaskLauncher.{ Filter, NoFilter, TaskAllocation, TaskRequest }

/**
 * A simple, naive implementation of `Scheduling`
 */
class SimpleScheduling extends Scheduling with TaskAllocator {

  private[this] val currentOffers = new concurrent.TrieMap[OfferID, Offer]

  def offer(offers: Seq[Offer]): Unit = {
    currentOffers ++= offers.map(o => (o.id, o))
  }

  def rescind(offers: Seq[OfferID]): Unit = {
    for (off <- offers) {
      currentOffers.remove(off)
    }
  }

  def schedule(tasks: Seq[TaskRequest], filter: Filter, urgency: Float): Future[TaskAllocation] = {
    val optAllocation: Option[TaskAllocation] = tryAllocate(
      currentOffers.values.toSeq,
      tasks,
      if (filter eq NoFilter) None else Some(filter)
    )
    optAllocation.fold {
      Future.failed[TaskAllocation](Scheduling.NoMatch())
    } { allocation =>
      // we cannot use these offers any more:
      this.rescind(allocation
        .filter { case (off, tsks) => tsks.nonEmpty }
        .map(_._1.id)
        .toSeq)

      Future.successful(allocation)
    }
  }
}
