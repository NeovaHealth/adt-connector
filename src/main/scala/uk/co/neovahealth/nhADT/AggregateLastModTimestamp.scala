package uk.co.neovahealth.nhADT

import org.apache.camel.Exchange
import org.apache.camel.processor.aggregate.AggregationStrategy
import org.apache.camel.scala.Preamble._

import scala.util.control.Exception._

/**
 * Created by max on 11/08/14.
 */
class AggregateLastModTimestamp extends AggregationStrategy{
  override def aggregate(original: Exchange, resource: Exchange): Exchange = {
    val r = allCatch opt resource.in[String] flatMap {
      case null => None
      case x => Some(x)
    }
    original.getIn.setHeader("lastModTimestamp",r)
    original
  }

}
