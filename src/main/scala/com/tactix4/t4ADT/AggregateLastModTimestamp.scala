package com.tactix4.t4ADT

import org.apache.camel.Exchange
import org.apache.camel.processor.aggregate.AggregationStrategy

/**
 * Created by max on 11/08/14.
 */
class AggregateLastModTimestamp extends AggregationStrategy{
  override def aggregate(original: Exchange, resource: Exchange): Exchange = {
    original.getIn.setHeader("lastModTimestamp",nullCheck(resource.getIn.getBody(classOf[String])))
    original
  }

  def nullCheck(s:String):String = if (s == null) "" else s
}
