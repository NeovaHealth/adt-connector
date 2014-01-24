package com.tactix4.t4ADT.utils

import scala.collection.JavaConversions._
import com.codahale.metrics._
import nl.grons.metrics.scala

/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 24/01/14
 * Time: 18:12
 * To change this template use File | Settings | File Templates.
 */
object Metrics {

  val metricRegistry = new com.codahale.metrics.MetricRegistry()


  final val reporter = JmxReporter.forRegistry(metricRegistry).build
  reporter.start()
}

trait Instrumented extends nl.grons.metrics.scala.InstrumentedBuilder {
  val metricRegistry = Metrics.metricRegistry

}
