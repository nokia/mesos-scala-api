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

import scala.concurrent.ExecutionContext

import org.apache.mesos.mesos.FrameworkInfo

import com.nokia.mesos.api.async.MesosDriver
import com.nokia.mesos.api.stream.MesosEvents
import com.nokia.mesos.impl.scheduler.EventProvidingScheduler
import com.nokia.mesos.proxy.SchedulerDriverProxy
import com.nokia.mesos.proxy.SchedulerProxy

/**
 * Factory of `MesosDriver` instances
 */
trait DriverFactory {

  /** Creates driver with the specified `MesosEvents` */
  def createDriver(
    frameworkInfo: FrameworkInfo,
    url: String,
    scheduler: Scheduler with MesosEvents
  )(implicit ec: ExecutionContext): () => MesosDriver

  /** Creates driver with default `MesosEvents` */
  def createDriver(
    frameworkInfo: FrameworkInfo,
    url: String
  )(implicit ec: ExecutionContext): () => MesosDriver
}

/**
 * Factory for the default implementation of `MesosDriver`
 * (based on `mesos-scala-java-bridge`).
 */
object DriverFactory extends DriverFactory {

  override def createDriver(
    frameworkInfo: FrameworkInfo,
    url: String,
    scheduler: Scheduler with MesosEvents
  )(implicit ec: ExecutionContext): () => MesosDriver = () => {
    val driver = new org.apache.mesos.MesosSchedulerDriver(
      new SchedulerProxy(scheduler),
      FrameworkInfo.toJavaProto(frameworkInfo),
      url
    )
    new MesosDriver {
      val executor: ExecutionContext =
        ec
      val eventProvider: MesosEvents =
        scheduler
      val schedulerDriver: SchedulerDriver =
        new SchedulerDriverProxy(driver)
    }
  }

  override def createDriver(
    frameworkInfo: FrameworkInfo,
    url: String
  )(implicit ec: ExecutionContext): () => MesosDriver = () => {
    val scheduler = new EventProvidingScheduler
    createDriver(frameworkInfo, url, scheduler)(ec)()
  }
}
