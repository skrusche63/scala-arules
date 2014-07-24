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

import com.twitter.scalding.{Job,Args}
import com.twitter.scalding._

import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.conf.Configuration

import de.kp.core.arules.TopKNRAlgorithm
import de.kp.core.arules.hadoop.io.{RuleWriter,VerticalReader}

class TopKNREngine(args: Args) extends Job(args) {

  override implicit val mode = new Hdfs(true, new Configuration())
  
  override def run: Boolean = {

    val job = new JobConf()

    /**
     * Extract parameters from args
     */
    val k:Integer = args("k").toInt
	val minConf:Double = args("min_conf").toDouble
	
	val delta:Integer = args("delta").toInt

    /**
     * Load vertical database
     */
    val reader = new VerticalReader()    
    val vertical = reader.read(args("input"),job)
	
    /**
     * Run algorithm and create Top K association rules
     */
	val algo = new TopKNRAlgorithm()
	val rules = algo.runAlgorithm(k, minConf, vertical, delta)
	/**
	 * Show statistics
	 */
	algo.printStats();

	/**
	 * Write Top K association rules to HDFS
	 */
	val writer = new RuleWriter()
	writer.write(rules,args("output") + "/rules", job);

    true

  }

}