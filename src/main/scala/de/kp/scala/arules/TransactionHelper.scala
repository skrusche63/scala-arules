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

import com.twitter.scalding.{Args, Job, TextLine}
import com.twitter.scalding._

import org.apache.hadoop.conf.Configuration

class TransactionHelper(args:Args) extends Job(args) {

  override implicit val mode = new Hdfs(true, new Configuration()) 

  override def next: Option[Job] = {
    
    val input = args("output") + "/builder"
    val nextArgs = args + ("input", Some(input))
    
    Some(new VerticalBuilder(nextArgs))
    
  }

  val lines = TextLine(args("input")).read
    /**
     * Ignore all empty or comment lines 
     * (identified by the first character)
     */
    .filter('line) {
      line:String =>  
        line != null && line.isEmpty() == false && line.charAt(0) != '#' && line.charAt(0) != '%' && line.charAt(0) != '@'

    }.groupAll {
        /**
         * This mechanism is used to assign a line no to each line;
         * in order to by synchronous with the respective algorithms,
         * we have to start with lno = 0
         */
        val lno = -1          
        _.scanLeft('line -> 'lno)(lno) {
          (lno:Int, line:String) => lno + 1
         }
        
    }.filter('lno) {
      lno:Int => lno >= 0
    
    }.write(Tsv(args("output") + "/builder", ('lno,'line)))

}