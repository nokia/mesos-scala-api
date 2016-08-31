# Scala API for Mesos

This library provides a [Scala](http://www.scala-lang.org) API for [Mesos](https://mesos.apache.org).

## Getting Started

The library is available for Scala 2.11. To use it, it's enough to depend on the `mesos-scala-api` artifact. To achieve this, add the following to your `build.sbt` file:

```scala
libraryDependencies += "com.nokia" %% "mesos-scala-api" % "0.4.0"
```

## <a name="deps"></a>Dependencies

All dependencies not listed here are handled automatically (since they are simple Maven dependencies).

### Runtime

* `libmesos.so`, the native Mesos library: <https://mesos.apache.org> (the currently supported Mesos version is 0.28.2)
* `MESOS_NATIVE_JAVA_LIBRARY` environment variable (should point to the native Mesos library)
* a backend for [SLF4J](http://www.slf4j.org/) (e.g., [Logback](http://logback.qos.ch/))
* if you want to run tasks in docker containers, a working [Docker](https://www.docker.com/) installation (the `docker` tool itself, a running Docker daemon, and authorization for the current user to run Docker commands)

### Build

* `protoc`, the protocol buffer compiler: <https://developers.google.com/protocol-buffers>

### Integration tests

* `MESOS_HOME` environment variable (should point to a Mesos source tree with a `build` directory
   which contains the compiled artifacts)
* a working Docker installation (see above)

## Tutorial

This is a comprehensive guide to help you get started with Mesos using our library. Following these steps will enable you to build our library, run a local Mesos cluster on your machine and play around with it using Scala. If you just want to develop software using our framework and connect to a remote Mesos master, fulfilling the build and runtime dependencies in the [Dependencies](#deps) section is sufficient.

You need `protoc` and `sbt` to build our library. Navigate to the directory containing `build.sbt`, start `sbt` and issue

```
compile
test
```

The `compile` and `test` tasks should finish without errors.

In order to run our examples and integration tests, you need Mesos installed. Download the appropriate version from https://mesos.apache.org/downloads/ and build it according to the official instructions at https://mesos.apache.org/gettingstarted/.

You will find a [`cluster.sh`](scripts/cluster.sh) script in our project in the [`scripts`](scripts) directory. In order for this to work, you need to set your `MESOS_HOME` to your Mesos directory containing the `build` directory. You also need to set `MESOS_NATIVE_JAVA_LIBRARY` to point to `libmesos.so`, which should be at `$MESOS_HOME/build/src/.libs/libmesos.so`.

If you execute `cluster.sh` you should see the Mesos dashboard at <http://localhost:5050> with six slaves and a lot of fake CPUs and memory.

Now you can run our example in the `mesos-scala-example` project. In `sbt` give the following commands.

```
project mesos-scala-example
run
```

This will run the [`com.example.mesos.Examples.main`](mesos-scala-example/src/main/scala/com/example/mesos/Examples.scala) method, which calls the `runSingleTask` method. This method creates a framework, registers it in Mesos, launches a task containing a `sleep 10` shell command, and terminates the framework once the task successfully finishes. You can observe this process on the Mesos dashboard once the program is run.

You can also run the integration test, which does all this automatically. For this you need to issue the `it:test` command in `sbt`. **You MUST NOT start the local cluster manually before running the integration test.** The integration test will launch a Mesos cluster automatically, and shut it down as soon as the test finishes, so it isn't suitable for exploration.

The integration test contains a test which launches a `hello-world` Docker image. You can inspect its code in [`com.example.mesos.FrameworkSpec`](mesos-scala-example/src/it/scala/com/example/mesos/FrameworkSpec.scala). This test will be skipped if no Docker installation is detected, so you should also install [Docker](https://www.docker.com/) if you want this test to run.

## Project structure

The main artifact is defined in [`mesos-scala-api`](mesos-scala-api); it depends on the other modules (except the examples):

* [`mesos-scala-framework`](mesos-scala-framework): high level Mesos API, automatic offer handling and task launching logic
* [`mesos-scala-java-bridge`](mesos-scala-java-bridge): adapter between the Java Mesos API and a Scala equivalent
* [`mesos-scala-interface`](mesos-scala-interface): Scala classes generated from the Mesos .protobuf file
* [`mesos-scala-example`](mesos-scala-example): examples and integration tests

## License

See [LICENSES](LICENSES.txt).
