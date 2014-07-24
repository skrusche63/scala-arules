package de.kp.scala.arules
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Scala-ARULES project
* (https://github.com/skrusche63/scala-arules).
* 
* Scala-ARULES is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Scala-ARULES is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Scala-ARULES. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.{BitSet,Collections,Comparator}
import scala.collection.JavaConversions._

import com.twitter.scalding.{Args, Job, TextLine}
import com.twitter.scalding._

import org.apache.hadoop.conf.Configuration

import de.kp.core.arules.{Transaction,Vertical}
import de.kp.core.arules.hadoop.VerticalWritable

class VerticalBuilder(args:Args) extends Job(args) {

  override implicit val mode = new Hdfs(true, new Configuration()) 

  override def next: Option[Job] = {
    
    val input = args("output") + "/vertical"
    val nextArgs = args + ("input", Some(input))
    
    val algo = args("algo")
    algo match {
      
      case "TopK"   => Some(new TopKEngine(nextArgs))
      case "TopKNR" => Some(new TopKNREngine(nextArgs))
      
      case _ => None
    
    }
    
  }

  val lines = Tsv(args("input"),('lno,'line)).read
  
  /**
   * Determine identifiers from transactions
   */
  val ids = lines.discard('lno).flatMapTo('line -> 'id) {
    line:String => {
      line.split(" ").map(item => {
        
        if (item != "") Integer.parseInt(item)
        
      })
    }
      
  }
  /**
   * Determine id with maximum value
   */
  val mid = ids.groupAll { _.max('id -> 'mid) }
  
  val transactions = lines.mapTo(('lno,'line) -> 'transaction) {
    value:(Int,String) => {
      
      val tid  = value._1
      val line = value._2
      
      val items = value._2.split(" ")
      val trans = new Transaction(items.length)
      
      trans.setId(value._1.toString)
      
      items.foreach(item => {
        
        if (item != "") {
          val id = Integer.parseInt(item)
          trans.addItem(id) 
        
        }
        
      })
		
      /*
       * Sort transactions by descending order of items because
       * TopKRules and TNR assume that items are sorted by lexical 
       * order for optimization
       */ 
	  Collections.sort(trans.getItems(), new Comparator[Integer](){
		def compare(o1:Integer, o2:Integer):Int = {
		  return o2-o1
	    }}
	  )
      
	  trans
	  
    }
  
  }

  val total = transactions.groupAll { _.size }.rename('size -> 'total)
  
  /**
   * Create vertical database; associate transaction identifier
   */
  val verticalDatabase = transactions.crossWithTiny(mid).crossWithTiny(total)
      .groupAll {
      
        val v = new Vertical()
      _.foldLeft(('transaction,'mid,'total) -> 'vertical)(v) {
        (vertical:Vertical, value:(Transaction,Int,Int)) => 
        
        vertical.setSize(value._2)
        
        val trans = value._1
        
        val tid   = trans.getId()
        val items = trans.getItems()
        
        for (item <- items) yield {
          
          val ids = vertical.tableItemTids(item)
          if (ids == null) vertical.tableItemTids(item) = new BitSet(value._3)
        
          /*
           * Associate items with respective transaction
           * using the transaction identifier tid
           */
          vertical.tableItemTids(item).set(tid.toInt)
          /*
           * Update the support of this item
           */
          vertical.tableItemCount(item) = vertical.tableItemCount(item) + 1
          
        }
        
        vertical.setTrans(trans)
        
        vertical
          
      }
    
    }.mapTo('vertical -> 'vertical) {
      vertical:Vertical => new VerticalWritable(vertical)
  
    }.write(SequenceFile(args("output") + "/vertical", 'vertical))
   
  
}