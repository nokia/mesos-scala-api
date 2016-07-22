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

import org.apache.mesos.mesos
import org.apache.mesos.mesos.Resource
import org.apache.mesos.mesos.Value.{ Range, Ranges, Scalar, Type }
import org.scalacheck.Gen
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import com.nokia.mesos.impl.launcher.ResourceProcessor

class ResourceProcessorSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  val r1 = Resource("a", Type.SCALAR, Some(Scalar(5)))
  val r2 = Resource("a", Type.SCALAR, Some(Scalar(3)))
  val rx = Resource("x", Type.SCALAR, Some(Scalar(3)))
  val rx2 = Resource("x", Type.SCALAR, Some(Scalar(2)))
  val ry = Resource("y", Type.SET, set = Some(mesos.Value.Set(Seq("aaa", "bbb", "ccc"))))
  val rz1 = Resource("y", Type.SET, set = Some(mesos.Value.Set(Seq("bbb"))))
  val rz2 = Resource("y", Type.SET, set = Some(mesos.Value.Set(Seq("aaa", "bbb", "ccc", "xxx"))))

  val range1 = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(1, 3), Range(11, 13)))))
  val range1rem = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(1, 1), Range(3, 3), Range(11, 13)))))
  val range2 = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(4, 6), Range(14, 16)))))
  val range2rem = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(4, 6), Range(14, 14)))))
  val rangeReq = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(2, 2))))) //, Range(15, 16)))))
  val rangeX = Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(2, 2), Range(15, 17)))))

  "trySubtractResource" should "subtract scalars" in {
    ResourceProcessor.trySubtractResource(r1, r2) should be(Some(Resource("a", Type.SCALAR, Some(Scalar(2)))))
  }

  it should "not subtract if the name is different" in {
    ResourceProcessor.trySubtractResource(r1, rx) should be(None)
  }

  it should "not subtract if the type is different" in {
    ResourceProcessor.trySubtractResource(r1, ry) should be(None)
  }

  it should "not subtract if the value would be negative" in {
    ResourceProcessor.trySubtractResource(r2, r1) should be(None)
  }

  it should "subtract sets" in {
    ResourceProcessor.trySubtractResource(ry, rz1) match {
      case Some(Resource("y", Type.SET, _, _, Some(mesos.Value.Set(es)), _, _, _, _)) => es.sorted should be(Seq("aaa", "ccc"))
      case x => fail(x.toString)
    }

    ResourceProcessor.trySubtractResource(ry, rz2) should be(None)
  }

  "trySubtractResource Seq" should "only modify the first acceptable item" in {
    ResourceProcessor.trySubtractResource(Vector(r1, r1), r2) should be(
      Some(Seq(Resource("a", Type.SCALAR, Some(Scalar(2))), r1))
    )
  }

  it should "return None if there is no acceptable item" in {
    ResourceProcessor.trySubtractResource(Vector(r2, rx, r2), r1) should be(None)
  }

  it should "handle an empty Seq" in {
    ResourceProcessor.trySubtractResource(Vector(), r1) should be(None)
  }

  it should "subtract ranges" in {
    ResourceProcessor.trySubtractResource(Vector(range1, rx, range2), rangeReq) should be(
      Some(Seq(range1rem, rx, range2)) //rem))
    )
  }

  it should "return None if a subset of the ranges are missing" in {
    ResourceProcessor.trySubtractResource(Vector(range1, rx, range2), rangeX) should be(None)
  }

  "acceptResources" should "return None, if not all resources can be satisfied" in {
    val required = Seq(r1, rx)
    ResourceProcessor.remainderOf(Seq(ry, rx2, r2, r1), required) should be(None)
  }

  it should "subtract only once, if there are more possibilities" in {
    val required = Seq(r1, rx2)
    ResourceProcessor.remainderOf(Seq(ry, rx, r2, r1), required) should be(
      Some(Seq(ry, Resource("x", Type.SCALAR, Some(Scalar(1))), r2, Resource("a", Type.SCALAR, Some(Scalar(0)))))
    )
  }

  it should "handle empty Seqs" in {
    ResourceProcessor.remainderOf(Seq(), Seq(r1, rx2)) should be(None)
    ResourceProcessor.remainderOf(Seq(r1, rx2), Seq()) should be(Some(Seq(r1, rx2)))
  }

  def ranges = for {
    a <- Gen.chooseNum(-1000L, 1000L)
    b <- Gen.chooseNum(a, a + 2000L)
  } yield Range(a, b)

  "intersectRange" should "work" in {
    forAll(ranges, ranges) { (r1, r2) =>
      val expectedRange = {
        val s = (r1.begin to r1.end).toSet intersect (r2.begin to r2.end).toSet
        if (s.isEmpty) None else Some(Range(s.min, s.max))
      }
      ResourceProcessor.intersectRange(r1, r2) should be(expectedRange)
    }
  }

  "subtractRange" should "work" in {
    forAll(ranges, ranges) { (a, b) =>
      for ((r1, r2) <- Seq((a, b), (b, a))) {
        val expSet = (r1.begin to r1.end).toSet -- (r2.begin to r2.end).toSet
        val res = ResourceProcessor.subtractRange(r1, r2)
        val actSet = res._1.map(r => (r.begin to r.end).toSet).fold(Set.empty)(_ ++ _)
        assert(
          expSet == actSet,
          s"in first (not in second): ${expSet -- actSet}\nin second (not in first): ${actSet -- expSet} (${r1.begin} -> ${r1.end} -- ${r2.begin} -> ${r2.end})"
        )
      }
    }
  }

  "trySubtractRanges" should "work" in {
    forAll(Gen.listOf(ranges), Gen.listOf(ranges)) { (a: List[Range], b: List[Range]) =>
      val exp: Option[Map[Long, Int]] = {
        val aSet = multiSetFromRanges(a)
        val bSet = multiSetFromRanges(b)
        val rem = subtractMultiSet(aSet, bSet)
        val unfulfilled = subtractMultiSet(bSet, aSet)
        if (unfulfilled.isEmpty) {
          Some(rem)
        } else {
          None
        }
      }

      val res = ResourceProcessor.trySubtractRanges(a, b)
      val act: Option[Map[Long, Int]] = res.map(multiSetFromRanges(_))
      assert(act == exp, s"${a.map(reprRange)} -- ${b.map(reprRange)} != ${res.map(_.map(reprRange))}")
    }
  }

  "addResource" should "add matching resources" in {
    ResourceProcessor.addResource(r1, r2) should be(
      Resource("a", Type.SCALAR, Some(Scalar(5 + 3))) :: Nil
    )
    ResourceProcessor.addResource(ry, rz2) should be(
      Resource("y", Type.SET, set = Some(mesos.Value.Set(Seq("aaa", "bbb", "ccc", "xxx")))) :: Nil
    )
    ResourceProcessor.addResource(range1, range2) should be(
      Resource("range", Type.RANGES, ranges = Some(Ranges(Seq(Range(1, 3), Range(11, 13), Range(4, 6), Range(14, 16))))) :: Nil
    )
  }

  it should "not add different resources" in {
    ResourceProcessor.addResource(r1, ry) should be(List(r1, ry))
    ResourceProcessor.addResource(range1, rz1) should be(List(range1, rz1))
  }

  "sumResources" should "collapse only matching resources" in {
    ResourceProcessor.sumResources(Nil) should be(Nil)
    ResourceProcessor.sumResources(List(range1)) should be(List(range1))
    ResourceProcessor.sumResources(List(range1, r1)).to[Set] should be(Set(range1, r1))
    ResourceProcessor.sumResources(List(range1, r1, rz1)).to[Set] should be(Set(range1, r1, rz1))
    ResourceProcessor.sumResources(List(range1, r1, rz1, r2)).to[Set] should be(Set(range1, Resource("a", Type.SCALAR, Some(Scalar(5 + 3))), rz1))
  }

  def multiSetFromRanges(rs: Seq[Range]): Map[Long, Int] = {
    val nums = rs.flatMap(r => (r.begin to r.end).toSeq)
    nums.groupBy(identity).mapValues(_.length)
  }

  def subtractMultiSet(a: Map[Long, Int], b: Map[Long, Int]): Map[Long, Int] = {
    val res = a.transform((k, av) => b.get(k).map(bv => Math.max(av - bv, 0)).getOrElse(av))
    res.filter(_._2 != 0)
  }

  def reprRange(r: Range) = s"[${r.begin}, ${r.end}]"
}
