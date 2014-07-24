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

import cascading.pipe.joiner._

import com.twitter.scalding.{Args, Job, TextLine}
import com.twitter.scalding._

import org.apache.hadoop.conf.Configuration

import scala.collection.mutable.ArrayBuffer

import de.kp.core.arules.hadoop.IntArrayWritable

/**
 * A helper class to preprocess large transaction
 * databases before using an FPGrowth algorithm;
 * 
 * this class expects transactions databases that
 * have been preprocessed by the TransactionHelper
 * 
 */
class FPGrowthHelper(args:Args) extends Job(args) {

  override implicit val mode = new Hdfs(true, new Configuration()) 
  
  val minsupp = args("minsupp").toDouble
  
  /**
   * The field 'lno is interpreted as the transaction 
   * identifier and used to assign items to a transaction
   */
  val lines = Tsv(args("input"),('lno,'line)).read
  
  /**
   * Determine total number of transactions
   */
  val total = lines.groupAll { _.size }.rename('size -> 'total)
  
  /**
   * Determine item support (appearances in all transactions)
   * and reduce to those items that are a above the threshold
   * 
   * This is the first scan of the transaction database
   */
  val itemSupport = lines.discard('lno).flatMapTo('line -> 'item) { line:String => line.split(" ") }
    .groupBy('item) { _.size }.rename('size -> 'count)
 
  /**
   * Filter all items that are above the threshold = minsupp * total;
   */
  val itemMinSupport = itemSupport.insert('minsupp, minsupp).crossWithTiny(total)
    .filter(('item, 'count, 'minsupp, 'total)) {
      value:(String,Int,Double,Int) => { value._2 >= Math.ceil(value._3 * value._4).toInt }
    
    }
    .mapTo(('item, 'count, 'minsupp, 'total) ->('item,'count)) {
      value:(String,Int,Double,Int) => (value._1, value._2)
      
    }
    .write(Tsv(args("output") + "/item-support", ('item,'count)))
  
  /**
   * This is the second scan of the transaction database to
   * derice transactions built from most frequent items and
   * sorted in descending order of support
   */
  val transactionItem = lines.flatMap('line -> 'item) { line:String => line.split(" ") }.discard('line)
  
  /**
   * Compute sorted item in the transaction descending order of support  
   */
  val transactions = transactionItem.joinWithSmaller('item -> 'item, itemMinSupport)
    .groupBy('lno) {
      
       val transaction = ArrayBuffer.empty[Int]      
       _.sortBy('count).reverse.foldLeft(('lno,'item,'count) -> 'transaction)(transaction) {
         (trans:ArrayBuffer[Int], value:(Int,String,Int)) => 
          
          trans += value._2.toInt
          trans
       } 
      
     }
    .mapTo(('lno, 'transaction) -> 'transaction) {
      data:(Int,ArrayBuffer[Int]) => new IntArrayWritable(data._2.toArray[Int])
    }
    .write(SequenceFile(args("output") + "/transactions", 'transaction))
    
}


