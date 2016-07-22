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

import scala.concurrent.ExecutionContext

import com.nokia.mesos.api.async.MesosDriver
import com.nokia.mesos.api.async.MesosFramework
import com.nokia.mesos.api.async.Scheduling
import com.nokia.mesos.api.stream.MesosEvents
import com.nokia.mesos.impl.async.MesosFrameworkImpl

/**
 * Default implementation of `MesosFramework` and
 * `TaskLauncher` together
 *
 * Subclasses must provide the `Scheduling` to
 * be used for launching tasks.
 *
 * @param driver The driver which actually
 * communicates with Mesos
 */
abstract class AbstractFrameworkImpl(override val driver: MesosDriver)
    extends MesosFrameworkImpl
    with TaskLauncherImpl {

  protected override val executor: ExecutionContext =
    driver.executor

  protected override val fw: MesosFramework =
    this

  override val eventProvider: MesosEvents =
    driver.eventProvider
}

/**
 * A concrete implementation of
 * `AbstractFrameworkImpl`, with
 * the default `SimpleScheduling`
 */
class FrameworkImpl(driver: MesosDriver)
    extends AbstractFrameworkImpl(driver) {

  override val scheduling: Scheduling =
    new SimpleScheduling
}
