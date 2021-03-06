/**
 * The MIT License (MIT) Copyright (c) 2014 University of Applied Sciences, Berlin, Germany
 * For more detailed information, please read the licence.txt in the root directory.
 **/

package org.onepercent

import akka.actor.Actor


/**
 *
 * @param jobID the jobID given by the WebServer to uniquely identify a Job.
 * @param jobResult the result structure of the Job.
 * @author pFriesch
 */
case class Result(jobID: String, jobResult: JobResult)

/**
 *
 * @param jobID the jobID given by the WebServer to uniquely identify a Job.
 * @param name unique name of the Job, needs to be exactly the same as the class name.
 * @param params parameters for the job execute method.
 * @param time the time the job got issued.
 */
case class JobSignature(jobID: String, name: String, params: List[String], time: String)

/**
 *
 * @param jobID the jobID given by the WebServer to uniquely identify a Job.
 * @param params parameters for the job execute method.
 */
case class ExecuteJob(jobID: String, params: List[String])

/**
 * A marking trait to mark results of jobs.
 */
trait JobResult

/**
 * Each Job needs to implement this trait and the executeJob method.
 * When a ExecuteJob Message is received it the Job will be executed and the Result is Returned to the JobHandler.
 *
 * @author Pius Friesch, Florian Willich
 */
trait JobExecutor extends Actor {

  /**
   * On receive of an ExecuteJob the Job will be executed and the result is send to the sender of the job request.
   */
  def receive = {

    case ExecuteJob(jobID, params) =>
      val result: Result = Result(jobID, executeJob(params))
      sender ! result
  }


  /**
   * Runs a spark Job and returns the result in a case class structure.
   * @param params the params of the specified job.
   * @return the result as a case class.
   */
  protected def executeJob(params: List[String]): JobResult

}
