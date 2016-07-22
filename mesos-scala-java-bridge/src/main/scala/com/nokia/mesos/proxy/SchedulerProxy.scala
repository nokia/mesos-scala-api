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

import java.util

import scala.collection.JavaConverters._

import org.apache.mesos.{ Scheduler => JavaScheduler, SchedulerDriver => JavaSchedulerDriver }
import org.apache.mesos.Protos._

import com.nokia.mesos.{ Scheduler => ScalaScheduler }
import com.typesafe.scalalogging.LazyLogging

/**
 * Implementation of the java Scheduler interface, therefore instances of this class
 * can be passed to mesos as a scheduler. All callbacks are delegated to the given
 * scala based scheduler implementation.
 *
 * @param target The scala scheduler to which callbacks are delegated
 */
class SchedulerProxy(target: ScalaScheduler) extends JavaScheduler with LazyLogging {

  // TODO: driver proxy instance may be cached, although SchedulerDriverProxy instance creation is lightweight

  override def resourceOffers(driver: JavaSchedulerDriver, offers: util.List[Offer]): Unit = {
    logger.debug(s"Offers received $offers")
    target.resourceOffers(
      new SchedulerDriverProxy(driver),
      offers.asScala.map(org.apache.mesos.mesos.Offer.fromJavaProto(_))
    )
  }

  override def offerRescinded(driver: JavaSchedulerDriver, offerID: OfferID): Unit = {
    logger.info(s"Offer rescinded $offerID")
    target.offerRescinded(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.OfferID.fromJavaProto(offerID)
    )
  }

  override def statusUpdate(driver: JavaSchedulerDriver, jTaskStatus: TaskStatus): Unit = {
    logger.info(s"Task status update: $jTaskStatus")
    target.statusUpdate(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.TaskStatus.fromJavaProto(jTaskStatus)
    )
  }

  override def registered(driver: JavaSchedulerDriver, frameworkID: FrameworkID, masterInfo: MasterInfo): Unit = {
    logger.info(s"Registered to master $masterInfo, frameworkID: $frameworkID")
    target.registered(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.FrameworkID.fromJavaProto(frameworkID),
      org.apache.mesos.mesos.MasterInfo.fromJavaProto(masterInfo)
    )
  }

  override def reregistered(driver: JavaSchedulerDriver, masterInfo: MasterInfo): Unit = {
    logger.info(s"Scheduler re-registered $masterInfo")
    target.reregistered(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.MasterInfo.fromJavaProto(masterInfo)
    )
  }

  override def disconnected(driver: JavaSchedulerDriver): Unit = {
    logger.info("Scheduler disconnected ")
    target.disconnected(new SchedulerDriverProxy(driver))
  }

  override def slaveLost(driver: JavaSchedulerDriver, slaveID: SlaveID): Unit = {
    logger.info(s"Slave is lost ${slaveID}")
    target.slaveLost(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.SlaveID.fromJavaProto(slaveID)
    )
  }

  override def executorLost(driver: JavaSchedulerDriver, executorID: ExecutorID, slaveID: SlaveID, status: Int): Unit = {
    logger.info(s"Executor $executorID is lost on slave $slaveID, status: $status")
    target.executorLost(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.ExecutorID.fromJavaProto(executorID),
      org.apache.mesos.mesos.SlaveID.fromJavaProto(slaveID),
      status
    )
  }

  override def frameworkMessage(driver: JavaSchedulerDriver, executorID: ExecutorID, slaveID: SlaveID, data: Array[Byte]): Unit = {
    logger.info(s"Framework message from exeutor $executorID from slave $slaveID, data: $data")
    target.frameworkMessage(
      new SchedulerDriverProxy(driver),
      org.apache.mesos.mesos.ExecutorID.fromJavaProto(executorID),
      org.apache.mesos.mesos.SlaveID.fromJavaProto(slaveID),
      data
    )
  }

  override def error(driver: JavaSchedulerDriver, message: String): Unit = {
    logger.info(s"Mesos error: $message")
    target.error(new SchedulerDriverProxy(driver), message)
  }
}
