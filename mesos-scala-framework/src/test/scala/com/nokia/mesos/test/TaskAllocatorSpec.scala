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

import scala.collection.immutable

import org.apache.mesos.mesos._
import org.scalatest.{ FlatSpec, Matchers }

import com.nokia.mesos.api.async.TaskLauncher.{ TaskAllocation, Filter, TaskDescriptor, TaskRequest }
import com.nokia.mesos.impl.launcher.TaskAllocator

class TaskAllocatorSpec extends FlatSpec with Matchers {

  val allocator = new TaskAllocator {}

  val ra = Resource("a", Value.Type.SCALAR, Some(Value.Scalar(5)))
  val rb = Resource("b", Value.Type.SCALAR, Some(Value.Scalar(6)))
  val td1 = TaskRequest(TaskDescriptor("task 1", immutable.Seq(ra), Left(CommandInfo())), TaskID("tid1"))
  val td2 = TaskRequest(TaskDescriptor("task 2", immutable.Seq(rb), Left(CommandInfo())), TaskID("tid2"))

  "Allocator" should "work for anti-affinity" in {
    val tasks = Seq(td1, td2)
    val filter: Filter = (m: TaskAllocation) => {
      val t1s = m.collectFirst { case (off, tss) if tss.contains(td1) => off.slaveId.value }.get
      val t2s = m.collectFirst { case (off, tss) if tss.contains(td2) => off.slaveId.value }.get
      t1s != t2s
    }
    val offers = immutable.Seq(
      Offer(OfferID("off1"), FrameworkID("fw-1"), SlaveID("s1"), "host", None, Seq(ra, rb)),
      Offer(OfferID("off2"), FrameworkID("fw-1"), SlaveID("s2"), "host", None, Seq(ra, rb))
    )
    allocator.tryAllocate(offers, tasks, Some(filter)) should be('defined)
  }

  it should "work with duplicate tasks" in {
    val offers = immutable.Seq(
      Offer(OfferID("off2"), FrameworkID("fw-1"), SlaveID("s2"), "host", None, Seq(ra))
    )
    allocator.tryAllocate(offers, Seq(td1, td1), None) shouldNot be('defined)
  }

  "TaskAllocator.allocate" should "generate all possibilities" in {
    val r = TaskAllocator.allocate(List(1, 2), List('a, 'b, 'c))
    val act = r.map(_.mapValues(_.to[List])).to[List]
    val exp = List(
      Map(1 -> List('a, 'b, 'c)),
      Map(1 -> List('b, 'c), 2 -> List('a)),
      Map(1 -> List('a, 'c), 2 -> List('b)),
      Map(1 -> List('a, 'b), 2 -> List('c)),
      Map(1 -> List('a), 2 -> List('b, 'c)),
      Map(1 -> List('b), 2 -> List('a, 'c)),
      Map(1 -> List('c), 2 -> List('a, 'b)),
      Map(2 -> List('a, 'b, 'c))
    )

    act.to[Set] should be(exp.to[Set])
  }

  it should "generate them lazily" in {
    val r: Stream[(Map[Int, Stream[Symbol]], Int)] = TaskAllocator.allocate(List(1, 2), List('a, 'b, 'c)).zipWithIndex
    r.map { case (v, idx) => if (idx >= 6) fail() else v }.take(6).toList shouldNot be('empty)
  }

  def seq[A](ss: Stream[Stream[A]]): List[List[A]] =
    TaskAllocator.sequenceStream(ss).map(_.toList).toList

  "TaskAllocator.sequenceStream" should "compute a cartesian product" in {
    seq(Stream.empty) should be(List(List()))
    seq(Stream(Stream.empty)) should be(List())
    seq(Stream(Stream(1))) should be(List(List(1)))
    seq(Stream(Stream(1), Stream(2))) should be(List(List(1, 2)))
    seq(Stream(Stream(1), Stream(2, 3))) should be(List(List(1, 2), List(1, 3)))
    seq(Stream(Stream(1, 2), Stream(3))) should be(List(List(1, 3), List(2, 3)))
    seq(Stream(Stream(1, 2), Stream(3, 4))) should be(List(List(1, 3), List(1, 4), List(2, 3), List(2, 4)))
    seq(Stream(Stream(1, 2), Stream())) should be(List())
    seq(Stream(Stream(), Stream(1, 2))) should be(List())
  }

  it should "be lazy" in {
    val res = TaskAllocator.sequenceStream(
      (1 #:: 2 #:: Stream()) #:: (3 #:: sys.error("err") #:: Stream()) #:: (5 #:: 6 #:: Stream()) #:: Stream()
    )
    res.take(2).toList should be(List(List(1, 3, 5), List(1, 3, 6)))
  }

  it should "be stack safe" in {
    val n = 100000
    val res = TaskAllocator.sequenceStream(
      Stream(1, 2) #:: Stream.range(0, n) #:: Stream()
    )
    val ints: List[Int] = res.flatten.toList
    ints.length should be(2 * 2 * n)
  }
}
