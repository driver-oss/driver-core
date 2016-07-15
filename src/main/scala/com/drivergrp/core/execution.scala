package com.drivergrp.core


object execution {

  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors
  import akka.actor.ActorSystem


  trait ExecutionContextModule {

    def executionContext: ExecutionContext
  }

  trait FixedThreadsExecutionContext extends ExecutionContextModule {

    def threadsNumber: Int

    val executionContext: ExecutionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadsNumber))
  }

  trait ActorSystemModule {

    def actorSystem: ActorSystem
  }
}
