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

import com.twitter.scalding.Args

object TopKApp {

  def main(args:Array[String]) {

    val input  = "/Work/tmp/arules/input/ContextIGB.txt"
    val output = "/Work/tmp/arules/output"

    val params = List("--input",input, "--output",output, "--k","10", "--min_conf","0.8", "--algo", "TopK")

    val args = Args(params)
    
    val start = System.currentTimeMillis()

    /**
     * Assign line numbers to textual description of 
     * e-commerce transactions
     */
    val helper = new TransactionHelper(args)    
    helper.run
    
    println("Vertical Builder started...")
    
    helper.next match {
      
      case None => {}     
      case Some(builder) => {
        /**
         * Create vertical database from e-commerce
         * transactions
         */
        builder.run
        
        builder.next match {
          
          case None => {}
          case Some(engine) => {
    
            println("Engine started...")
            engine.run  
            
            val end = System.currentTimeMillis()           
            println("Total time: " + (end-start) + " ms")
            
          }
          
        }
        
      }
	
    }

  }
  
}