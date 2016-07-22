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

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.mesos.mesos.FrameworkInfo
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.nokia.mesos.DriverFactory
import com.nokia.mesos.Scheduler
import com.nokia.mesos.SchedulerDriver
import com.nokia.mesos.api.async.MesosDriver
import com.nokia.mesos.api.stream.MesosEvents

class DriverFactorySpec extends FlatSpec with Matchers {

  "DriverFactory" should "create the default driver successfully" in {
    DriverFactory.createDriver(
      FrameworkInfo(name = "myFramework", user = ""),
      "localhost:5050"
    )
  }

  "A custom DriverFactory" should "be implementable with using only the API" in {
    // this won't actually work, we're testing only the API
    val customDriverFactory: DriverFactory = new DriverFactory {

      override def createDriver(
        frameworkInfo: FrameworkInfo,
        url: String,
        scheduler: Scheduler with MesosEvents
      )(implicit ec: ExecutionContext): MesosDriver = new MesosDriver {

        val executor: ExecutionContext =
          ec

        val eventProvider: MesosEvents =
          scheduler

        val schedulerDriver: SchedulerDriver =
          ???
      }

      override def createDriver(
        frameworkInfo: FrameworkInfo,
        url: String
      )(implicit ec: ExecutionContext): MesosDriver = {
        this.createDriver(frameworkInfo, url, ???)
      }
    }

    val f: (FrameworkInfo, String) => MesosDriver =
      customDriverFactory.createDriver _
  }
}
