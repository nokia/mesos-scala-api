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
package com.example.mesos

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.sys.process.Process

import org.apache.mesos.mesos
import org.apache.mesos.mesos.{Value, Resource, Status}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatest.concurrent.ScalaFutures

import com.nokia.mesos.api.async.TaskLauncher.TaskDescriptor
import com.typesafe.scalalogging.LazyLogging

class FrameworkSpec
    extends FlatSpec
    with TestMesosCluster
    with BeforeAndAfterAll
    with ScalaFutures
    with LazyLogging {

  var procs: Seq[Process] = Seq()

  override def beforeAll = {
    procs = startTestCluster()
  }

  override def afterAll = {
    procs foreach {_.destroy()}
  }

  "runSingleTask" should "successfully execute a shell Task" in {
    val sleepDuration: Int = 10
    val cmd = s"sleep $sleepDuration"

    val fut = Examples.runSingleTask("localhost:5050", Examples.shellTaskDescriptor(cmd))
    Await.ready(fut, 2 * sleepDuration.seconds)
    assert(fut.value.get.isSuccess)
  }

  it should "successfully run a Docker Task" in {
    if (!isDockerInstalled) cancel("Docker is not installed")

    val task = TaskDescriptor(
      "docker test task",
      Seq(Resource("cpus", Value.Type.SCALAR, Some(Value.Scalar(1.0)))),
      Right(mesos.ContainerInfo(
        mesos.ContainerInfo.Type.DOCKER,
        hostname = Some("optional"),
        docker = Some(mesos.ContainerInfo.DockerInfo(
          image = "hello-world",
          forcePullImage = Some(true),
          network = Some(mesos.ContainerInfo.DockerInfo.Network.BRIDGE),
          privileged = Some(false)
        ))
      )),
      Seq()
    )

    val fut = Examples.runSingleTask("localhost:5050", task)
    Await.ready(fut, 30.seconds)
    assert(fut.value.get.isSuccess)
  }

  "abort" should "work correctly" in {
    val st = for {
      fw <- Examples.connect()
      ti <- fw.submitTask(Examples.shellTaskDescriptor("sleep 1")).info
      st <- fw.abort()
    } yield st
    assert(st.futureValue(PatienceConfig(2.seconds)) == Status.DRIVER_ABORTED)
  }

  "reconnect" should "be possible after a disconnect" in {
    val fw = Examples.createFw()
    val fut = for {
      _ <- fw.connect()
      ti <- fw.submitTask(Examples.shellTaskDescriptor("sleep 1")).info
      st <- fw.disconnect()
      _ = assert(st == Status.DRIVER_STOPPED)
      fw = Examples.createFw()
      _ <- fw.connect()
      ti <- fw.submitTask(Examples.shellTaskDescriptor("sleep 1")).info
    } yield ti
    val taskInfo = fut.futureValue(PatienceConfig(5.seconds))
    assert(taskInfo.command.get.value.get == "sleep 1")
  }
}
