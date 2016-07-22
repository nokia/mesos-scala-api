#!/bin/bash

# Copyright (c) 2016, Nokia Solutions and Networks Oy
# All rights reserved.

# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#     * Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#     * Redistributions in binary form must reproduce the above copyright
#       notice, this list of conditions and the following disclaimer in the
#       documentation and/or other materials provided with the distribution.
#     * Neither the name of Nokia Solutions and Networks Oy nor the
#       names of its contributors may be used to endorse or promote products
#       derived from this software without specific prior written permission.

# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL NOKIA SOLUTIONS AND NETWORKS OY BE LIABLE FOR ANY
# DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

ID=${1:-1}  # ID is the first parameter of the script, defaults to "1"
PORT=$((5050 + $ID))
MASTER=${2-'127.0.0.1:5050'}

mkdir -p /tmp/mesos/slave-$ID
rm -vf /tmp/mesos/slave-$ID/meta/slaves/latest
$MESOS_HOME/build/bin/mesos-slave.sh \
--containerizers=mesos \
--work_dir=/tmp/mesos/slave-$ID --hostname=slave-$ID --port=$PORT \
--resources='cpus:24;mem:24576;disk:409600;ports:[21000-24000];vlans:[501-600]' \
--attributes='rack:3;u:2' \
--systemd_enable_support=false \
--master=$MASTER
