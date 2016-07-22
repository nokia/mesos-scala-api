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

import scala.annotation.tailrec

import org.apache.mesos.mesos

import com.nokia.mesos.api.async.TaskLauncher
import com.typesafe.scalalogging.LazyLogging

/**
 * Implements a naive task allocation algorithm
 *
 * @see `SimpleScheduling`
 */
trait TaskAllocator extends LazyLogging {

  import TaskLauncher._

  def tryAllocate(offers: Seq[mesos.Offer], tasks: Seq[TaskDescriptor], optFilter: Option[Filter]): Option[TaskAllocation] = {
    logger info s"Searching for valid allocation for tasks ${tasks} in offers: $offers"

    // optimization: first quickly check approximate resource constraints
    if (trySimpleAllocation(ResourceProcessor.sumResources(offers.flatMap(_.resources)), tasks)) {
      // generate actual allocations, and verify them:
      val filter: (TaskAllocation => Boolean) = optFilter match {
        case Some(f) => (m => resourceFilter(m) && f(m))
        case None => resourceFilter
      }
      val res = generateAllocations(offers, tasks.to[List]).find(filter)
      res.fold(logger info "No valid allocation found")(all => logger info s"Found valid allocation: $all")
      res
    } else {
      // it's impossible to create a correct allocation,
      // so we don't even have to try generating them:
      logger info s"No allocation possible: offer have insufficient resources to launch all tasks"
      None
    }
  }

  def resourceFilter: Filter = (m: TaskAllocation) => {
    m.forall {
      case (offer, tasks) => {
        val remains = tasks.foldLeft(Option(offer.resources.toVector)) {
          case (Some(remainder), task) =>
            ResourceProcessor.remainderOf(remainder, task.resources)
          case (None, _) =>
            None
        }

        remains.isDefined
      }
    }
  }

  /** Simply checks whether all the resources are enough to launch all the tasks */
  @tailrec
  private[mesos] final def trySimpleAllocation(rs: Vector[mesos.Resource], tasks: Seq[TaskDescriptor]): Boolean = tasks.to[List] match {
    case Nil =>
      true
    case task :: rest =>
      ResourceProcessor.remainderOf(rs, task.resources) match {
        case Some(remainder) => trySimpleAllocation(remainder, rest)
        case None => false
      }
  }

  private[mesos] def generateAllocations(offers: Seq[mesos.Offer], tasks: List[TaskDescriptor]): Iterator[TaskAllocation] = {
    val a = TaskAllocator.allocate(offers, tasks)
    a.map(_.mapValues(_.to[List])).iterator
  }
}

private[mesos] object TaskAllocator {

  private[mesos] def allocate[O, T](offers: TraversableOnce[O], tasks: TraversableOnce[T]): Stream[Map[O, Stream[T]]] = {
    allocateImpl(offers, tasks) map (_.groupBy(_._1).mapValues(_.map(_._2)))
  }

  private[mesos] def allocateImpl[O, T](offers: TraversableOnce[O], tasks: TraversableOnce[T]): Stream[Stream[(O, T)]] = {
    val os = offers.toStream
    val ts = tasks.toStream
    val r = for {
      t <- ts
    } yield {
      for {
        o <- os
      } yield (o, t)
    }

    sequenceStream(r)
  }

  /**
   * Lazy Cartesian product of a `Stream` of `Stream`s
   */
  private[mesos] def sequenceStream[A](ss: Stream[Stream[A]]): Stream[Stream[A]] = {
    val z = Stream.empty[A] #:: Stream.empty
    ss.foldRight(z) { (s, acc) =>
      s.flatMap { a =>
        acc.map(a #:: _)
      }
    }
  }
}
