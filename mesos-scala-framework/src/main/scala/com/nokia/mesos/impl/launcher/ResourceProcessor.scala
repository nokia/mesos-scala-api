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
import org.apache.mesos.mesos.Resource
import org.apache.mesos.mesos.Value.{ Range, Ranges, Scalar }

/**
 * Utilities for counting Mesos resources
 */
object ResourceProcessor {

  /**
   * Tries to subtract the `required` resources
   * from the `available` ones.
   *
   * @return The remaining resources, or None, if
   * there are not enough available resources
   */
  def remainderOf(available: Seq[Resource], required: Seq[Resource]): Option[Vector[Resource]] =
    remainderOf(available.toVector, required.toList)

  @tailrec
  private[mesos] def remainderOf(available: Vector[Resource], required: List[Resource]): Option[Vector[Resource]] = required match {
    case Nil => Some(available)
    case resource :: rest => trySubtractResource(available, resource) match {
      case Some(remainder) => remainderOf(remainder, rest)
      case None => None
    }
  }

  private[mesos] def trySubtractResource(rs: Vector[Resource], req: Resource): Option[Vector[Resource]] = {
    val (found, remainder) = rs.foldLeft((false, Vector.empty[Resource])) { (st, r) =>
      val (f, rem) = st
      if (f) {
        (f, rem :+ r)
      } else {
        // FIXME: can we satisfy req from more than one resources?
        trySubtractResource(r, req) match {
          case Some(res) => (true, rem :+ res)
          case None => (f, rem :+ r)
        }
      }
    }

    if (found) Some(remainder) else None
  }

  private[mesos] def sumResources(rs: Seq[Resource]): Vector[Resource] = rs.to[List] match {
    case Nil => Vector.empty
    case h :: Nil => Vector(h)
    case h :: t => {
      val s = sumResources(t)
      val (succ, coll) = s.foldLeft((false, Vector.empty[Resource])) {
        case ((done, collected), rs) =>
          if (done) {
            (done, collected :+ rs)
          } else {
            addResource(h, rs) match {
              case res :: Nil => (true, collected :+ res)
              case _ => (false, collected :+ rs)
            }
          }
      }

      if (succ) {
        coll
      } else {
        coll :+ h
      }
    }
  }

  /**
   * Adds two resources, unifying them (if possible)
   */
  def addResource(a: Resource, b: Resource): List[Resource] = {
    if (a.name == b.name) {
      (a, b) match {
        case (Resource(_, _, Some(Scalar(s1)), _, _, _, _, _, _), Resource(_, _, Some(Scalar(s2)), _, _, _, _, _, _)) => {
          a.copy(scalar = Some(Scalar(s1 + s2))) :: Nil
        }
        case (Resource(_, _, _, Some(Ranges(rs1)), _, _, _, _, _), Resource(_, _, _, Some(Ranges(rs2)), _, _, _, _, _)) => {
          val sum = (rs1 ++ rs2).to[Set].to[Seq]
          a.copy(ranges = Some(Ranges(sum))) :: Nil
        }
        case (Resource(_, _, _, _, Some(mesos.Value.Set(es1)), _, _, _, _), Resource(_, _, _, _, Some(mesos.Value.Set(es2)), _, _, _, _)) => {
          val sum = (es1 ++ es2).to[Set].to[Seq]
          a.copy(set = Some(mesos.Value.Set(sum))) :: Nil
        }
        // TODO: Text resource type
        case _ => List(a, b)
      }
    } else {
      List(a, b)
    }
  }

  private[mesos] def trySubtractResource(a: Resource, b: Resource): Option[Resource] = {
    // FIXME: what to do when the result is zero?
    if (a.name == b.name) {
      (a, b) match {
        case (Resource(_, _, Some(Scalar(s1)), _, _, _, _, _, _), Resource(_, _, Some(Scalar(s2)), _, _, _, _, _, _)) => {
          val rem = s1 - s2
          if (rem >= 0) Some(a.copy(scalar = Some(Scalar(rem)))) else None
        }
        case (Resource(_, _, _, Some(Ranges(rs1)), _, _, _, _, _), Resource(_, _, _, Some(Ranges(rs2)), _, _, _, _, _)) => {
          trySubtractRanges(rs1, rs2).map(remainder => a.copy(ranges = Some(Ranges(remainder))))
        }
        case (Resource(_, _, _, _, Some(mesos.Value.Set(es1)), _, _, _, _), Resource(_, _, _, _, Some(mesos.Value.Set(es2)), _, _, _, _)) => {
          val offer = es1.toSet
          val requirement = es2.toSet
          val (found, remainder) = offer.partition(requirement)
          if (found == requirement) {
            Some(a.copy(set = Some(mesos.Value.Set(remainder.toSeq))))
          } else {
            None
          }
        }
        // TODO: Text resource type
        case _ => None
      }
    } else {
      None
    }
  }

  private[mesos] def trySubtractRanges(offers: Seq[Range], requirements: Seq[Range]): Option[Seq[Range]] = {
    val (remainder, unfulfilled) = subtractRangesFromOffers(offers.toVector, requirements.toVector)
    if (unfulfilled.isEmpty) {
      Some(remainder)
    } else {
      None
    }
  }

  private[mesos] def subtractRangesFromOffers(offers: Vector[Range], requirements: Vector[Range]): (Vector[Range], Vector[Range]) = offers match {
    case Vector() => (Vector.empty, requirements)
    case off +: tail => {
      val (rem, un) = subtractRangesFromSingleOffer(off, requirements)
      val (nRem, nUn) = subtractRangesFromOffers(tail, un)
      (rem ++ nRem, nUn)
    }
  }

  private[mesos] def subtractRangesFromSingleOffer(offer: Range, requirements: Vector[Range]): (Vector[Range], Vector[Range]) = requirements match {
    case Vector() => (Vector(offer), Vector.empty)
    case req +: tail => {
      val (rem, un) = subtractRange(offer, req)
      val (nRem, nUn) = subtractRangesFromOffers(rem, tail)
      (nRem, un ++ nUn)
    }
  }

  private[mesos] def subtractRange(a: Range, b: Range): (Vector[Range], Vector[Range]) = {
    intersectRange(a, b) match {
      case Some(i) => (subtractContainedRange(a, i), subtractContainedRange(b, i))
      case None => (Vector(a), Vector(b))
    }
  }

  private[mesos] def intersectRange(r1: Range, r2: Range): Option[Range] = {
    if ((r1.begin <= r1.end) && (r2.begin <= r2.end)) {
      val (a, b) = if (r1.begin <= r2.begin) {
        (r1, r2)
      } else {
        (r2, r1)
      }

      if (b.begin <= a.end) {
        Some(Range(b.begin, Math.min(a.end, b.end)))
      } else {
        None
      }
    } else {
      None
    }
  }

  private def subtractContainedRange(a: Range, b: Range): Vector[Range] = {
    require(a.begin <= b.begin)
    require(a.end >= b.end)

    if (a == b) {
      Vector.empty
    } else if (a.begin == b.begin) {
      Vector(Range(b.end + 1, a.end))
    } else if (a.end == b.end) {
      Vector(Range(a.begin, b.begin - 1))
    } else {
      Vector(Range(a.begin, b.begin - 1), Range(b.end + 1, a.end))
    }
  }
}
