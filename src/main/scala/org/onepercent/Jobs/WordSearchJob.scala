/**
 * The MIT License (MIT) Copyright (c) 2014 University of Applied Sciences, Berlin, Germany
 * For more detailed information, please read the licence.txt in the root directory.
 **/

package org.onepercent.Jobs

import java.text.SimpleDateFormat
import java.util.Calendar

import org.onepercent.utils.Types.TypeCreator
import org.onepercent.utils._
import org.onepercent.{Env, JobExecutor, JobResult}
import org.apache.spark.sql.hive.HiveContext

import scala.util.{Failure, Success, Try}

/**
 * Job to search for a specific word in the Tweet texts.
 * @author Patrick Mariot, Florian Willich
 */
class WordSearchJob extends JobExecutor with Logging {

  /**
   * This Method analysis the last 24 Hours to find a specific word in the Tweet texts.
   *
   * @param params List element 0: Word to look for
   *
   * @return  The result of the analysis that looks like follow:
   *          WordSearch @see { TweetAnalyser }
   *
   *          Or errors if there has been something going wrong:
   *
   *          If the search word includes not valid characters:
   *          "SearchWord contains not valid characters: WORD, 100)
   *
   *          If the timestamp between start- and enddate is not valid:
   *          ErrorMessage("No Data available between X and Y", 100)
   *
   *          If start- or enddate doesn't match a valid date:
   *          ErrorMessage("Parameter X is not a valid path!", 100)
   *
   *          If there was something going wrong in the analysis:
   *          ErrorMessage("WordSearch analyses failed!", 101)
   *
   * @author  Patrick Mariot, Florian Willich
   */
  override def executeJob(params: List[String]): JobResult = {

    val timeFormatter: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val currentCalendar: Calendar = Calendar.getInstance()
    // make sure that a minimal amount of data is already written
    currentCalendar.set(Calendar.MINUTE, currentCalendar.get(Calendar.MINUTE) - 10)
    val startCalendar: Calendar = Calendar.getInstance()
    startCalendar.setTime(currentCalendar.getTime)
    startCalendar.set(Calendar.HOUR_OF_DAY, startCalendar.get(Calendar.HOUR_OF_DAY) - 23)
    val endTime: String = timeFormatter.format(currentCalendar.getTime())
    val startTime: String = timeFormatter.format(startCalendar.getTime())

    Try(TypeCreator.gregorianCalendar(startTime, timeFormatter)) match {
      case Success(startGregCalendar) =>

        Try(TypeCreator.gregorianCalendar(endTime, timeFormatter)) match {
          case Success(endGregCalendar) =>

            Try(TypeCreator.multipleClusterPath(Config.get.tweetsPrefixPath, startGregCalendar, endGregCalendar, "*.data")) match {
              case Success(path) =>

                checkSearchWord(params(0)) match {
                  case true =>
                    val hc = new HiveContext(Env.sc)
                    val ta = new TweetAnalyser(Env.sc, hc)
                    log("executeJob", "Starting Anaylsis with keyword: " + params(0))

                    Try(ta.wordSearch(new TweetJSONFileReader(Env.sc, hc).readFile(path), params(0))) match {
                      case Success(result) =>
                        //stop the spark context, otherwise its stuck in this context...
                        log("executeJob", "End Anaylsis with word: " + params(0))
                        result
                      case Failure(_) =>
                        //stop the spark context, otherwise its stuck in this context...
                        log("executeJob", "WordSearch analyses failed! word[" + params(0) + "]")
                        ErrorMessage("WordSearch analyses failed!", 101);
                    }

                  case false =>
                    log("executeJob", "SearchWord contains not valid characters: " + params(0))
                    ErrorMessage("SearchWord contains not valid characters: " + params(0), 100)

                }

              case Failure(wrongPath) =>
                ErrorMessage("No Data available between " + startTime + " and " + endTime, 100)
            }
          case Failure(wrongEndTime) =>
            ErrorMessage("Parameter [" + wrongEndTime + "] is not a valid path!", 100)
        }
      case Failure(wrongStartTime) =>
        ErrorMessage("Parameter [" + wrongStartTime + "] is not a valid path!", 100)

    }

  }

  /**
   * This method checks the transfered word if it includes only valid chars.
   * The regex pattern is build as follows:
   * - # is allowed once or not at all
   * - Every word character is allowed (A word character: [a-zA-Z_0-9]) from Javadoc Regex Pattern
   * - Special Chars are allowed: äöü
   * - The word length has to be a minimum of 2 characters and a maximum of 150
   *
   * @param     word    The word on which the regex check is processed on.
   *
   * @return    true if the validation succeeded, else false.
   */
  def checkSearchWord(word: String): Boolean = {
    val regex: String = "^[#]?+[\\wäöü]{2,150}$"
    word.matches(regex)
  }

}
