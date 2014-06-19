package com.tactix4.t4ADT.utils
/**
 * Created with IntelliJ IDEA.
 * User: max
 * Date: 24/09/13
 * Time: 15:12
 * To change this template use File | Settings | File Templates.
 */
import scala.collection.JavaConversions._
import scala.util.matching.Regex

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
  def propertiesToListRegex(p:java.util.Properties): List[Regex] = {
    val l = scala.collection.mutable.MutableList[Regex]()
    for( n : String <- p.stringPropertyNames){
      l += p.getProperty(n).r
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
