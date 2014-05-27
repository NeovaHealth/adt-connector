package com.tactix4.t4ADT.utils
/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 24/09/13
 * Time: 15:12
 * To change this template use File | Settings | File Templates.
 */
import scala.collection.JavaConversions._
object SpringHelper {

  def propertiesToMap(p: java.util.Properties) : Map[String,String] = {
    val m = scala.collection.mutable.Map[String,String]()
    for( n : String <- p.stringPropertyNames){
      m.put(n, p.getProperty(n))
    }
    m.toMap
  }
  def propertiesToList(p:java.util.Properties): List[String] = {
    val l = scala.collection.mutable.MutableList[String]()
    for( n : String <- p.stringPropertyNames){
      l += p.getProperty(n)
    }
    l.toList
  }
  def propertiesToSet(p:java.util.Properties): Set[String] = {
    val l = scala.collection.mutable.MutableList[String]()
    for( n : String <- p.stringPropertyNames){
      l += p.getProperty(n)
    }
    l.toSet
  }


}
