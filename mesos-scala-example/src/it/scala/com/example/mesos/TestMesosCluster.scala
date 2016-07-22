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

import java.nio.file.{Files, Paths}

import scala.sys.process._
import scala.util.Try

trait TestMesosCluster {

  /** Launches a locally hosted Mesos cluster of a master and 6 slaves
    *
    * @return The processes
    */
  def startTestCluster(): Seq[Process] = {
    val mesosHome = assertEnvVariables()
    val mesosMasterBin = s"$mesosHome/build/src/mesos-master"
    val mesosSlaveBin = s"$mesosHome/build/src/mesos-slave"
    assert(new java.io.File(mesosMasterBin).exists, s"Couldn't find Mesos executable $mesosMasterBin")
    assert(new java.io.File(mesosSlaveBin).exists, s"Couldn't find Mesos executable $mesosSlaveBin")

    val masterTmp = Files.createTempDirectory(Paths.get("/tmp"), "mesos-master-").toAbsolutePath().toString()
    val masterIp = "127.0.0.1"
    val master = Seq(mesosMasterBin,
      "--hostname=localhost",
      s"--work_dir=$masterTmp",
      s"--ip=$masterIp",
      "--quorum=1"
    ).run()

    val slaves = 1 to 6 map { id =>
      val slaveTmp = Files.createTempDirectory(Paths.get("/tmp"), "mesos-slave-").toAbsolutePath().toString()

      s"rm -vf $slaveTmp/meta/slaves/latest".!

      val containerizerArg = if (isDockerInstalled) "--containerizers=mesos,docker" else "--containerizers=mesos"

      val args = Seq(
        s"--work_dir=$slaveTmp",
        s"--hostname=slave-$id",
        s"--port=${5050+id}",
        "--resources=cpus:24;mem:24576;disk:409600;ports:[21000-24000];vlans:[501-600]",
        "--attributes='rack:3;u:2'",
        "--systemd_enable_support=false",
        s"--master=$masterIp:5050"
      ) :+ containerizerArg

      Process(mesosSlaveBin +: args, None, ("MESOS_LAUNCHER_DIR", s"$mesosHome/build/src")).run()
    }

    slaves :+ master
  }

  /** Checks if environment variables required for the it test are set
    *
    * @return MESOS_HOME environment variable
    */
  def assertEnvVariables(): String = {
    // required by the Mesos Java library
    assert(sys.env.get("MESOS_NATIVE_JAVA_LIBRARY").isDefined, "MESOS_NATIVE_JAVA_LIBRARY is not set")
    // required only by the integration test
    assert(sys.env.get("MESOS_HOME").isDefined, "MESOS_HOME required by the it test is not set")
    sys.env.get("MESOS_HOME").get
  }

  def isDockerInstalled(): Boolean = {
    Try {
      "docker --version".! == 0
    }.getOrElse(false)
  }
}
