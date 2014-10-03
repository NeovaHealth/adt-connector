package com.tactix4.t4ADT

import com.tactix4.t4ADT.utils.ConfigHelper
import org.apache.camel.language.Bean
import org.apache.camel.{RoutingSlip, Exchange, Header, RecipientList}
import com.typesafe.config.Config
import org.apache.camel.component.hl7.Terser
import org.apache.camel.scala.Preamble._

/**
 * Created by max on 06/08/14.
 */
class RoutingSlipBean {

  @RoutingSlip
  def route(@Header("CamelHL7TriggerEvent") msgType:String ): String = {
    ConfigHelper.getRecipientLists.getOrElse(msgType, throw new Exception(s"Config error: $msgType not found in Recipient List Map")).mkString(",")
  }
}
