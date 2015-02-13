package com.neovahealth

import com.tactix4.t4openerp.connector.transport.{OEDictionary, OEString}

/**
 * Created by max on 11/02/15.
 */
package object nhADT {
  implicit def tupToMap(t:(String, String)) : OEDictionary = OEDictionary(t._1 -> OEString(t._2))
  implicit def tup2OETup(t:(String, String)) : (String, OEString) = t._1 -> OEString(t._2)
  implicit def mapToDic(m:Map[String,String]): OEDictionary = OEDictionary(m.mapValues(OEString))
}
